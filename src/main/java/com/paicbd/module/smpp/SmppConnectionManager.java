package com.paicbd.module.smpp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.SubmitSmResponseEvent;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.SmppEncoding;
import com.paicbd.smsc.utils.SmppUtils;
import com.paicbd.smsc.utils.UtilsEnum;
import com.paicbd.smsc.utils.Watcher;
import com.paicbd.smsc.ws.SocketSession;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.bean.DataCoding;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.DeliveryReceiptState;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.paicbd.module.utils.Constants.PARAM_UPDATE_SESSIONS;
import static com.paicbd.module.utils.Constants.STOPPED;

/**
 * @author <a href="mailto:enmanuelcalero61@gmail.com"> Enmanuel Calero </a>
 * @author <a href="mailto:ndiazobed@gmail.com"> Obed Navarrete </a>
 */
@Slf4j
public class SmppConnectionManager {
    private static final SecureRandom secureRandom = new SecureRandom();
    private final ExecutorService mainExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ThreadFactory factory = Thread.ofVirtual().name("okay-", 0).factory();
    private final ExecutorService service = Executors.newThreadPerTaskExecutor(factory);
    @Getter
    private final List<SMPPSession> sessions = new CopyOnWriteArrayList<>();

    private final AppProperties properties;
    private final JedisCluster jedisCluster;
    private final SocketSession socketSession;
    private final AtomicInteger requestCounterTotal = new AtomicInteger(0);
    private final ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    private final ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap;
    private final MessageReceiverListenerImpl messageReceiverListener;
    private final String redisListName;
    private final CdrProcessor cdrProcessor;
    @Getter
    private final SessionStateListenerImpl sessionStateListener;

    @Getter
    private Gateway gateway;
    private boolean stopped = true;

    public SmppConnectionManager(
            JedisCluster jedisCluster, Gateway gateway, SocketSession socketSession,
            ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap,
            ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap,
            AppProperties properties, CdrProcessor cdrProcessor) {
        this.jedisCluster = jedisCluster;
        this.socketSession = socketSession;
        this.cdrProcessor = cdrProcessor;
        this.sessionStateListener = new SessionStateListenerImpl(gateway, this.socketSession, jedisCluster, sessions);
        this.gateway = gateway;
        this.errorCodeMappingConcurrentHashMap = errorCodeMappingConcurrentHashMap;
        this.routingRulesConcurrentHashMap = routingRulesConcurrentHashMap;
        this.properties = properties;
        this.messageReceiverListener = new MessageReceiverListenerImpl(
                this.gateway,
                this.jedisCluster,
                this.properties.getPreDeliverQueue(),
                this.properties.getPreMessageQueue(),
                this.properties.getSubmitSmResultQueue(),
                this.cdrProcessor
        );

        log.info("Gateway [{}] will be working with rate of [{}] tps", gateway.getSystemId(), gateway.getTps());
        Thread.startVirtualThread(() -> new Watcher("Gateway " + gateway.getSystemId(), requestCounterTotal, 1));
        this.redisListName = this.gateway.getNetworkId() + "_smpp_message";
    }

    public void updateGatewayInDeep(Gateway gateway) {
        log.debug("Updating Gateway with networkId {}, gateway {}", gateway.getNetworkId(), gateway);
        this.gateway = gateway;
        this.messageReceiverListener.setGateway(gateway);
    }

    public void startMessagesProcessor() {
        CompletableFuture.runAsync(() -> Flux.interval(Duration.ofMillis(this.properties.getGatewaysWorkExecuteEvery()))
                .flatMap(f -> {
                    if (sessions.isEmpty()) {
                        return Flux.empty();
                    }
                    return fetchAllItems()
                            .doOnNext(this::sendSubmitSmList);
                })
                .subscribe(), mainExecutor);
    }

    private Flux<List<MessageEvent>> fetchAllItems() {
        int batchSize = batchPerWorker();
        if (batchSize == 0) {
            return Flux.empty();
        }
        var wpg = this.properties.getWorkersPerGateway();
        return Flux.range(0, wpg)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(worker -> {
                    List<String> batch = jedisCluster.lpop(redisListName, batchSize);
                    Objects.requireNonNull(batch, "The batch obtained from redis should be " + batchSize + " but was null");
                    List<MessageEvent> submitSmEvents = batch
                            .parallelStream()
                            .map(msgRaw -> Converter.stringToObject(msgRaw, MessageEvent.class))
                            .filter(Objects::nonNull)
                            .toList();
                    return Flux.just(submitSmEvents);
                }).subscribeOn(Schedulers.boundedElastic());
    }

    private int batchPerWorker() {
        int recordsToTake = gateway.getTps() * (this.properties.getGatewaysWorkExecuteEvery() / 1000);
        int listSize = (int) jedisCluster.llen(redisListName);
        if (listSize == 0) {
            return 0;
        }
        int min = Math.min(recordsToTake, listSize);
        var bpw = min / properties.getWorkersPerGateway();
        return bpw > 0 ? bpw : 1;
    }

    private int handleException(Exception exception, MessageEvent submitSmEvent) {
        String specificExceptionType = exception.getClass().getSimpleName();
        String message = "Failed on send submit_sm with id {} and messageId {} due to " + specificExceptionType;
        log.warn(message, submitSmEvent.getId(), submitSmEvent.getMessageId(), exception);
        return switch (exception) {
            case PDUException ignored -> 4444;
            case ResponseTimeoutException ignored -> 5555;
            case InvalidResponseException ignored -> 6666;
            case NegativeResponseException negativeResponseException -> negativeResponseException.getCommandStatus();
            case IOException ignored -> 7777;
            default -> 8888;
        };
    }

    public void connect() {
        this.initSessions(this.gateway.getSessionsNumber() - this.sessions.size());
        this.retryConnection();
    }

    private SMPPSession createAndBindSmppSession(Gateway gateway) {
        SMPPSession smppSession = new SMPPSession();
        smppSession.addSessionStateListener(this.sessionStateListener);
        smppSession.setMessageReceiverListener(this.messageReceiverListener);
        smppSession.setTransactionTimer(gateway.getPduTimeout());
        smppSession.setEnquireLinkTimer(this.gateway.getEnquireLinkPeriod());
        smppSession.setPduProcessorDegree(this.gateway.getPduProcessorDegree());
        smppSession.setQueueCapacity(100000);

        try {
            smppSession.connectAndBind(
                    gateway.getIp(), gateway.getPort(),
                    new BindParameter(
                            UtilsEnum.getBindType(gateway.getBindType()),
                            gateway.getSystemId(),
                            gateway.getPassword(),
                            gateway.getSystemType(),
                            UtilsEnum.getTypeOfNumber(gateway.getAddressTON()),
                            UtilsEnum.getNumberingPlanIndicator(gateway.getAddressNPI()),
                            gateway.getAddressRange(),
                            UtilsEnum.getInterfaceVersion(gateway.getInterfaceVersion())
                    ));
            if (smppSession.getSessionState().isBound()) {
                log.debug("SMPP session bound to {}:{} with systemId {}", gateway.getIp(), gateway.getPort(), gateway.getSystemId());
                return smppSession;
            }
        } catch (IOException e) {
            log.error("Error while connecting to Gateway {}", gateway.getSystemId());
        }
        return null;
    }

    private void initSessions(int qq) {
        if ("stopped".equalsIgnoreCase(this.gateway.getStatus())
                || this.gateway.getEnabled() != 1
                || this.gateway.getSessionsNumber() < 1
                || this.sessions.size() >= this.gateway.getSessionsNumber()) {
            return;
        }

        log.info("Starting SMPP sessions for gateway with networkId {}, quantity {}", this.gateway.getNetworkId(), qq);
        var smppSessionsBySp = IntStream.range(0, qq)
                .parallel()
                .mapToObj(i -> this.createAndBindSmppSession(gateway))
                .filter(Objects::nonNull)
                .toList();
        sessions.addAll(smppSessionsBySp);

        if (!sessions.isEmpty()) {
            this.stopped = false;
        }
    }

    public void stopConnection() {
        this.stopped = true;
        if (!this.sessions.isEmpty()) {
            var sessionsCopy = new ArrayList<>(this.sessions);
            sessions.clear();
            for (Session session : sessionsCopy) {
                session.unbindAndClose();
            }
        }
        gateway.setStatus(STOPPED);
        gateway.setSuccessSession(0);
        this.sessionStateListener.getSuccessSession().set(0);
        this.socketSession.sendStatus(String.valueOf(gateway.getNetworkId()), PARAM_UPDATE_SESSIONS, "0");
        this.sessionStateListener.updateOnRedis();
        this.jedisCluster.hset("gateways", String.valueOf(gateway.getNetworkId()), gateway.toString());
        log.warn("Gateway with networkId {} has been stopped", gateway.getNetworkId());
    }

    private void retryConnection() {
        Runnable retryTask = () -> {
            if (this.stopped) {
                return;
            }

            if (gateway.getSuccessSession() < gateway.getSessionsNumber()) {
                this.initSessions(gateway.getSessionsNumber() - sessions.size());
            }
        };

        scheduler.scheduleAtFixedRate(retryTask, gateway.getBindRetryPeriod(), gateway.getBindRetryPeriod(), TimeUnit.MILLISECONDS);
    }

    private void addInCache(int registeredDelivery, MessageEvent submitSmEvent, SubmitSmResult submitSmResult) {
        if (registeredDelivery != 0) {
            String messageIdResponse = cleanAndUpperString(submitSmResult.getMessageId());
            log.debug("Requesting DLR for submit_sm with id {} and messageId {}", submitSmEvent.getId(), messageIdResponse);
            SubmitSmResponseEvent submitSmResponseEvent = new SubmitSmResponseEvent();
            submitSmResponseEvent.setHashId(messageIdResponse);
            submitSmResponseEvent.setSystemId(submitSmEvent.getSystemId());
            submitSmResponseEvent.setId(System.currentTimeMillis() + "-" + System.nanoTime());
            submitSmResponseEvent.setSubmitSmId(messageIdResponse);
            submitSmResponseEvent.setSubmitSmServerId(submitSmEvent.getMessageId());
            submitSmResponseEvent.setOriginProtocol(submitSmEvent.getOriginProtocol().toUpperCase());
            submitSmResponseEvent.setOriginNetworkId(submitSmEvent.getOriginNetworkId());
            submitSmResponseEvent.setOriginNetworkType(submitSmEvent.getOriginNetworkType());
            submitSmResponseEvent.setMsgReferenceNumber(submitSmEvent.getMsgReferenceNumber());
            submitSmResponseEvent.setSegmentSequence(submitSmEvent.getSegmentSequence());
            submitSmResponseEvent.setTotalSegment(submitSmEvent.getTotalSegment());
            submitSmResponseEvent.setParentId(submitSmEvent.getParentId());
            jedisCluster.hset(properties.getSubmitSmResultQueue(), submitSmResponseEvent.getHashId(), submitSmResponseEvent.toString());
        }
    }

    private void sendToRetryProcess(MessageEvent submitSmEventToRetry, int errorCode) {
        log.warn("Starting retry process for submit_sm with id {} and error code {}", submitSmEventToRetry.getMessageId(), errorCode);
        if (errorContained(gateway.getNoRetryErrorCode(), errorCode)) {
            handleNoRetryError(submitSmEventToRetry, errorCode);
            return;
        }

        if (errorContained(gateway.getRetryAlternateDestinationErrorCode(), errorCode)) {
            handleRetryAlternateDestination(submitSmEventToRetry, errorCode);
            return;
        }

        handleAutoRetry(submitSmEventToRetry, errorCode);
    }

    private void handleNoRetryError(MessageEvent submitSmEventToRetry, int errorCode) {
        log.warn("Failed to retry for submit_sm with id {}. The gateway {} contains this error code for no retry", submitSmEventToRetry.getMessageId(), this.getGateway().getName());
        sendDeliverSm(submitSmEventToRetry, errorCode);
        MessageReceiverListenerImpl.handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "ERROR IS DEFINED AS NO RETRY");
    }

    private void handleRetryAlternateDestination(MessageEvent submitSmEventToRetry, int errorCode) {
        Map.Entry<Integer, String> alternativeRoute = getAlternativeRoute(submitSmEventToRetry);
        if (alternativeRoute != null) {
            prepareForRetry(submitSmEventToRetry, alternativeRoute);
            String listName = determineListName(submitSmEventToRetry.getDestProtocol(), submitSmEventToRetry.getDestNetworkId());
            if (Objects.nonNull(listName)) {
                log.warn("Retry for submit_sm with id {}. new networkId {}", submitSmEventToRetry.getMessageId(), submitSmEventToRetry.getDestNetworkId());
                jedisCluster.rpush(listName, submitSmEventToRetry.toString());
                MessageReceiverListenerImpl.handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "MESSAGE HAS BEEN SENT TO ALTERNATIVE ROUTE");
                return;
            }
            log.warn("Failed to retry for submit_sm with id {}. No more alternative routes were found", submitSmEventToRetry.getMessageId());
            handleNoAlternativeRoute(submitSmEventToRetry, errorCode, "ALTERNATIVE ROUTE FOUND BUT LIST NAME IS NULL FOR ERROR " + errorCode);
        } else {
            log.warn("Failed to retry for submit_sm with id {}. No more alternative routes were found on else block", submitSmEventToRetry.getMessageId());
            handleNoAlternativeRoute(submitSmEventToRetry, errorCode, "NO ALTERNATIVE ROUTES WERE FOUND FOR ERROR " + errorCode);
        }
    }

    private void handleNoAlternativeRoute(MessageEvent submitSmEventToRetry, int errorCode, String message) {
        sendDeliverSm(submitSmEventToRetry, errorCode);
        MessageReceiverListenerImpl.handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, message);
    }

    private void handleAutoRetry(MessageEvent submitSmEventToRetry, int errorCode) {
        log.info("AutoRetryErrorCodes defined {}, ReceivedErrorCode {}", gateway.getAutoRetryErrorCode(), errorCode);
        if (errorContained(gateway.getAutoRetryErrorCode(), errorCode)) {
            log.info("AutoRetrying for submit_sm with id {}", submitSmEventToRetry.getMessageId());
            MessageReceiverListenerImpl.handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "MESSAGE SENT TO AUTO RETRY PROCESS DUE SMPP ERROR " + errorCode);
            submitSmEventToRetry.setRetryNumber(
                    Objects.isNull(submitSmEventToRetry.getRetryNumber()) ? 1 : submitSmEventToRetry.getRetryNumber() + 1
            );
            jedisCluster.lpush(properties.getRetryMessage(), submitSmEventToRetry.toString());
            log.info("Successfully added to Redis retry message list {} -> {}", properties.getRetryMessage(), submitSmEventToRetry);
            return;
        }

        log.warn("Failed to retry for submit_sm with id {}. The gateway {} doesn't have the error code {} for the retry process.", submitSmEventToRetry.getMessageId(), this.getGateway().getName(), errorCode);
        sendDeliverSm(submitSmEventToRetry, errorCode);
        MessageReceiverListenerImpl.handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "NOT FOUND ERROR CODE TO AUTO RETRY");
    }

    private void prepareForRetry(MessageEvent submitSmEventToRetry, Map.Entry<Integer, String> alternativeRoute) {
        submitSmEventToRetry.setRetry(true);
        String existingRetryDestNetworkId = submitSmEventToRetry.getRetryDestNetworkId();
        if (existingRetryDestNetworkId.isEmpty()) {
            submitSmEventToRetry.setRetryDestNetworkId(submitSmEventToRetry.getDestNetworkId() + "");
        } else {
            submitSmEventToRetry.setRetryDestNetworkId(existingRetryDestNetworkId + "," + submitSmEventToRetry.getDestNetworkId());
        }
        submitSmEventToRetry.setDestNetworkId(alternativeRoute.getKey());
        submitSmEventToRetry.setDestProtocol(alternativeRoute.getValue());
    }

    private String determineListName(String destProtocol, Integer destNetworkId) {
        return switch (destProtocol.toLowerCase()) {
            case "http" -> destNetworkId + "_http_message";
            case "smpp" -> destNetworkId + "_smpp_message";
            case "ss7" -> destNetworkId + "_ss7_message";
            default -> null;
        };
    }

    private boolean errorContained(String stringList, int errorCode) {
        return Arrays.stream(stringList.split(",")).toList().contains(String.valueOf(errorCode));
    }

    private Map.Entry<Integer, String> getAlternativeRoute(MessageEvent submitSmEventToRetry) {
        List<RoutingRule> routingList = this.routingRulesConcurrentHashMap.get(submitSmEventToRetry.getOriginNetworkId());
        if (routingList != null && !routingList.isEmpty()) {
            Optional<RoutingRule> optionalRouting = routingList.stream().filter(routing -> routing.getId() == submitSmEventToRetry.getRoutingId()).findFirst();
            if (optionalRouting.isPresent()) {
                List<RoutingRule.Destination> orderDestinations = optionalRouting.get().getDestination().stream()
                        .sorted(Comparator.comparingInt(RoutingRule.Destination::getPriority)).toList();
                for (RoutingRule.Destination destination : orderDestinations) {
                    if (destination.getNetworkId() != submitSmEventToRetry.getDestNetworkId()
                            && !errorContained(submitSmEventToRetry.getRetryDestNetworkId(), destination.getNetworkId())) {
                        return Map.entry(destination.getNetworkId(), destination.getProtocol());
                    }
                }
            }
        }
        return null;
    }

    private void sendDeliverSm(MessageEvent submitSmEventToRetry, int errorCode) {
        MessageEvent deliverSmEvent = createDeliverSm(submitSmEventToRetry, errorCode);
        switch (submitSmEventToRetry.getOriginProtocol().toUpperCase()) {
            case "HTTP" -> jedisCluster.rpush("http_dlr", deliverSmEvent.toString());
            case "SMPP" -> jedisCluster.rpush("smpp_dlr", deliverSmEvent.toString());
            default -> log.error("Invalid Origin Protocol");
        }
        MessageReceiverListenerImpl.handlerCdrDetail(deliverSmEvent, UtilsEnum.MessageType.DELIVER, UtilsEnum.CdrStatus.FAILED, cdrProcessor, false, "MESSAGE HAS BEEN FAILED, SENDING DLR");
    }

    private MessageEvent createDeliverSm(MessageEvent submitSmEventToRetry, int errorCode) {
        MessageEvent deliverSmEvent = new MessageEvent();
        DeliveryReceiptState deliveryReceiptState = DeliveryReceiptState.UNDELIV;
        DeliveryReceipt delRec = new DeliveryReceipt(submitSmEventToRetry.getMessageId(), 1, 0,
                new Date(), new Date(), deliveryReceiptState, String.valueOf(errorCode), "");

        List<ErrorCodeMapping> errorCodeMappingList = errorCodeMappingConcurrentHashMap.get(String.valueOf(gateway.getMno()));
        if (errorCodeMappingList != null) {
            Optional<ErrorCodeMapping> optionalErrorCodeMapping = errorCodeMappingList.stream().filter(errorCodeMapping -> errorCodeMapping.getErrorCode() == errorCode).findFirst();
            if (optionalErrorCodeMapping.isPresent()) {
                deliveryReceiptState = UtilsEnum.getDeliverReceiptState(optionalErrorCodeMapping.get().getDeliveryStatus());
                String error = optionalErrorCodeMapping.get().getDeliveryErrorCode() + "";
                delRec.setFinalStatus(deliveryReceiptState);
                delRec.setError(error);
                log.warn("Creating deliver_sm with status {} and error {} for submit_sm with id {}", deliveryReceiptState, error, submitSmEventToRetry.getMessageId());
            } else {
                log.warn("No error code mapping found for mno {} with error {}. using status {}", gateway.getMno(), errorCode, DeliveryReceiptState.UNDELIV);
            }
        } else {
            log.warn("No error code mapping found for mno {} using status {}", gateway.getMno(), DeliveryReceiptState.UNDELIV);
        }

        deliverSmEvent.setId(System.currentTimeMillis() + "-" + System.nanoTime());
        deliverSmEvent.setMessageId(submitSmEventToRetry.getMessageId());
        deliverSmEvent.setParentId(submitSmEventToRetry.getMessageId());
        deliverSmEvent.setSystemId(submitSmEventToRetry.getSystemId());
        deliverSmEvent.setCommandStatus(submitSmEventToRetry.getCommandStatus());
        deliverSmEvent.setSequenceNumber(submitSmEventToRetry.getSequenceNumber());
        deliverSmEvent.setSourceAddrTon(submitSmEventToRetry.getDestAddrTon());
        deliverSmEvent.setSourceAddrNpi(submitSmEventToRetry.getDestAddrNpi());
        deliverSmEvent.setSourceAddr(submitSmEventToRetry.getDestinationAddr());
        deliverSmEvent.setDestAddrTon(submitSmEventToRetry.getSourceAddrTon());
        deliverSmEvent.setDestAddrNpi(submitSmEventToRetry.getSourceAddrNpi());
        deliverSmEvent.setDestinationAddr(submitSmEventToRetry.getSourceAddr());
        deliverSmEvent.setOriginNetworkType(submitSmEventToRetry.getDestNetworkType());
        deliverSmEvent.setOriginProtocol(submitSmEventToRetry.getDestProtocol());
        deliverSmEvent.setOriginNetworkId(submitSmEventToRetry.getDestNetworkId());
        deliverSmEvent.setDestNetworkType(submitSmEventToRetry.getOriginNetworkType());
        deliverSmEvent.setDestProtocol(submitSmEventToRetry.getOriginProtocol());
        deliverSmEvent.setDestNetworkId(submitSmEventToRetry.getOriginNetworkId());
        deliverSmEvent.setEsmClass(submitSmEventToRetry.getEsmClass());
        deliverSmEvent.setValidityPeriod(submitSmEventToRetry.getValidityPeriod());
        deliverSmEvent.setRegisteredDelivery(submitSmEventToRetry.getRegisteredDelivery());
        deliverSmEvent.setDataCoding(submitSmEventToRetry.getDataCoding());
        deliverSmEvent.setSmDefaultMsgId(submitSmEventToRetry.getSmDefaultMsgId());
        deliverSmEvent.setShortMessage(delRec.toString());
        deliverSmEvent.setDelReceipt(delRec.toString());
        deliverSmEvent.setDeliverSmServerId(submitSmEventToRetry.getMessageId());
        deliverSmEvent.setCheckSubmitSmResponse(false);
        deliverSmEvent.setRoutingId(submitSmEventToRetry.getRoutingId());
        return deliverSmEvent;
    }

    private void sendSubmitSmList(List<MessageEvent> events) {
        Flux.fromIterable(events)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(submitSmEvent -> {
                    try {
                        if (submitSmEvent.getMessageParts() != null) {
                            submitSmEvent.getMessageParts().forEach(msgPart -> {
                                var messageEvent = new MessageEvent().clone(submitSmEvent);
                                messageEvent.setMessageId(msgPart.getMessageId());
                                messageEvent.setShortMessage(msgPart.getShortMessage());
                                messageEvent.setMsgReferenceNumber(msgPart.getMsgReferenceNumber());
                                messageEvent.setTotalSegment(msgPart.getTotalSegment());
                                messageEvent.setSegmentSequence(msgPart.getSegmentSequence());
                                messageEvent.setUdhJson(msgPart.getUdhJson());
                                messageEvent.setOptionalParameters(msgPart.getOptionalParameters());
                                sendMessage(messageEvent);
                            });
                        } else {
                            sendMessage(submitSmEvent);
                        }

                    } catch (Exception exception) {
                        log.error("Failed to send submit sm list", exception);
                    }
                }).subscribe();
    }

    private void sendMessage(MessageEvent submitSmEvent) {
        try {
            int encodingType = SmppUtils.determineEncodingType(submitSmEvent.getDataCoding(), this.gateway);
            DataCoding dataCoding = SmppEncoding.getDataCoding(submitSmEvent.getDataCoding());
            byte[] encodedShortMessage = SmppEncoding.encodeMessage(submitSmEvent.getShortMessage(), encodingType);

            // udh message type
            if (submitSmEvent.getUdhJson() != null && !submitSmEvent.getUdhJson().isBlank()) {
                Map<String, Object> jsonMap = Converter.stringToObject(submitSmEvent.getUdhJson(), new TypeReference<>() {
                });
                encodedShortMessage = Converter.paramsToUdhBytes(jsonMap, encodingType, true);
            }

            SubmitSmResult submitSmResult = this.getRandomSession().submitShortMessage(
                    null,
                    UtilsEnum.getTypeOfNumber(submitSmEvent.getSourceAddrTon()), UtilsEnum.getNumberingPlanIndicator(submitSmEvent.getSourceAddrNpi()), submitSmEvent.getSourceAddr(),
                    UtilsEnum.getTypeOfNumber(submitSmEvent.getDestAddrTon()), UtilsEnum.getNumberingPlanIndicator(submitSmEvent.getDestAddrNpi()), submitSmEvent.getDestinationAddr(),
                    new ESMClass(submitSmEvent.getEsmClass()),
                    (byte) 0,
                    (byte) 0,
                    null,
                    submitSmEvent.getStringValidityPeriod(),
                    new RegisteredDelivery(submitSmEvent.getRegisteredDelivery()),
                    (byte) 0,
                    dataCoding,
                    (byte) 0,
                    encodedShortMessage,
                    Objects.nonNull(submitSmEvent.getOptionalParameters()) ? SmppUtils.getTLV(submitSmEvent) : new OptionalParameter[0]);
            requestCounterTotal.incrementAndGet();
            MessageReceiverListenerImpl.handlerCdrDetail(submitSmEvent, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.SENT, cdrProcessor, true, "Sent to GW");
            service.execute(() -> addInCache(submitSmEvent.getRegisteredDelivery(), submitSmEvent, submitSmResult));
        } catch (Exception e) {
            handleExceptionForErrorOnSendSubmitSm(submitSmEvent, e);
        }
    }

    private void handleExceptionForErrorOnSendSubmitSm(MessageEvent submitSmEvent, Exception exception) {
        int errorHandlingResult = handleException(exception, submitSmEvent);
        log.debug("Error handling result for submit_sm with id {} is {}. " +
                        "Validity period is: {}, isLastRetry: {}",
                submitSmEvent.getMessageId(), errorHandlingResult,
                submitSmEvent.getValidityPeriod(), submitSmEvent.isLastRetry());

        if (submitSmEvent.getValidityPeriod() == 0) {
            handlingDlrForFailedMessage(submitSmEvent, errorHandlingResult, "THE VALIDITY PERIOD IS 0");
            return;
        }

        if (submitSmEvent.isLastRetry()) {
            handlingDlrForFailedMessage(submitSmEvent, errorHandlingResult, "THE MESSAGE HAS REACHED THE VALIDITY PERIOD");
            return;
        }

        sendToRetryProcess(submitSmEvent, errorHandlingResult);
    }

    private void handlingDlrForFailedMessage(MessageEvent submitSmEvent, int errorCode, String message) {
        MessageReceiverListenerImpl.handlerCdrDetail(submitSmEvent, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, message);
        sendDeliverSm(submitSmEvent, errorCode);
    }

    private SMPPSession getRandomSession() {
        return sessions.get(secureRandom.nextInt(sessions.size()));
    }

    public static String cleanAndUpperString(String messageId) {
        return messageId.replaceFirst("^0+", "").toUpperCase();
    }
}
