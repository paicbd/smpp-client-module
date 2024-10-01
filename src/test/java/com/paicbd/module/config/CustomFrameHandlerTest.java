package com.paicbd.module.config;

import com.paicbd.module.smpp.SmppClientManager;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import static com.paicbd.module.utils.Constants.CONNECT_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.DELETE_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.DELETE_ROUTING_RULE_ENDPOINT;
import static com.paicbd.module.utils.Constants.STOP_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_ERROR_CODE_MAPPING_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_ROUTING_RULE_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFrameHandlerTest {

    @Mock(strictness = Mock.Strictness.LENIENT) // In handleFrameLogic_invalidDestination test case, IllegalArgumentException is thrown by the method
    private SocketSession socketSession;

    @Mock
    private SmppClientManager smppClientManager;

    @Mock
    private StompHeaders stompHeaders;

    @Mock
    private StompSession stompSession;

    @InjectMocks
    CustomFrameHandler customFrameHandler;

    @BeforeEach
    public void setUp() {
        when(socketSession.getStompSession()).thenReturn(stompSession);
    }

    @Test
    void handleFrameLogic_updateGateway() {
        String payload = "systemId123";
        when(stompHeaders.getDestination()).thenReturn(UPDATE_GATEWAY_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_connectGateway() {
        String payload = "systemId123";
        when(stompHeaders.getDestination()).thenReturn(CONNECT_GATEWAY_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_stopGateway() {
        String payload = "systemId123";
        when(stompHeaders.getDestination()).thenReturn(STOP_GATEWAY_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_deleteGateway() {
        String payload = "systemId123";
        when(stompHeaders.getDestination()).thenReturn(DELETE_GATEWAY_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_updateErrorCodeMapping() {
        String payload = "mnoId123";
        when(stompHeaders.getDestination()).thenReturn(UPDATE_ERROR_CODE_MAPPING_ENDPOINT);
        assertDoesNotThrow(() ->customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_updateRoutingRule() {
        String payload = "networkId123";
        when(stompHeaders.getDestination()).thenReturn(UPDATE_ROUTING_RULE_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_deleteRoutingRule() {
        String payload = "networkId123";
        when(stompHeaders.getDestination()).thenReturn(DELETE_ROUTING_RULE_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }

    @Test
    void handleFrameLogic_invalidDestination() {
        String payload = "networkId123";
        when(stompHeaders.getDestination()).thenReturn("INVALID_DESTINATION");
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(stompHeaders, payload));
    }
}