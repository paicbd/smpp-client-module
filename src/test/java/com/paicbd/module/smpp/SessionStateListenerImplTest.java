package com.paicbd.module.smpp;

import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.ws.SocketSession;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.List;

import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionStateListenerImplTest {
    @Mock
    SocketSession socketSession;

    @Mock
    Gateway gateway;

    @Mock
    List<SMPPSession> sessions;

    @Mock
    JedisCluster jedisCluster;

    @InjectMocks
    SessionStateListenerImpl sessionStateListener;

    @BeforeEach
    void setUp() {
        this.gateway = Gateway.builder()
                .networkId(1)
                .name("SMPP-Operator")
                .systemId("op_01_smpp_gw")
                .sessionsNumber(10)
                .tps(10)
                .successSession(0)
                .status("STARTED")
                .enabled(0)
                .enquireLinkPeriod(30000)
                .enquireLinkTimeout(0)
                .requestDLR(true)
                .protocol("SMPP")
                .build();
        this.sessionStateListener = new SessionStateListenerImpl(gateway, socketSession, jedisCluster, sessions);
    }

    @Test
    @DisplayName("On State Change When Bound Sessions Then Success")
    void onStateChangeWhenBoundSessionsThenSuccess() {
        // BOUND_TRX
        Session source = new SMPPSession();
        assertEquals(1, gateway.getNetworkId());

        assertEquals("STARTED", gateway.getStatus());
        sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.OUTBOUND, source);
        assertEquals(1, gateway.getSuccessSession());
        assertEquals("BOUND", gateway.getStatus());
        verify(jedisCluster).hset("gateways", String.valueOf(gateway.getNetworkId()), gateway.toString());

        sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.OUTBOUND, source);
        assertEquals(2, gateway.getSuccessSession());
        assertEquals("BOUND", gateway.getStatus());
        verify(jedisCluster).hset("gateways", String.valueOf(gateway.getNetworkId()), gateway.toString());

        sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.OUTBOUND, source);
        assertEquals(3, gateway.getSuccessSession());
        assertEquals("BOUND", gateway.getStatus());
        verify(jedisCluster).hset("gateways", String.valueOf(gateway.getNetworkId()), gateway.toString());

        // BOUND_RX
        sessionStateListener.onStateChange(SessionState.BOUND_RX, SessionState.OUTBOUND, source);
        assertEquals(4, gateway.getSuccessSession());
        assertEquals("BOUND", gateway.getStatus());
        verify(jedisCluster).hset("gateways", String.valueOf(gateway.getNetworkId()), gateway.toString());

        // BOUND_TX
        sessionStateListener.onStateChange(SessionState.BOUND_TX, SessionState.OUTBOUND, source);
        assertEquals(5, gateway.getSuccessSession());
        assertEquals("BOUND", gateway.getStatus());
        verify(jedisCluster).hset("gateways", String.valueOf(gateway.getNetworkId()), gateway.toString());
    }

    @Test
    @DisplayName("On State Change When Closed Sessions Then Success")
    void onStateChangeWhenNotBoundSessionsThenSuccess() {
        Session source = new SMPPSession();
        assertEquals(1, gateway.getNetworkId());

        assertEquals("STARTED", gateway.getStatus());
        sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.OUTBOUND, source);
        sessionStateListener.onStateChange(SessionState.BOUND_TRX, SessionState.OUTBOUND, source);
        assertEquals(2, gateway.getSuccessSession());
        assertEquals("BOUND", gateway.getStatus());

        sessionStateListener.onStateChange(SessionState.UNBOUND, SessionState.BOUND_TRX, source);
        sessionStateListener.onStateChange(SessionState.CLOSED, SessionState.UNBOUND, source);
        assertEquals(1, gateway.getSuccessSession());
        assertEquals("BOUND", gateway.getStatus());

        sessionStateListener.onStateChange(SessionState.UNBOUND, SessionState.BOUND_TRX, source);
        sessionStateListener.onStateChange(SessionState.CLOSED, SessionState.UNBOUND, source);
        assertEquals(0, gateway.getSuccessSession());
        assertEquals("UNBOUND", gateway.getStatus());

        verify(jedisCluster).hset("gateways", String.valueOf(gateway.getNetworkId()), gateway.toString());
        verify(socketSession).sendStatus(String.valueOf(gateway.getNetworkId()), PARAM_UPDATE_STATUS, "UNBOUND");
    }

    @Test
    @DisplayName("On State Change When Stopped And Success Session Is Zero Then Success")
    void onStateChangeWhenStoppedAndSuccessSessionIsZeroThenSuccess() {
        Session source = new SMPPSession();
        assertEquals(1, gateway.getNetworkId());

        assertEquals("STARTED", gateway.getStatus());
        sessionStateListener.onStateChange(SessionState.CLOSED, SessionState.BOUND_TRX, source);
        assertEquals(0, gateway.getSuccessSession());
        assertEquals("STARTED", gateway.getStatus());
        verify(jedisCluster, never()).hset("gateways", String.valueOf(gateway.getNetworkId()), gateway.toString());
        verify(socketSession, never()).sendStatus(String.valueOf(gateway.getNetworkId()), PARAM_UPDATE_STATUS, "STARTED");
    }
}