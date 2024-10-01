package com.paicbd.module.config;

import com.paicbd.module.smpp.SessionStateListenerImpl;
import com.paicbd.module.smpp.SmppConnectionManager;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeansDefinitionTest {
    @Mock
    AppProperties appProperties;

    @Mock
    JedisCluster jedisCluster;

    @InjectMocks
    BeansDefinition beansDefinition;

    @Test
    void testSmppConnectionManagerListCreation() {
        ConcurrentMap<String, SmppConnectionManager> connectionManagers = beansDefinition.smppConnectionManagerList();
        assertNotNull(connectionManagers);
        assertTrue(connectionManagers.isEmpty());
    }

    @Test
    void testErrorCodeMappingConcurrentHashMapCreation() {
        ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap = beansDefinition.errorCodeMappingConcurrentHashMap();
        assertNotNull(errorCodeMappingConcurrentHashMap);
        assertTrue(errorCodeMappingConcurrentHashMap.isEmpty());
    }

    @Test
    void testRoutingRulesConcurrentHashMapCreation() {
        ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap = beansDefinition.routingRulesConcurrentHashMap();
        assertNotNull(routingRulesConcurrentHashMap);
        assertTrue(routingRulesConcurrentHashMap.isEmpty());
    }

    @Test
    void testJedisClusterCreation() {
        when(appProperties.getRedisNodes()).thenReturn(List.of("localhost:6379", "localhost:6380"));
        when(appProperties.getRedisMaxTotal()).thenReturn(10);
        when(appProperties.getRedisMinIdle()).thenReturn(1);
        when(appProperties.getRedisMaxIdle()).thenReturn(5);
        when(appProperties.isRedisBlockWhenExhausted()).thenReturn(true);
        assertNull(beansDefinition.jedisCluster());
    }

    @Test
    void testSessionStateListenerByGateway() {
        ConcurrentMap<String, SessionStateListenerImpl> connectionManagers = beansDefinition.sessionStateListenerByGateway();
        assertNotNull(connectionManagers);
        assertTrue(connectionManagers.isEmpty());
    }

    @Test
    void testSocketSessionCreation() {
        SocketSession socketSession = beansDefinition.socketSession();
        assertNotNull(socketSession);
        assertEquals("gw", socketSession.getType());
    }

    @Test
    void cdrProcessor() {
        assertNotNull(beansDefinition.cdrProcessor(jedisCluster));
    }
}