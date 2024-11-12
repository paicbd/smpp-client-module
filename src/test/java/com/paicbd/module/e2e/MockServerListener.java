package com.paicbd.module.e2e;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.PDUStringException;
import org.jsmpp.bean.BroadcastSm;
import org.jsmpp.bean.CancelBroadcastSm;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.DataCodings;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.MessageMode;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.QueryBroadcastSm;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.BroadcastSmResult;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.QueryBroadcastSmResult;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPServerSession;
import org.jsmpp.session.ServerMessageReceiverListener;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.jsmpp.session.SubmitMultiResult;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.DeliveryReceiptState;
import org.jsmpp.util.MessageId;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class MockServerListener implements SessionStateListener, ServerMessageReceiverListener {
    private static final Random RANDOM = new Random();
    private static final boolean DELIVER_DECIMAL_FORMAT = false;
    private static final EnumSet<SessionState> BOUND_STATES = EnumSet.of(
            SessionState.BOUND_RX,
            SessionState.BOUND_TX,
            SessionState.BOUND_TRX
    );
    private final List<Session> sessions = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Exception forcedException;

    @Override
    @SneakyThrows
    public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPServerSession smppServerSession) {
        log.info("SubmitSm received {}", submitSm);

        if (Objects.nonNull(forcedException)) {
            if (forcedException instanceof ResponseTimeoutException) {
                try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
                    executorService.schedule(() -> {
                    }, 6, TimeUnit.SECONDS);
                }
            }
            throw forcedException;
        }

        String hexMessageId = this.generateRandomHex(8);
        String finalMessageId = hexMessageId;

        if (DELIVER_DECIMAL_FORMAT) {
            log.info("Delivery receipt in decimal format");
            long messageIdLong = Long.parseLong(hexMessageId, 16);
            finalMessageId = String.valueOf(messageIdLong);
        }

        this.scheduleDeliveryReceipt(submitSm, finalMessageId);
        return createSubmitSmResult(hexMessageId);
    }

    @Override
    public SubmitMultiResult onAcceptSubmitMulti(SubmitMulti submitMulti, SMPPServerSession smppServerSession) {
        log.info("SubmitMulti received {}", submitMulti);
        return null;
    }

    @Override
    public QuerySmResult onAcceptQuerySm(QuerySm querySm, SMPPServerSession smppServerSession) {
        log.info("QuerySm received {}", querySm);
        return null;
    }

    @Override
    public void onAcceptReplaceSm(ReplaceSm replaceSm, SMPPServerSession smppServerSession) {
        log.info("ReplaceSm received {}", replaceSm);
    }

    @Override
    public void onAcceptCancelSm(CancelSm cancelSm, SMPPServerSession smppServerSession) {
        log.info("CancelSm received {}", cancelSm);
    }

    @Override
    public BroadcastSmResult onAcceptBroadcastSm(BroadcastSm broadcastSm, SMPPServerSession smppServerSession) {
        log.info("BroadcastSm received {}", broadcastSm);
        return null;
    }

    @Override
    public void onAcceptCancelBroadcastSm(CancelBroadcastSm cancelBroadcastSm, SMPPServerSession smppServerSession) {
        log.info("CancelBroadcastSm received {}", cancelBroadcastSm);
    }

    @Override
    public QueryBroadcastSmResult onAcceptQueryBroadcastSm(QueryBroadcastSm queryBroadcastSm, SMPPServerSession smppServerSession) {
        log.info("QueryBroadcastSm received {}", queryBroadcastSm);
        return null;
    }

    @Override
    public DataSmResult onAcceptDataSm(DataSm dataSm, Session session) {
        log.info("DataSm received {}", dataSm);
        return null;
    }

    @Override
    @Synchronized
    public void onStateChange(SessionState newState, SessionState oldState, Session source) {
        if (this.isBoundState(newState)) {
            this.sessions.add(source);
            log.info("Session {} added", source.getSessionId());
        } else if (newState.equals(SessionState.CLOSED)) {
            this.sessions.remove(source);
            log.info("Session {} removed", source.getSessionId());
        }
    }

    private void scheduleDeliveryReceipt(SubmitSm submitSm, String messageId) {
        this.scheduler.schedule(() ->
                this.sendReactiveDeliverSm(getRandomSessionForClient(), messageId, 1, submitSm)
                        .subscribe(), 2, TimeUnit.SECONDS);
    }

    private SMPPServerSession getRandomSessionForClient() {
        log.debug("Getting random session");
        return (SMPPServerSession) sessions.get(ThreadLocalRandom.current().nextInt(sessions.size()));
    }

    private SubmitSmResult createSubmitSmResult(String messageId) throws ProcessRequestException {
        try {
            return new SubmitSmResult(new MessageId(messageId), null);
        } catch (PDUStringException e) {
            log.error("Error on SubmitSmResult {}", e.getMessage());
            throw new ProcessRequestException(e.getMessage(), e.getErrorCode(), e);
        }
    }

    public Mono<Void> sendReactiveDeliverSm(SMPPServerSession serverSession, String messageId, int tlv, SubmitSm submitSm) {
        return Mono.just(submitSm)
                .filter(SubmitSm::isSmscDelReceiptSuccessAndFailureRequested)
                .flatMap(sm -> {
                    DeliveryReceiptState state = DeliveryReceiptState.DELIVRD;
                    List<OptionalParameter> optionalParameters = new ArrayList<>();
                    if (tlv == 1) {
                        optionalParameters.add(new OptionalParameter.Receipted_message_id(messageId));
                        optionalParameters.add(new OptionalParameter.Message_state((byte) state.value()));
                    }

                    try {
                        var delRec = new DeliveryReceipt(messageId, 1, 1, new Date(), new Date(), state, "000", "");
                        serverSession.deliverShortMessage(
                                "mc",
                                TypeOfNumber.INTERNATIONAL,
                                NumberingPlanIndicator.ISDN,
                                submitSm.getDestAddress(),
                                TypeOfNumber.INTERNATIONAL,
                                NumberingPlanIndicator.ISDN,
                                submitSm.getSourceAddr(),
                                new ESMClass(MessageMode.DEFAULT, MessageType.SMSC_DEL_RECEIPT, GSMSpecificFeature.DEFAULT),
                                (byte) 0,
                                (byte) 0,
                                new RegisteredDelivery(0),
                                DataCodings.ZERO,
                                delRec.toString().getBytes(),
                                optionalParameters.toArray(new OptionalParameter[0]));
                    } catch (Exception e) {
                        log.error("Error on process deliverSm {} ex -> {}", submitSm, e.getMessage());
                    }
                    return Mono.empty();
                });
    }

    private boolean isBoundState(SessionState state) {
        return BOUND_STATES.contains(state);
    }

    public String generateRandomHex(int length) {
        byte[] bytes = new byte[length / 2];
        RANDOM.nextBytes(bytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
