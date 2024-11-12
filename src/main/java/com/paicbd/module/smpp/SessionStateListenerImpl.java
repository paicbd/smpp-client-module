package com.paicbd.module.smpp;

import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.ws.SocketSession;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SessionStateListener;
import redis.clients.jedis.JedisCluster;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.paicbd.module.utils.Constants.BINDING;
import static com.paicbd.module.utils.Constants.BOUND;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_SESSIONS;
import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.UNBINDING;
import static com.paicbd.module.utils.Constants.UNBOUND;

@Slf4j
public class SessionStateListenerImpl implements SessionStateListener {
    private final SocketSession socketSession;
    private final Gateway gateway;
    private final int networkId;
    private final List<SMPPSession> sessions;
    @Getter
    private final AtomicInteger successSession = new AtomicInteger(0);
    private final JedisCluster jedisCluster;
    private static final EnumSet<SessionState> BOUND_STATES = EnumSet.of(
            SessionState.BOUND_RX,
            SessionState.BOUND_TX,
            SessionState.BOUND_TRX
    );

    public SessionStateListenerImpl(Gateway gateway, SocketSession socketSession, JedisCluster jedisCluster, List<SMPPSession> sessions) {
        this.gateway = gateway;
        this.networkId = gateway.getNetworkId();
        this.socketSession = socketSession;
        this.jedisCluster = jedisCluster;
        this.sessions = sessions;
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

            updateOnRedis();
        } else if (newState == SessionState.CLOSED) {
            if (gateway.getSuccessSession() <= 0) {
                gateway.setSuccessSession(0);
                this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_SESSIONS, "0");
                return;
            }

            sessions.remove(castedSource);
            if (gateway.getSuccessSession() == 1) {
                gateway.setStatus(UNBINDING);
                waitForSessionState();
                this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_STATUS, UNBINDING);
            }

            gateway.setSuccessSession(successSession.decrementAndGet());
            this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_SESSIONS, "-1");

            if (gateway.getSuccessSession() == 0) {
                gateway.setStatus(UNBOUND);
                waitForSessionState();
                this.socketSession.sendStatus(String.valueOf(networkId), PARAM_UPDATE_STATUS, UNBOUND);
            }

            updateOnRedis();
        }
    }

    private boolean isBoundState(SessionState state) {
        return BOUND_STATES.contains(state);
    }

    public void updateOnRedis() {
        jedisCluster.hset("gateways", String.valueOf(networkId), this.gateway.toString());
    }

    public void waitForSessionState() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            log.error("An error has occurred: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
}
