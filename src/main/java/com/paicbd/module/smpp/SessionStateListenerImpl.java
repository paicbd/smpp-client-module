package com.paicbd.module.smpp;

import com.paicbd.smsc.dto.BindEvent;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.kafka.KafkaTopicsConstants;
import com.paicbd.smsc.utils.GeneralSmscConstants;
import com.paicbd.smsc.ws.SocketSession;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import org.springframework.kafka.core.KafkaTemplate;
import com.paicbd.smsc.utils.RedisManager;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static com.paicbd.module.utils.Constants.BINDING;
import static com.paicbd.module.utils.Constants.BOUND;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_SESSIONS;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.UNBINDING;
import static com.paicbd.module.utils.Constants.UNBOUND;

@Slf4j
public class SessionStateListenerImpl implements SessionStateListener {
    private static final EnumSet<SessionState> BOUND_STATES = EnumSet.of(
            SessionState.BOUND_RX,
            SessionState.BOUND_TX,
            SessionState.BOUND_TRX
    );

    private final SocketSession socketSession;
    private final int networkId;
    private final List<SMPPSession> sessions;
    private final Runnable onStateChangedCallback;
    @Getter
    private final AtomicInteger successSession = new AtomicInteger(0);
    private final RedisManager redisManager;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final boolean bindNotificationEnabled;
    @Setter
    private Gateway gateway;

    public SessionStateListenerImpl(
            Gateway gateway, SocketSession socketSession, RedisManager redisManager,
            List<SMPPSession> sessions, Runnable onStateChangedCallback,
            KafkaTemplate<String, String> kafkaTemplate, boolean bindNotificationEnabled) {
        this.gateway = gateway;
        this.networkId = gateway.getNetworkId();
        this.socketSession = socketSession;
        this.redisManager = redisManager;
        this.sessions = sessions;
        this.onStateChangedCallback = onStateChangedCallback;
        this.kafkaTemplate = kafkaTemplate;
        this.bindNotificationEnabled = bindNotificationEnabled;
    }

    @Override
    public synchronized void onStateChange(SessionState newState, SessionState oldState, Session source) {
        log.debug("SMPP session state changed from {} to {} for session {}", oldState, newState, source.getSessionId());
        SMPPSession castedSource = (SMPPSession) source;
        if (isBoundState(newState)) {
            if (gateway.getSuccessSession() == 0) {
                waitForSessionState();
                gateway.setStatus(BINDING);
                this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_STATUS, BINDING);
            }

            if (gateway.getSessionsNumber() > gateway.getSuccessSession()) {
                gateway.setSuccessSession(successSession.incrementAndGet());
            }

            this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_SESSIONS, "1");

            if (gateway.getSuccessSession() == 1) {
                gateway.setStatus(BOUND);
                waitForSessionState();
                this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_STATUS, BOUND);
            }

            this.publishBindEvent("BIND", source.getSessionId(), gateway.getIp());
            this.updateOnRedis();
        } else if (newState == SessionState.CLOSED) {
            if (gateway.getSuccessSession() <= 0) {
                gateway.setSuccessSession(0);
                this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_SESSIONS, "0");
                return;
            }

            sessions.remove(castedSource);
            this.runOnStateChangedCallback();

            if (gateway.getSuccessSession() == 1) {
                gateway.setStatus(UNBINDING);
                waitForSessionState();
                this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_STATUS, UNBINDING);
            }

            gateway.setSuccessSession(successSession.decrementAndGet());
            this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_SESSIONS, "-1");

            this.publishBindEvent("UNBIND", source.getSessionId(), gateway.getIp());

            if (gateway.getSuccessSession() == 0) {
                gateway.setStatus(UNBOUND);
                waitForSessionState();
                this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_STATUS, UNBOUND);
            }

            this.updateOnRedis();
        }
    }

    private boolean isBoundState(SessionState state) {
        return BOUND_STATES.contains(state);
    }

    public void updateOnRedis() {
        redisManager.hset(GeneralSmscConstants.SMPP_HTTP_GATEWAYS_HASH_NAME, String.valueOf(networkId), this.gateway.toString());
    }

    public void waitForSessionState() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            log.error("An error has occurred: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private void runOnStateChangedCallback() {
        if (Objects.nonNull(this.onStateChangedCallback)) {
            this.onStateChangedCallback.run();
        }
    }

    private void publishBindEvent(String eventType, String sessionId, String host) {
        if (!bindNotificationEnabled) {
            return;
        }
        BindEvent bindEvent = BindEvent.builder()
                .eventType(eventType)
                .networkId(gateway.getNetworkId())
                .systemId(gateway.getSystemId())
                .host(host)
                .sessionId(sessionId)
                .timestamp(System.currentTimeMillis())
                .build();
        kafkaTemplate.send(KafkaTopicsConstants.SMPP_BIND_EVENTS_TOPIC, bindEvent.toString());
        log.info("Published {} event for networkId {} from host {}", eventType, gateway.getNetworkId(), host);
    }
}
