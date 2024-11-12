package com.paicbd.module.component;

import com.paicbd.module.smpp.SmppClientManager;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.DisplayName;
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
import static com.paicbd.module.utils.Constants.RESPONSE_SMPP_CLIENT_ENDPOINT;
import static com.paicbd.module.utils.Constants.STOP_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_ERROR_CODE_MAPPING_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_ROUTING_RULE_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomFrameHandlerTest {
    @Mock
    private SocketSession socketSession;

    @Mock
    private SmppClientManager smppClientManager;

    @Mock
    private StompSession stompSession;

    @InjectMocks
    CustomFrameHandler customFrameHandler;

    @Test
    @DisplayName("HandleFrameLogic when updateGateway then do it successfully")
    void handleFrameLogicWhenUpdateGatewayThenDoItSuccessfully() {
        when(socketSession.getStompSession()).thenReturn(stompSession);

        StompHeaders headers = new StompHeaders();
        headers.setDestination(UPDATE_GATEWAY_ENDPOINT);
        String stringNetworkId = "1";

        customFrameHandler.handleFrameLogic(headers, stringNetworkId);

        verify(smppClientManager).updateGateway(stringNetworkId);
        verify(stompSession).send(RESPONSE_SMPP_CLIENT_ENDPOINT, "Notified the gateway to update");
    }

    @Test
    @DisplayName("HandleFrameLogic when connectGateway then do it successfully")
    void handleFrameLogicWhenConnectGatewayThenDoItSuccessfully() {
        when(socketSession.getStompSession()).thenReturn(stompSession);

        StompHeaders headers = new StompHeaders();
        headers.setDestination(CONNECT_GATEWAY_ENDPOINT);
        String stringNetworkId = "1";

        customFrameHandler.handleFrameLogic(headers, stringNetworkId);

        verify(smppClientManager).connectGateway(stringNetworkId);
        verify(stompSession).send(RESPONSE_SMPP_CLIENT_ENDPOINT, "Notified the gateway to connect");
    }

    @Test
    @DisplayName("HandleFrameLogic when unknown destination then truncate the process")
    void handleFrameLogicWhenUnknownDestinationThenTruncateTheProcess() {
        StompHeaders headers = new StompHeaders();
        headers.setDestination("UNKNOWN_ENDPOINT");
        String systemId = "1";

        customFrameHandler.handleFrameLogic(headers, systemId);

        verify(smppClientManager, never()).connectGateway(anyString());
        verify(smppClientManager, never()).updateGateway(anyString());
        verify(smppClientManager, never()).stopGateway(anyString());
        verify(stompSession, never()).send(anyString(), anyString());
    }

    @Test
    @DisplayName("HandleFrameLogic when stopGateway then do it successfully")
    void handleFrameLogicWhenStopGatewayThenDoItSuccessfully() {
        when(socketSession.getStompSession()).thenReturn(stompSession);

        StompHeaders headers = new StompHeaders();
        headers.setDestination(STOP_GATEWAY_ENDPOINT);
        String stringNetworkId = "1";

        customFrameHandler.handleFrameLogic(headers, stringNetworkId);

        verify(smppClientManager).stopGateway(stringNetworkId);
        verify(stompSession).send(RESPONSE_SMPP_CLIENT_ENDPOINT, "Notified the gateway to stop");
    }

    @Test
    @DisplayName("HandleFrameLogic when deleteGateway then do it successfully")
    void handleFrameLogicWhenDeleteGatewayThenDoItSuccessfully() {
        when(socketSession.getStompSession()).thenReturn(stompSession);

        StompHeaders headers = new StompHeaders();
        headers.setDestination(DELETE_GATEWAY_ENDPOINT);
        String stringNetworkId = "1";

        customFrameHandler.handleFrameLogic(headers, stringNetworkId);

        verify(smppClientManager).deleteGateway(stringNetworkId);
        verify(stompSession).send(RESPONSE_SMPP_CLIENT_ENDPOINT, "Notified the gateway to delete");
    }

    @Test
    @DisplayName("HandleFrameLogic when updateErrorCodeMapping then do it successfully")
    void handleFrameLogicWhenUpdateErrorCodeMappingThenDoItSuccessfully() {
        when(socketSession.getStompSession()).thenReturn(stompSession);

        StompHeaders headers = new StompHeaders();
        headers.setDestination(UPDATE_ERROR_CODE_MAPPING_ENDPOINT);
        String mnoId = "1";

        customFrameHandler.handleFrameLogic(headers, mnoId);

        verify(smppClientManager).updateErrorCodeMapping(mnoId);
        verify(stompSession).send(RESPONSE_SMPP_CLIENT_ENDPOINT, "Notified the gateway to update error code mapping");
    }

    @Test
    @DisplayName("HandleFrameLogic when updateRoutingRule then do it successfully")
    void handleFrameLogicWhenUpdateRoutingRuleThenDoItSuccessfully() {
        when(socketSession.getStompSession()).thenReturn(stompSession);

        StompHeaders headers = new StompHeaders();
        headers.setDestination(UPDATE_ROUTING_RULE_ENDPOINT);
        String networkId = "1";

        customFrameHandler.handleFrameLogic(headers, networkId);

        verify(smppClientManager).updateRoutingRule(networkId);
        verify(stompSession).send(RESPONSE_SMPP_CLIENT_ENDPOINT, "Notified the gateway to update routing rule");
    }

    @Test
    @DisplayName("HandleFrameLogic when deleteRoutingRule then do it successfully")
    void handleFrameLogicWhenDeleteRoutingRuleThenDoItSuccessfully() {
        when(socketSession.getStompSession()).thenReturn(stompSession);

        StompHeaders headers = new StompHeaders();
        headers.setDestination(DELETE_ROUTING_RULE_ENDPOINT);
        String networkId = "1";

        customFrameHandler.handleFrameLogic(headers, networkId);

        verify(smppClientManager).deleteRoutingRule(networkId);
        verify(stompSession).send(RESPONSE_SMPP_CLIENT_ENDPOINT, "Notified the gateway to delete routing rule");
    }

    @Test
    @DisplayName("HandleFrameLogic when key is not a number then throw IllegalArgumentException")
    void handleFrameLogicWhenKeyIsNotANumberThenThrowIllegalArgumentException() {
        StompHeaders mockStompHeaders = mock(StompHeaders.class);
        assertThrows(IllegalArgumentException.class,
                () -> customFrameHandler.handleFrameLogic(mockStompHeaders, "fakeSystemId"));
        verifyNoInteractions(socketSession);
        verifyNoInteractions(smppClientManager);
    }
}