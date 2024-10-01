package com.paicbd.module.utils;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Component
public class AppProperties {
    @Value("#{'${redis.cluster.nodes}'.split(',')}")
    private List<String> redisNodes;

    @Value("${redis.threadPool.maxTotal}")
    private int redisMaxTotal = 20;

    @Value("${redis.threadPool.maxIdle}")
    private int redisMaxIdle = 20;

    @Value("${redis.threadPool.minIdle}")
    private int redisMinIdle = 1;

    @Value("${redis.threadPool.blockWhenExhausted}")
    private boolean redisBlockWhenExhausted = true;

    @Value("${smpp.workersPerGateway}")
    private int workersPerGateway;

    @Value("${smpp.workForWorker}")
    private int workForWorker;

    @Value("${smpp.gatewaysWork.executeEvery}")
    private int gatewaysWorkExecuteEvery;

    @Value("${redis.retry.messages.queue}")
    private String retryMessage;

    @Value("${redis.preDeliver.queue}")
    private String preDeliverQueue = "preDeliver";

    @Value("${redis.preMessage.queue}")
    private String preMessageQueue = "preMessage";

    @Value("${redis.submitSmResult.queue}")
    private String submitSmResultQueue = "submit_sm_result";

    @Value("${websocket.server.host}")
    private String wsHost;

    @Value("${websocket.server.port}")
    private int wsPort;

    @Value("${websocket.server.path}")
    private String wsPath;

    @Value("${websocket.server.enabled}")
    private boolean wsEnabled;

    @Value("${websocket.header.name}")
    private String wsHeaderName;

    @Value("${websocket.header.value}")
    private String wsHeaderValue;

    @Value("${websocket.retry.intervalSeconds}")
    private int wsRetryInterval;

    @Value("${smpp.key.gateways}")
    private String keyGatewayRedis;

    @Value("${smpp.key.errorCodeMapping}")
    private String keyErrorCodeMapping;

    @Value("${smpp.key.routing.rules}")
    private String routingRulesHash;

    @Value("${smpp.connection.processorDegree}")
    private int smppProcessorDegree;

    @Value("${smpp.connection.queueCapacity}")
    private int smppQueueCapacity;

    @Value("${smpp.connection.transactionTimer}")
    private long transactionTimer;

    @Value("${smpp.connection.timeRetry}")
    private long timeRetry;
}
