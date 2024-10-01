package com.paicbd.module.smpp;

import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.ws.SocketSession;
import org.jsmpp.extra.SessionState;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class SessionStateListenerImplTest {
    @Mock
    SocketSession socketSession;

    @Mock
    Gateway gateway;

    @Mock
    List<SMPPSession> sessions = new ArrayList<>();

    @Mock
    JedisCluster jedisCluster;

    @InjectMocks
    SessionStateListenerImpl sessionStateListener;

    @BeforeEach
    void setUp() {
        this.gateway = new Gateway();
        gateway.setNetworkId(1);
        gateway.setName("smppgw");
        gateway.setSystemId("systemId");
        gateway.setPassword("password");
        gateway.setIp("192.168.100.20");
        gateway.setPort(7001);
        gateway.setBindType("TRANSCEIVER");
        gateway.setSystemType("");
        gateway.setInterfaceVersion("IF_50");
        gateway.setSessionsNumber(10);
        gateway.setAddressTON(0);
        gateway.setAddressNPI(0);
        gateway.setAddressRange(null);
        gateway.setTps(10);
        gateway.setSuccessSession(0);
        gateway.setStatus("STARTED");
        gateway.setEnabled(0);
        gateway.setEnquireLinkPeriod(30000);
        gateway.setEnquireLinkTimeout(0);
        gateway.setRequestDLR(true);
        gateway.setNoRetryErrorCode("");
        gateway.setRetryAlternateDestinationErrorCode("");
        gateway.setBindTimeout(5000);
        gateway.setBindRetryPeriod(10000);
        gateway.setPduTimeout(5000);
        gateway.setPduProcessorDegree(1);
        gateway.setThreadPoolSize(100);
        gateway.setMno(1);
        gateway.setTlvMessageReceiptId(false);
        gateway.setMessageIdDecimalFormat(false);
        gateway.setProtocol("SMPP");
        gateway.setAutoRetryErrorCode("");
        gateway.setEncodingIso88591(3);
        gateway.setEncodingGsm7(0);
        gateway.setEncodingUcs2(2);
        gateway.setSplitMessage(false);
        gateway.setSplitSmppType("TLV");

        socketSession = new SocketSession("gw");
        this.sessionStateListener = new SessionStateListenerImpl(gateway, socketSession, jedisCluster, sessions);
    }

    @Test
    void onStateChange() {
        // BOUND_TRX
        SessionState newState = SessionState.BOUND_TRX;
        SessionState oldState = SessionState.OUTBOUND;
        Session source = new SMPPSession();
        assertEquals("systemId", gateway.getSystemId());
        assertDoesNotThrow(() -> sessionStateListener.onStateChange(newState, oldState, source));
        assertEquals(1, gateway.getSuccessSession());

        // CLOSED
        SessionState newState2 = SessionState.CLOSED;
        SessionState oldState2 = SessionState.BOUND_TRX;
        assertDoesNotThrow(() -> sessionStateListener.onStateChange(newState2, oldState2, source));
        assertEquals(0, gateway.getSuccessSession());

        // SuccessSession == 0
        gateway.setSuccessSession(0);
        assertDoesNotThrow(() -> sessionStateListener.onStateChange(newState2, oldState2, source));
        assertEquals(0, gateway.getSuccessSession());
    }
}