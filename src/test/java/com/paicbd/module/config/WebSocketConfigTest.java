package com.paicbd.module.config;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.ws.SocketClient;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private SocketSession socketSession;

    @Mock
    private CustomFrameHandler customFrameHandler;

    @InjectMocks
    private WebSocketConfig webSocketConfig;

    @BeforeEach
    public void setUp() {
        when(appProperties.isWsEnabled()).thenReturn(true);
        when(appProperties.getWsHost()).thenReturn("localhost");
        when(appProperties.getWsPort()).thenReturn(8080);
        when(appProperties.getWsPath()).thenReturn("/ws");
        when(appProperties.getWsHeaderName()).thenReturn("Authorization");
        when(appProperties.getWsHeaderValue()).thenReturn("Token");
        when(appProperties.getWsRetryInterval()).thenReturn(10);
    }

    @Test
    void socketClient() {
        SocketClient socketClient = webSocketConfig.socketClient();
        assertNotNull(socketClient, "SocketClient should not be null");
    }
}