package com.paicbd.module.config;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.ws.SocketClient;
import com.paicbd.smsc.ws.SocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static com.paicbd.module.utils.Constants.CONNECT_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.DELETE_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.DELETE_ROUTING_RULE_ENDPOINT;
import static com.paicbd.module.utils.Constants.STOP_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_ERROR_CODE_MAPPING_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_ROUTING_RULE_ENDPOINT;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebSocketConfig {
    private final AppProperties appProperties;
    private final SocketSession socketSession;
    private final CustomFrameHandler customFrameHandler;

    @Bean
    public SocketClient socketClient() {
        List<String> topicsToSubscribe = List.of(
                UPDATE_GATEWAY_ENDPOINT,
                CONNECT_GATEWAY_ENDPOINT,
                STOP_GATEWAY_ENDPOINT,
                DELETE_GATEWAY_ENDPOINT,
                UPDATE_ERROR_CODE_MAPPING_ENDPOINT,
                UPDATE_ROUTING_RULE_ENDPOINT,
                DELETE_ROUTING_RULE_ENDPOINT
        );
        UtilsRecords.WebSocketConnectionParams wsp = new UtilsRecords.WebSocketConnectionParams(
                appProperties.isWsEnabled(),
                appProperties.getWsHost(),
                appProperties.getWsPort(),
                appProperties.getWsPath(),
                topicsToSubscribe,
                appProperties.getWsHeaderName(),
                appProperties.getWsHeaderValue(),
                appProperties.getWsRetryInterval(),
                "SMPP-CLIENT" // Current SMSC Module
        );
        return new SocketClient(customFrameHandler, wsp, socketSession);
    }
}
