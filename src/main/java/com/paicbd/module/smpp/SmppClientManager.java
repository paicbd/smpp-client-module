package com.paicbd.module.smpp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.ws.SocketSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.STOPPED;

/**
 * @author <a href="mailto:enmanuelcalero61@gmail.com"> Enmanuel Calero </a>
 * @author <a href="mailto:ndiazobed@gmail.com"> Obed Navarrete </a>
 */
@Slf4j
@RequiredArgsConstructor
@Component("smppClientManager")
public class SmppClientManager {
    private final CdrProcessor cdrProcessor;
    private final JedisCluster jedisCluster;
    private final AppProperties appProperties;
    private final SocketSession socketSession;
    private final ConcurrentMap<Integer, SmppConnectionManager> smppConnectionManagerList;
    private final ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    private final ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap;

    @PostConstruct
    public void startManager() {
        loadSmppConnectionManager();
        loadErrorCodeMapping();
        loadRoutingRules();
    }

    public void updateGateway(String stringNetworkId) {
        String gatewayInRaw = jedisCluster.hget(appProperties.getKeyGatewayRedis(), stringNetworkId);
        if (gatewayInRaw == null) {
            log.debug("No gateways found for connect on updateGateway");
            return;
        }
        int networkId = Integer.parseInt(stringNetworkId);
        Gateway gateway = castGateway(gatewayInRaw);
        if (!"smpp".equalsIgnoreCase(gateway.getProtocol())) {
            log.warn("The gateway with networkId {} is not handled by this application. Failed to update", networkId);
            return;
        }

        if (smppConnectionManagerList.containsKey(networkId)) {
            SmppConnectionManager smppConnectionManager = smppConnectionManagerList.get(networkId);
            smppConnectionManager.updateGatewayInDeep(gateway);
        } else {
            SmppConnectionManager smppConnectionManager = new SmppConnectionManager(
                    jedisCluster, gateway, socketSession,
                    errorCodeMappingConcurrentHashMap,
                    routingRulesConcurrentHashMap,
                    appProperties,
                    cdrProcessor
            );
            smppConnectionManager.startMessagesProcessor();
            smppConnectionManagerList.put(gateway.getNetworkId(), smppConnectionManager);
        }
    }

    public void connectGateway(String stringNetworkId) {
        try {
            SmppConnectionManager smppConnectionManager = smppConnectionManagerList.get(Integer.parseInt(stringNetworkId));
            if (Objects.isNull(smppConnectionManager)) { // Probably is an HTTP gateway trying to connect, this is not handled by this application
                log.warn("This gateway is not present in the application, probably is an HTTP gateway");
                return;
            }

            smppConnectionManager.getGateway().setEnabled(1);
            smppConnectionManager.getGateway().setStatus("STARTED");
            smppConnectionManager.connect();
        } catch (Exception e) {
            log.error("Error on connect on gateway {} with error {}", stringNetworkId, e.getMessage(), e);
        }
    }

    public void stopGateway(String stringNetworkId) {
        log.info("Stopping gateway with networkId {}", stringNetworkId);
        SmppConnectionManager smppConnectionManager = smppConnectionManagerList.get(Integer.parseInt(stringNetworkId));
        if (Objects.isNull(smppConnectionManager)) {
            log.warn("The gateway {} is not handled by this application", stringNetworkId);
            return;
        }

        smppConnectionManager.stopConnection();
        socketSession.sendStatus(stringNetworkId, PARAM_UPDATE_STATUS, STOPPED);
    }

    private Gateway castGateway(String gatewayInRaw) {
        Gateway gateway = Converter.stringToObject(gatewayInRaw, Gateway.class);
        Objects.requireNonNull(gateway, "An error occurred while casting the gateway");
        if (gateway.getPduProcessorDegree() <= 0)
            gateway.setPduProcessorDegree(appProperties.getSmppProcessorDegree());
        if (gateway.getThreadPoolSize() <= 0)
            gateway.setThreadPoolSize(appProperties.getSmppQueueCapacity());
        if (gateway.getBindRetryPeriod() <= 0)
            gateway.setBindRetryPeriod((int) (appProperties.getTimeRetry() / 1000));
        if (gateway.getAddressRange() == null || gateway.getAddressRange().isEmpty()) {
            gateway.setAddressRange(null);
        }
        return gateway;
    }

    private void loadSmppConnectionManager() {
        Map<String, String> gatewaysKey = jedisCluster.hgetAll(appProperties.getKeyGatewayRedis());
        if (!gatewaysKey.isEmpty()) {
            gatewaysKey.forEach((stringNetworkId, gatewayInRaw) -> {
                Gateway gateway = castGateway(gatewayInRaw);
                if ("SMPP".equalsIgnoreCase(gateway.getProtocol())) {
                    SmppConnectionManager smppConnectionManager = new SmppConnectionManager(
                            jedisCluster, gateway, socketSession, errorCodeMappingConcurrentHashMap,
                            routingRulesConcurrentHashMap, appProperties, cdrProcessor);
                    smppConnectionManager.startMessagesProcessor();
                    smppConnectionManagerList.put(gateway.getNetworkId(), smppConnectionManager);
                }
            });
            log.warn("{} Gateways load successfully", smppConnectionManagerList.values().size());
        } else {
            log.warn("No gateways found for connect");
        }
    }

    private void loadErrorCodeMapping() {
        Map<String, String> errorCodeKeys = jedisCluster.hgetAll(appProperties.getKeyErrorCodeMapping());
        if (!errorCodeKeys.isEmpty()) {
            errorCodeKeys.forEach((stringMnoId, errorCodeMappingListInRaw) -> {
                List<ErrorCodeMapping> errorCodeMappingList = Converter.stringToObject(errorCodeMappingListInRaw, new TypeReference<>() {
                });
                errorCodeMappingConcurrentHashMap.put(stringMnoId, errorCodeMappingList);
            });
            log.warn("{} Error code mapping load successfully", errorCodeMappingConcurrentHashMap.values().size());
        } else {
            log.warn("No Error code mapping found");
        }
    }

    public void updateErrorCodeMapping(String mnoId) {
        String errorCodeMappingInRaw = jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), mnoId);
        if (errorCodeMappingInRaw == null) {
            errorCodeMappingConcurrentHashMap.remove(mnoId); // Remove if existed, if not exist do anything
            return;
        }

        List<ErrorCodeMapping> errorCodeMappingList = Converter.stringToObject(errorCodeMappingInRaw, new TypeReference<>() {
        });
        errorCodeMappingConcurrentHashMap.put(mnoId, errorCodeMappingList); // Put do it the replacement if exist
    }

    public void deleteGateway(String stringNetworkId) {
        log.warn("Deleting gateway {}", stringNetworkId);
        SmppConnectionManager smppConnectionManager = smppConnectionManagerList.get(Integer.parseInt(stringNetworkId));
        if (Objects.isNull(smppConnectionManager)) {
            log.warn("The gateway {} is not handled by this application. Failed to delete", stringNetworkId);
            return;
        }
        smppConnectionManager.stopConnection();
        smppConnectionManagerList.remove(Integer.parseInt(stringNetworkId));
    }

    private void loadRoutingRules() {
        var redisRoutingRules = jedisCluster.hgetAll(appProperties.getRoutingRulesHash());
        redisRoutingRules.entrySet().parallelStream().forEach(entry -> {
            String data = String.valueOf(entry.getValue());
            data = data.replace("\\", "\\\\");
            List<RoutingRule> routingList = Converter.stringToObject(data, new TypeReference<>() {
            });
            routingRulesConcurrentHashMap.put(Integer.parseInt(entry.getKey()), new ArrayList<>());
            routingList.forEach(r -> routingRulesConcurrentHashMap.get(r.getOriginNetworkId()).add(r));
            log.info("Loaded routing rules for network id: {}, rules: {}", entry.getKey(), routingList.size());
        });
        log.info("Loaded routing rules: {}", routingRulesConcurrentHashMap.size());
    }

    public void updateRoutingRule(String networkId) {
        var routingRuleInRaw = jedisCluster.hget(appProperties.getRoutingRulesHash(), networkId);
        if (routingRuleInRaw == null) {
            log.info("No routing rule found for network id {}", networkId);
            return;
        }
        List<RoutingRule> routingMappingList = Converter.stringToObject(routingRuleInRaw, new TypeReference<>() {
        });
        routingRulesConcurrentHashMap.put(Integer.parseInt(networkId), routingMappingList);
        log.info("Updated routing rules for network id {}: {}", networkId, routingMappingList.toArray());
    }

    public void deleteRoutingRule(String networkId) {
        routingRulesConcurrentHashMap.remove(Integer.parseInt(networkId));
    }

    @PreDestroy
    public void stopAllConnections() {
        log.warn("Stopping all connections");
        smppConnectionManagerList.values().parallelStream().forEach(connection -> {
            connection.stopConnection();
            log.warn("Stopped gateway {}", connection.getGateway().toString());
        });
    }
}
