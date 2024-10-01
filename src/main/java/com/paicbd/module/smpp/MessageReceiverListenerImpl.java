package com.paicbd.module.smpp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.module.utils.SmppUtils;
import com.paicbd.smsc.dto.SubmitSmResponseEvent;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.MessageIDGeneratorImpl;
import com.paicbd.smsc.utils.SmppEncoding;
import com.paicbd.smsc.utils.UtilsEnum;
import com.paicbd.smsc.utils.Watcher;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.SMPPConstant;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.EnquireLink;
import org.jsmpp.bean.MessageRequest;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.IntUtil;
import org.jsmpp.util.InvalidDeliveryReceiptException;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import reactor.core.publisher.Flux;
import redis.clients.jedis.JedisCluster;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jsmpp.SMPPConstant.STAT_ESME_RSYSERR;
import static com.paicbd.module.utils.Constants.ORIGIN_GATEWAY_TYPE;

/**
 * @author <a href="mailto:enmanuelcalero61@gmail.com"> Enmanuel Calero </a>
 * @author <a href="mailto:ndiazobed@gmail.com"> Obed Navarrete </a>
 */
@Slf4j
public class MessageReceiverListenerImpl implements MessageReceiverListener {
    private final String preDeliverQueue;
    private final String preMessageQueue;
    private final CdrProcessor cdrProcessor;
    private final JedisCluster jedisCluster;
    private final String submitSmResultQueue;
    private final AtomicInteger receivedSubmitSm = new AtomicInteger(0);
    private final AtomicInteger receivedDeliverSm = new AtomicInteger(0);
    private final MessageIDGenerator messageIDGenerator = new MessageIDGeneratorImpl();

    @Setter
    private Gateway gateway;

    public MessageReceiverListenerImpl(Gateway gateway, JedisCluster jedisCluster, String preDeliverQueue, String preMessageQueue, String submitSmResultQueue, CdrProcessor cdrProcessor) {
        this.gateway = gateway;
        this.jedisCluster = jedisCluster;
        this.preDeliverQueue = preDeliverQueue;
        this.preMessageQueue = preMessageQueue;
        this.submitSmResultQueue = submitSmResultQueue;
        this.cdrProcessor = cdrProcessor;
        new Watcher("submit_sm received per second by " + gateway.getSystemId(), receivedSubmitSm, 1);
        new Watcher("deliver_sm received per second by " + gateway.getSystemId(), receivedDeliverSm, 1);
    }

    @Override
    public void onAcceptDeliverSm(final DeliverSm deliverSm) {
        boolean isReceipt = MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass());
        boolean isDefault = MessageType.DEFAULT.containedIn(deliverSm.getEsmClass());

        if (isReceipt || isDefault) {
            Flux.just(deliverSm)
                .flatMap(deliverSmEvent -> {
                    try {
                        return Flux.just(getDeliverSmEvent(deliverSm));
                    } catch (InvalidDeliveryReceiptException e) {
                        log.error("Error on getDeliverSmEvent {}", e.getMessage());
                        return Flux.empty();
                    }
                })
                .doOnNext(deliverSmEvent -> {
                    deliverSmEvent.setSystemId(this.gateway.getSystemId());
                    deliverSmEvent.setRegisteredDelivery(this.gateway.isRequestDLR() ? 1 : 0);
                    addInQ(deliverSm, deliverSmEvent);
                })
                .subscribe();
        }
    }

    @Override
    public void onAcceptAlertNotification(AlertNotification alertNotification) {
        log.debug("onAcceptAlertNotification: {} {}", alertNotification.getSourceAddr(), alertNotification.getEsmeAddr());
    }

    @Override
    public DataSmResult onAcceptDataSm(final DataSm dataSm, final Session source)
            throws ProcessRequestException {
        log.debug("The data_sm is not implemented onAcceptDataSm: {} {} {}", source.getSessionId(), dataSm.getSourceAddr(), dataSm.getDestAddress());
        throw new ProcessRequestException("The data_sm is not implemented", STAT_ESME_RSYSERR);
    }

    @Override
    public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPSession smppSession) throws ProcessRequestException {
        byte dataCoding = submitSm.getDataCoding();
        if (dataCoding != SmppEncoding.DCS_0 && dataCoding != SmppEncoding.DCS_3 && dataCoding != SmppEncoding.DCS_8) {
            log.info("Invalid data coding {} for session: {}", dataCoding, smppSession.getSessionId());
            throw new ProcessRequestException("Invalid data coding", SMPPConstant.STAT_ESME_RINVDCS);
        }

        MessageId messageId = messageIDGenerator.newMessageId();
        addInQ(submitSm, messageId);

        return new SubmitSmResult(messageId, new OptionalParameter[0]);
    }

    private void addInQ(DeliverSm deliverSm, MessageEvent deliverSmEvent) {
        try {
            boolean isDeliveryReceipt = MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass());
            if (isDeliveryReceipt) {
                DeliveryReceipt delReceipt = deliverSm.getShortMessageAsDeliveryReceipt();
                if (this.gateway.isTlvMessageReceiptId()) {
                    log.debug("Getting message_id from the TLV -> RECEIPTED_MESSAGE_ID (0x001E)");
                    OptionalParameter receiptMessageId = deliverSm.getOptionalParameter(OptionalParameter.Tag.RECEIPTED_MESSAGE_ID);
                    if (Objects.isNull(receiptMessageId)) {
                        log.error("The message_id is not present in the TLV, this deliver_sm can not be processed {}", delReceipt.toString());
                        return;
                    }
                    var recMsgId = (OptionalParameter.Receipted_message_id) receiptMessageId;
                    deliverSmEvent.setDeliverSmId(getMessageId(recMsgId.getValueAsString()));
                } else {
                    log.debug("Getting message_id from the DeliveryReceipt object");
                    deliverSmEvent.setDeliverSmId(getMessageId(delReceipt.getId()));
                }

                deliverSmEvent.setStatus(delReceipt.getFinalStatus().name());
                deliverSmEvent.setErrorCode(delReceipt.getError());

                String key = deliverSmEvent.getDeliverSmId();
                String existSubmitResp = jedisCluster.hget(this.submitSmResultQueue, key);
                if (Objects.isNull(existSubmitResp)) {
                    log.error("The submit_sm response is not present, this deliver_sm can not be processed {}", delReceipt);
                    return;
                }

                SubmitSmResponseEvent submitSmResponseEvent = Converter.stringToObject(existSubmitResp, new TypeReference<>() {});
                deliverSmEvent.setMessageId(submitSmResponseEvent.getSubmitSmServerId());
                deliverSmEvent.setParentId(submitSmResponseEvent.getParentId());
            } else {
                MessageId messageId = messageIDGenerator.newMessageId();
                deliverSmEvent.setMessageId(messageId.toString());
                deliverSmEvent.setDeliverSmId(messageId.toString());
                deliverSmEvent.setParentId(messageId.toString());
            }

            if (deliverSm.getOptionalParameters() != null && deliverSm.getOptionalParameters().length >= 1) {
                deliverSmEvent.setOptionalParameters(SmppUtils.setTLV(deliverSm.getOptionalParameters()));
            }

            deliverSmEvent.setSystemId(this.gateway.getSystemId());
            deliverSmEvent.setOriginNetworkId(this.gateway.getNetworkId());
            deliverSmEvent.setOriginProtocol(this.gateway.getProtocol());
            deliverSmEvent.setOriginNetworkType(ORIGIN_GATEWAY_TYPE);

            jedisCluster.lpush(preDeliverQueue, deliverSmEvent.toString());
            handlerCdrDetail(deliverSmEvent, UtilsEnum.MessageType.DELIVER, UtilsEnum.CdrStatus.RECEIVED, cdrProcessor, false, "RECEIVED FROM GW");
            receivedDeliverSm.incrementAndGet();
        } catch (Exception e) {
            log.error("Error on send to the queue {}", e.getMessage());
        }
    }

    private MessageEvent getDeliverSmEvent(DeliverSm deliverSm) throws InvalidDeliveryReceiptException {

        int encodingType = SmppUtils.determineEncodingType(deliverSm.getDataCoding(), this.gateway);
        String decodedMessage = SmppEncoding.decodeMessage(deliverSm.getShortMessage(), encodingType);

        MessageEvent deliverSmEvent = new MessageEvent();
        deliverSmEvent.setId(System.currentTimeMillis() + "-" + System.nanoTime());
        deliverSmEvent.setRegisteredDelivery((int) deliverSm.getRegisteredDelivery());
        this.setDataToMessageEvent(deliverSmEvent, deliverSm);
        deliverSmEvent.setShortMessage(decodedMessage);
        if (MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass())){
            deliverSmEvent.setDelReceipt(deliverSm.getShortMessageAsDeliveryReceipt().toString());
            deliverSmEvent.setCheckSubmitSmResponse(true);
        } else {
            deliverSmEvent.setDelReceipt(decodedMessage);
            deliverSmEvent.setCheckSubmitSmResponse(false);
        }

        return deliverSmEvent;
    }

    private String getMessageId(String id) {
        if (this.gateway.isMessageIdDecimalFormat()) {
            log.debug("The message_id is in decimal format");
            return Long.toHexString(Long.parseLong(id)).toUpperCase();
        }
        log.debug("The message_id is in hexadecimal format");
        return id;
    }

    @Override
    public void onAcceptEnquireLink(EnquireLink enquireLink, Session source) {
        MessageReceiverListener.super.onAcceptEnquireLink(enquireLink, source);
    }

    /**
     * handling input submitSm messages
     */
    private void addInQ(SubmitSm submitSm, MessageId messageId) {
        MessageEvent submitSmEvent = createSubmitSmEvent(submitSm, messageId);
        submitSmEvent.setOriginNetworkType(ORIGIN_GATEWAY_TYPE);
        submitSmEvent.setOriginProtocol("SMPP");

        jedisCluster.lpush(preMessageQueue, submitSmEvent.toString());
        handlerCdrDetail(submitSmEvent, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.RECEIVED, cdrProcessor, false, "RECEIVED FROM GW");
        receivedSubmitSm.incrementAndGet();
    }

    private MessageEvent createSubmitSmEvent(SubmitSm submitSm, MessageId messageId) {
        MessageEvent event = getSubmitSmEvent(submitSm, gateway);
        event.setSystemId(gateway.getSystemId());
        event.setOriginNetworkId(gateway.getNetworkId());
        event.setId(System.currentTimeMillis() + "-" + System.nanoTime());
        event.setMessageId(messageId.getValue());
        event.setParentId(messageId.getValue());

        if (submitSm.getOptionalParameters() != null && submitSm.getOptionalParameters().length >= 1) {
            event.setOptionalParameters(SmppUtils.setTLV(submitSm.getOptionalParameters()));
        }

        return event;
    }

    private MessageEvent getSubmitSmEvent(SubmitSm submitSm, Gateway gateway) {
        int encodingType = SmppUtils.determineEncodingType(submitSm.getDataCoding(), gateway);
        String decodedMessage = SmppEncoding.decodeMessage(submitSm.getShortMessage(), encodingType);
        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setRetry(false);
        submitSmEvent.setRetryDestNetworkId("");
        submitSmEvent.setRegisteredDelivery(gateway.isRequestDLR() ? 1 : 0);
        this.setDataToMessageEvent(submitSmEvent, submitSm);
        submitSmEvent.setShortMessage(decodedMessage);
        return submitSmEvent;
    }

    public static void handlerCdrDetail(MessageEvent messageEvent, UtilsEnum.MessageType messageType, UtilsEnum.CdrStatus cdrStatus, CdrProcessor cdrProcessor, boolean createCdr, String message) {
        cdrProcessor.putCdrDetailOnRedis(
                messageEvent.toCdrDetail(UtilsEnum.Module.SMPP_CLIENT, messageType, cdrStatus, message.toUpperCase()));

        if (createCdr) {
            cdrProcessor.createCdr(messageEvent.getMessageId());
        }
    }

    public void setDataToMessageEvent(MessageEvent messageEvent, MessageRequest messageRequest) {
        messageEvent.setCommandStatus(messageRequest.getCommandStatus());
        messageEvent.setSequenceNumber(messageRequest.getSequenceNumber());
        messageEvent.setSourceAddrTon((int) messageRequest.getSourceAddrTon());
        messageEvent.setSourceAddrNpi((int) messageRequest.getSourceAddrNpi());
        messageEvent.setSourceAddr(messageRequest.getSourceAddr());
        messageEvent.setDestAddrTon((int) messageRequest.getDestAddrTon());
        messageEvent.setDestAddrNpi((int) messageRequest.getDestAddrNpi());
        messageEvent.setDestinationAddr(messageRequest.getDestAddress());
        messageEvent.setEsmClass((int) messageRequest.getEsmClass());
        messageEvent.setValidityPeriod(messageRequest.getValidityPeriod());
        messageEvent.setDataCoding((int) messageRequest.getDataCoding());
        messageEvent.setSmDefaultMsgId(messageRequest.getSmDefaultMsgId());
    }
}
