package com.paicbd.module.config;

import com.paicbd.module.smpp.SmppClientManager;
import com.paicbd.smsc.ws.FrameHandler;
import com.paicbd.smsc.ws.SocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.paicbd.module.utils.Constants.CONNECT_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.DELETE_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.DELETE_ROUTING_RULE_ENDPOINT;
import static com.paicbd.module.utils.Constants.RESPONSE_SMPP_CLIENT_ENDPOINT;
import static com.paicbd.module.utils.Constants.STOP_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_ERROR_CODE_MAPPING_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_GATEWAY_ENDPOINT;
import static com.paicbd.module.utils.Constants.UPDATE_ROUTING_RULE_ENDPOINT;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFrameHandler implements FrameHandler {
    private final SocketSession socketSession;
    private final SmppClientManager smppClientManager;

    @Override
    public void handleFrameLogic(StompHeaders headers, Object payload) {
        String systemId = payload.toString();
        String destination = headers.getDestination();
        Objects.requireNonNull(systemId, "System ID cannot be null");
        Objects.requireNonNull(destination, "Destination cannot be null");

        switch (destination) {
            case UPDATE_GATEWAY_ENDPOINT -> handleUpdateGateway(systemId);
            case CONNECT_GATEWAY_ENDPOINT -> handleConnectGateway(systemId);
            case STOP_GATEWAY_ENDPOINT -> handleStopGateway(systemId);
            case DELETE_GATEWAY_ENDPOINT -> handleDeleteGateway(systemId);
            case UPDATE_ERROR_CODE_MAPPING_ENDPOINT -> handleUpdateErrorCodeMapping(systemId);
            case UPDATE_ROUTING_RULE_ENDPOINT -> handleUpdateRoutingRule(systemId);
            case DELETE_ROUTING_RULE_ENDPOINT -> handleDeleteRoutingRule(systemId);
            default -> log.warn("Unknown destination: {}", destination);
        }
    }

    private void handleUpdateGateway(String systemId) {
        log.info("Updating gateway {}", systemId);
        this.smppClientManager.updateGateway(systemId);
        this.sendResponse("Notified the gateway to update");
    }

    private void handleConnectGateway(String systemId) {
        log.info("Connecting gateway {}", systemId);
        this.smppClientManager.connectGateway(systemId);
        this.sendResponse("Notified the gateway to connect");
    }

    private void handleStopGateway(String systemId) {
        log.info("Stopping gateway {}", systemId);
        this.smppClientManager.stopGateway(systemId);
        this.sendResponse("Notified the gateway to stop");
    }

    private void handleDeleteGateway(String systemId) {
        log.info("Deleting gateway {}", systemId);
        this.smppClientManager.deleteGateway(systemId);
        this.sendResponse("Notified the gateway to delete");
    }

    private void handleUpdateErrorCodeMapping(String mnoId) {
        log.info("Updating error code mapping for mno_id {}", mnoId);
        this.smppClientManager.updateErrorCodeMapping(mnoId);
        this.sendResponse("Notified the gateway to update error code mapping");
    }

    private void handleUpdateRoutingRule(String networkId) {
        log.info("Updating routing rule with network id {}", networkId);
        this.smppClientManager.updateRoutingRule(networkId);
        this.sendResponse("Notified the gateway to update routing rule");
    }

    private void handleDeleteRoutingRule(String networkId) {
        log.info("Socket Client is deleting routing rule with network id {}", networkId);
        this.smppClientManager.deleteRoutingRule(networkId);
        this.sendResponse("Notified the gateway to delete routing rule");
    }

    private void sendResponse(String message) {
        this.socketSession.getStompSession().send(RESPONSE_SMPP_CLIENT_ENDPOINT, message);
    }
}
