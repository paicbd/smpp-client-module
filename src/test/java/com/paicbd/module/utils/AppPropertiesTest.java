package com.paicbd.module.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class AppPropertiesTest {

    @InjectMocks
    private AppProperties appProperties;

    @BeforeEach
    void setUp() throws Exception {
        injectField("redisNodes", Arrays.asList("node1", "node2", "node3"));
        injectField("redisMaxTotal", 20);
        injectField("redisMaxIdle", 20);
        injectField("redisMinIdle", 1);
        injectField("redisBlockWhenExhausted", true);
        injectField("workersPerGateway", 10);
        injectField("workForWorker", 5);
        injectField("gatewaysWorkExecuteEvery", 30);
        injectField("retryMessage", "retry_message");
        injectField("preDeliverQueue", "preDeliver");
        injectField("preMessageQueue", "preMessage");
        injectField("submitSmResultQueue", "submit_sm_result");
        injectField("wsHost", "localhost");
        injectField("wsPort", 8080);
        injectField("wsPath", "/ws");
        injectField("wsEnabled", true);
        injectField("wsHeaderName", "header_name");
        injectField("wsHeaderValue", "header_value");
        injectField("wsRetryInterval", 10);
        injectField("keyGatewayRedis", "key_gateway_redis");
        injectField("keyErrorCodeMapping", "key_error_code_mapping");
        injectField("routingRulesHash", "routing_rules_hash");
        injectField("smppProcessorDegree", 4);
        injectField("smppQueueCapacity", 1000);
        injectField("transactionTimer", 30000L);
        injectField("timeRetry", 5000L);
    }

    private void injectField(String fieldName, Object value) throws Exception {
        Field field = AppProperties.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(appProperties, value);
    }

    @Test
    void testProperties_1() {
        List<String> expectedRedisNodes = Arrays.asList("node1", "node2", "node3");
        assertEquals(expectedRedisNodes, appProperties.getRedisNodes());
        assertEquals(20, appProperties.getRedisMaxTotal());
        assertEquals(20, appProperties.getRedisMaxIdle());
        assertEquals(1, appProperties.getRedisMinIdle());
        assertTrue(appProperties.isRedisBlockWhenExhausted());
        assertEquals(10, appProperties.getWorkersPerGateway());
        assertEquals(5, appProperties.getWorkForWorker());
        assertEquals(30, appProperties.getGatewaysWorkExecuteEvery());
        assertEquals("retry_message", appProperties.getRetryMessage());
        assertEquals("preDeliver", appProperties.getPreDeliverQueue());
        assertEquals("preMessage", appProperties.getPreMessageQueue());
    }

    @Test
    void testProperties_2() {
        assertEquals("submit_sm_result", appProperties.getSubmitSmResultQueue());
        assertEquals("localhost", appProperties.getWsHost());
        assertEquals(8080, appProperties.getWsPort());
        assertEquals("/ws", appProperties.getWsPath());
        assertTrue(appProperties.isWsEnabled());
        assertEquals("header_name", appProperties.getWsHeaderName());
        assertEquals("header_value", appProperties.getWsHeaderValue());
        assertEquals(10, appProperties.getWsRetryInterval());
        assertEquals("key_gateway_redis", appProperties.getKeyGatewayRedis());
        assertEquals("key_error_code_mapping", appProperties.getKeyErrorCodeMapping());
        assertEquals("routing_rules_hash", appProperties.getRoutingRulesHash());
        assertEquals(4, appProperties.getSmppProcessorDegree());
        assertEquals(1000, appProperties.getSmppQueueCapacity());
        assertEquals(30000L, appProperties.getTransactionTimer());
        assertEquals(5000L, appProperties.getTimeRetry());
    }
}