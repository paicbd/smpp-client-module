package com.paicbd.module.smpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.ws.SocketSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.STOPPED;

/**
 * @author <a href="mailto:enmanuelcalero61@gmail.com"> Enmanuel Calero </a>
 * @author <a href="mailto:ndiazobed@gmail.com"> Obed Navarrete </a>
 */
@Setter
@Getter
@Slf4j
@RequiredArgsConstructor
@Component("smppClientManager")
public class SmppClientManager {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final CdrProcessor cdrProcessor;
    private final JedisCluster jedisCluster;
    private final AppProperties appProperties;
    private final SocketSession socketSession;
    private final ConcurrentMap<String, SmppConnectionManager> smppConnectionManagerList;
    private final ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    private final ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap;

    @PostConstruct
    public void startManager() {
        loadSmppConnectionManager();
        loadErrorCodeMapping();
        loadRoutingRules();
    }

    public void updateGateway(@NonNull String systemId) {
        String gatewayInRaw = jedisCluster.hget(appProperties.getKeyGatewayRedis(), systemId);
        if (gatewayInRaw == null) {
            log.warn("No gateways found for connect on updateGateway");
            return;
        }
        try {
            Gateway gateway = castGateway(gatewayInRaw);
            if (!"smpp".equalsIgnoreCase(gateway.getProtocol())) {
                log.warn("This gateway {} is not handled by this application. Failed to update", systemId);
                return;
            }

            if (smppConnectionManagerList.containsKey(systemId)) {
                SmppConnectionManager smppConnectionManager = smppConnectionManagerList.get(systemId);
                smppConnectionManager.setGateway(gateway);
            } else {
                SmppConnectionManager smppConnectionManager = new SmppConnectionManager(
                        jedisCluster, gateway, socketSession,
                        errorCodeMappingConcurrentHashMap,
                        routingRulesConcurrentHashMap,
                        appProperties,
                        cdrProcessor
                );
                smppConnectionManagerList.put(gateway.getSystemId(), smppConnectionManager);
            }
        } catch (JsonProcessingException ex) {
            log.error("Error on update the smpp connections {}. {}", systemId, ex.getMessage());
        }
    }

    public void connectGateway(@NonNull String systemId) {
        SmppConnectionManager smppConnectionManager = smppConnectionManagerList.get(systemId);
        if (Objects.isNull(smppConnectionManager)) { // Probably is an HTTP gateway trying to connect, this is not handled by this application
            log.warn("This gateway is not handled by this application, {}", systemId);
            return;
        }
        try {
            smppConnectionManager.getGateway().setEnabled(1);
            smppConnectionManager.getGateway().setStatus("STARTED");
            smppConnectionManager.connect();
        } catch (Exception e) {
            log.error("Error on connect on gateway {} with error {}", systemId, e.getMessage());
        }
    }

    public void stopGateway(@NonNull String systemId) {
        log.info("Stopping gateway {}", systemId);
        SmppConnectionManager smppConnectionManager = smppConnectionManagerList.get(systemId);
        if (Objects.isNull(smppConnectionManager)) {
            log.warn("The gateway {} is not handled by this application", systemId);
            return;
        }
        try {
            smppConnectionManager.stopConnection();
            socketSession.sendStatus(systemId, PARAM_UPDATE_STATUS, STOPPED);
        } catch (Exception e) {
            log.error("Error on stop on gateway {} with error {}", systemId, e.getMessage());
        }

    }

    private Gateway castGateway(String gatewayInRaw) throws JsonProcessingException {
        Gateway gateway = objectMapper.readValue(gatewayInRaw, Gateway.class);
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
        Set<String> gatewaysKey = jedisCluster.hkeys(appProperties.getKeyGatewayRedis());
        if (!gatewaysKey.isEmpty()) {
            gatewaysKey.forEach(key -> {
                try {
                    String gatewayInRaw = jedisCluster.hget(appProperties.getKeyGatewayRedis(), key);
                    Gateway gateway = castGateway(gatewayInRaw);
                    if ("SMPP".equalsIgnoreCase(gateway.getProtocol())) {
                        SmppConnectionManager smppConnectionManager = new SmppConnectionManager(
                                jedisCluster, gateway, socketSession, errorCodeMappingConcurrentHashMap,
                                routingRulesConcurrentHashMap, appProperties, cdrProcessor);

                        smppConnectionManagerList.put(gateway.getSystemId(), smppConnectionManager);
                    }
                } catch (JsonProcessingException ex) {
                    log.error("Error on load the smpp connections {}", ex.getMessage());
                }
            });
            log.warn("{} Gateways load successfully", smppConnectionManagerList.values().size());
        } else {
            log.warn("No gateways found for connect, class {}, method {}", this.getClass().getName(), "loadSmppConnectionManager");
        }
    }

    private void loadErrorCodeMapping() {
        Set<String> errorCodeKeys = jedisCluster.hkeys(appProperties.getKeyErrorCodeMapping());
        if (!errorCodeKeys.isEmpty()) {
            errorCodeKeys.forEach(key -> {
                try {
                    List<ErrorCodeMapping> errorCodeMappingList;
                    String errorCodeMappingInRaw = jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), key);
                    errorCodeMappingList = objectMapper.readValue(errorCodeMappingInRaw, new TypeReference<>() {
                    });

                    errorCodeMappingConcurrentHashMap.put(key, errorCodeMappingList);
                } catch (JsonProcessingException ex) {
                    log.error("Error on load the error code mapping on method loadErrorCodeMapping {}", ex.getMessage());
                }
            });
            log.warn("{} Error code mapping load successfully", errorCodeMappingConcurrentHashMap.values().size());
        } else {
            log.warn("No Error code mapping found");
        }
    }

    public void updateErrorCodeMapping(String mnoId) {
        try {
            String errorCodeMappingInRaw = jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), mnoId);
            if (errorCodeMappingInRaw == null) {
                errorCodeMappingConcurrentHashMap.remove(mnoId); // Remove if existed, if not exist do anything
                return;
            }
            List<ErrorCodeMapping> errorCodeMappingList = objectMapper.readValue(errorCodeMappingInRaw, new TypeReference<>() {
            });
            errorCodeMappingConcurrentHashMap.put(mnoId, errorCodeMappingList); // Put do it the replacement if exist
        } catch (JsonProcessingException ex) {
            log.error("Error on load the error code mapping on method updateErrorCodeMapping {}", ex.getMessage());
        }
    }

    public void deleteGateway(String systemId) {
        log.warn("Deleting gateway {}", systemId);
        SmppConnectionManager smppConnectionManager = smppConnectionManagerList.get(systemId);
        if (Objects.isNull(smppConnectionManager)) {
            log.warn("The gateway {} is not handled by this application. Failed to delete", systemId);
            return;
        }
        smppConnectionManager.stopConnection();
        smppConnectionManagerList.remove(systemId);
    }

    public void loadRoutingRules() {
        var redisRoutingRules = jedisCluster.hgetAll(appProperties.getRoutingRulesHash());
        redisRoutingRules.entrySet().parallelStream().forEach(entry -> {
            try {
                String data = String.valueOf(entry.getValue());
                data = data.replace("\\", "\\\\");
                List<RoutingRule> routingList = objectMapper.readValue(data, new TypeReference<>() {
                });
                routingRulesConcurrentHashMap.put(Integer.parseInt(entry.getKey()), new ArrayList<>());
                routingList.forEach(r -> routingRulesConcurrentHashMap.get(r.getOriginNetworkId()).add(r));
                log.info("Loaded routing rules for network id: {}, rules: {}", entry.getKey(), routingList.size());
            } catch (Exception e) {
                log.error("Error loading routing rule: {}", e.getMessage());
            }
        });
        log.info("Loaded routing rules: {}", routingRulesConcurrentHashMap.size());
    }

    public void updateRoutingRule(String networkId) {
        var routingRuleInRaw = jedisCluster.hget(appProperties.getRoutingRulesHash(), networkId);
        if (routingRuleInRaw == null) {
            log.info("No routing rule found for network id {}", networkId);
            return;
        }
        try {
            List<RoutingRule> routingMappingList = objectMapper.readValue(routingRuleInRaw, new TypeReference<>() {
            });
            routingRulesConcurrentHashMap.put(Integer.parseInt(networkId), routingMappingList);
            log.info("Updated routing rules for network id {}: {}", networkId, routingMappingList.toArray());
        } catch (JsonProcessingException ex) {
            log.error("Error on update the routing rule on method updateRoutingRule {}", ex.getMessage());
        }
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
