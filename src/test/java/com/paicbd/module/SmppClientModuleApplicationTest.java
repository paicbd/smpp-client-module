package com.paicbd.module;

import com.paicbd.module.e2e.SmppServerMock;
import com.paicbd.module.smpp.SmppConnectionManager;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.MessagePart;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.SubmitSmResponseEvent;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.ws.SocketSession;
import org.jsmpp.InvalidResponseException;
import org.jsmpp.PDUException;
import org.jsmpp.extra.NegativeResponseException;
import org.jsmpp.extra.ResponseTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmppClientModuleApplicationTest {

    @Mock
    JedisCluster jedisCluster;

    @Mock
    SocketSession socketSession;

    @Mock
    ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;

    @Mock
    ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap;

    @Mock
    AppProperties properties;

    @Mock
    CdrProcessor cdrProcessor;

    SmppConnectionManager smppConnectionManager;

    ExecutorService executorService = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    @DisplayName("Connect when SMPP server throws an exception")
    void connectWhenSMPPServerThrowsExceptionThenDoNothing() {
        int port = 7777;
        executorService.submit(new SmppServerMock(port, new Exception(), null));
        Gateway smppGateway = getSMPPGw(port);
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        smppConnectionManager.connect();
        assertTrue(smppConnectionManager.getSessions().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("exceptionSendMessageParameters")
    @DisplayName("Try to send message when server listener throws an exception")
    void startMessagesProcessorWhenServerListenerThrowsExceptionThenCheckSubmitSmValues(Exception exception, int port) {
        executorService.submit(new SmppServerMock(port, null, exception));
        Gateway smppGateway = getSMPPGw(port);
        smppGateway.setSessionsNumber(1);
        smppGateway.setNoRetryErrorCode("8");
        String key = smppGateway.getNetworkId() + "_smpp_message";
        MessageEvent messageEvent = getMessageEvent();
        when(properties.getGatewaysWorkExecuteEvery()).thenReturn(1000);
        when(properties.getWorkersPerGateway()).thenReturn(1);
        when(properties.getSubmitSmResultQueue()).thenReturn("submit_sm_result");
        when(jedisCluster.llen(key)).thenReturn(1L).thenAnswer(invocationOnMock -> 0L);
        when(jedisCluster.lpop(key, 1)).thenReturn(List.of(messageEvent.toString()));
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        smppConnectionManager.connect();
        smppConnectionManager.startMessagesProcessor();
        toSleep(5);
        ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(jedisCluster, atLeastOnce()).rpush(fieldCaptor.capture(), valueCaptor.capture());
        MessageEvent deliverSmEvent = Converter.stringToObject(valueCaptor.getValue(), MessageEvent.class);
        assertEquals("smpp_dlr", fieldCaptor.getValue());
        assertEquals(deliverSmEvent.getMessageId(), messageEvent.getMessageId());
        smppConnectionManager.stopConnection();
    }

    @ParameterizedTest
    @MethodSource("exceptionParameters")
    @DisplayName("Handle exceptions when server listener throws an exception")
    void handleExceptionWhen(Exception exception, int errorCode) throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        MessageEvent messageEvent = getMessageEvent();
        Gateway smppGateway = getSMPPGw(9999);
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        int errorResult = invokePrivateMethod(smppConnectionManager, new Class<?>[]{Exception.class, MessageEvent.class}, exception, messageEvent);
        assertEquals(errorCode, errorResult);
    }

    @Test
    @DisplayName("Send message when registered delivery is not zero")
    void startMessagesProcessorWhenRegisteredDeliveryIsNotZeroThenCheckSubmitSmValues() {
        int port = 7778;
        executorService.submit(new SmppServerMock(port, null, null));
        Gateway smppGateway = getSMPPGw(port);
        String key = smppGateway.getNetworkId() + "_smpp_message";
        MessageEvent messageEvent = getMessageEvent();
        when(properties.getGatewaysWorkExecuteEvery()).thenReturn(1000);
        when(properties.getWorkersPerGateway()).thenReturn(1);
        when(properties.getSubmitSmResultQueue()).thenReturn("submit_sm_result");
        when(jedisCluster.llen(key)).thenReturn(1L).thenAnswer(invocationOnMock -> 0L);
        when(jedisCluster.lpop(key, 1)).thenReturn(List.of(messageEvent.toString()));
        when(jedisCluster.hget(anyString(), anyString())).thenReturn(new SubmitSmResponseEvent().toString());
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        smppConnectionManager.connect();
        smppConnectionManager.startMessagesProcessor();
        toSleep(5);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(jedisCluster, atLeastOnce()).hset(keyCaptor.capture(), fieldCaptor.capture(), valueCaptor.capture());
        SubmitSmResponseEvent submitSmResponseEvent = Converter.stringToObject(valueCaptor.getValue(), SubmitSmResponseEvent.class);
        assertEquals(properties.getSubmitSmResultQueue(), keyCaptor.getValue());
        assertEquals(fieldCaptor.getValue(), submitSmResponseEvent.getHashId());
        assertEquals(submitSmResponseEvent.getSubmitSmServerId(), messageEvent.getMessageId());
        assertEquals(smppGateway.getSessionsNumber(), smppConnectionManager.getSessions().size());
        smppConnectionManager.stopConnection();
    }

    @Test
    @DisplayName("Try to send message when batch size is zero")
    void startMessagesProcessorWhenBatchSizeIsZeroThenDoNothing() {
        int port = 7779;
        executorService.submit(new SmppServerMock(port, null, null));
        Gateway smppGateway = getSMPPGw(port);
        smppGateway.setTps(0);
        String key = smppGateway.getNetworkId() + "_smpp_message";
        when(properties.getGatewaysWorkExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(key)).thenReturn(0L);
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        smppConnectionManager.connect();
        smppConnectionManager.startMessagesProcessor();
        toSleep(2);
        verify(properties, never()).getWorkersPerGateway();
        smppConnectionManager.stopConnection();
    }

    @Test
    @DisplayName("Try to send message when batch per worker is zero")
    void startMessagesProcessorWhenBatchPerWorkerIsZeroThenDoNothing() {
        int port = 7780;
        executorService.submit(new SmppServerMock(port, null, null));
        Gateway smppGateway = getSMPPGw(port);
        smppGateway.setTps(0);
        String key = smppGateway.getNetworkId() + "_smpp_message";
        when(properties.getGatewaysWorkExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(key)).thenReturn(1L).thenAnswer(invocationOnMock -> 0L);
        when(properties.getWorkersPerGateway()).thenReturn(1);
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        smppConnectionManager.connect();
        smppConnectionManager.startMessagesProcessor();
        toSleep(2);
        assertEquals(0, jedisCluster.lpop(key, 1).size());
        smppConnectionManager.stopConnection();
    }

    @Test
    @DisplayName("Send message when message parts is not null")
    void startMessagesProcessorWhenMessagePartsIsNotNullThenCheckSubmitSmValues() {
        int port = 7781;
        executorService.submit(new SmppServerMock(port, null, null));
        Gateway smppGateway = getSMPPGw(port);
        smppGateway.setEncodingIso88591(3);
        smppGateway.setEncodingGsm7(1);
        smppGateway.setEncodingUcs2(2);
        String key = smppGateway.getNetworkId() + "_smpp_message";
        String messageId = "1722446896082-12194920127675";
        MessageEvent messageEvent = getMessageEvent();
        messageEvent.setShortMessage("Testing message part I'm the first part");
        messageEvent.setEsmClass(64);
        messageEvent.setMessageParts(
                List.of(
                        MessagePart.builder()
                                .messageId(messageId)
                                .shortMessage("Testing message part I'm the first part")
                                .segmentSequence(1)
                                .totalSegment(2)
                                .msgReferenceNumber("2")
                                .udhJson("{\"message\":\"Testing message part I'm the first part\",\"0x00\":[2,2,1]}")
                                .build(),
                        MessagePart.builder()
                                .messageId(messageId)
                                .shortMessage("Testing message part I'm the second part")
                                .segmentSequence(2)
                                .totalSegment(2)
                                .msgReferenceNumber("2")
                                .udhJson("{\"message\":\"Testing message part I'm the second part\",\"0x00\":[2,2,2]}")
                                .build()
                )
        );
        when(properties.getGatewaysWorkExecuteEvery()).thenReturn(1000);
        when(properties.getWorkersPerGateway()).thenReturn(1);
        when(properties.getSubmitSmResultQueue()).thenReturn("submit_sm_result");
        when(jedisCluster.llen(key)).thenReturn(1L).thenAnswer(invocationOnMock -> 0L);
        when(jedisCluster.lpop(key, 1)).thenReturn(List.of(messageEvent.toString()));
        when(jedisCluster.hget(anyString(), anyString())).thenReturn(new SubmitSmResponseEvent().toString());
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        smppConnectionManager.connect();
        smppConnectionManager.startMessagesProcessor();
        toSleep(5);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(jedisCluster, atLeastOnce()).hset(keyCaptor.capture(), fieldCaptor.capture(), valueCaptor.capture());
        SubmitSmResponseEvent submitSmResponseEvent = Converter.stringToObject(valueCaptor.getValue(), SubmitSmResponseEvent.class);
        assertEquals(properties.getSubmitSmResultQueue(), keyCaptor.getValue());
        assertEquals(fieldCaptor.getValue(), submitSmResponseEvent.getHashId());
        assertEquals(submitSmResponseEvent.getSubmitSmServerId(), messageEvent.getMessageId());
        assertEquals(smppGateway.getSessionsNumber(), smppConnectionManager.getSessions().size());
        smppConnectionManager.stopConnection();
    }

    @ParameterizedTest
    @MethodSource("errorParameters")
    @DisplayName("Try to send message when the error exists in no retry error list")
    void startMessagesProcessorWhenErrorExistsInNoErrorCodeListThenCheckSubmitSmValues(int errorCodeMapping, int port, String originProtocol, Long validityPeriod, boolean isLastRetry) {
        executorService.submit(new SmppServerMock(port, null, new NegativeResponseException(8)));
        Gateway smppGateway = getSMPPGw(port);
        smppGateway.setSessionsNumber(1);
        String key = smppGateway.getNetworkId() + "_smpp_message";
        MessageEvent messageEvent = getMessageEvent();
        messageEvent.setValidityPeriod(validityPeriod);
        messageEvent.setOriginProtocol(originProtocol);
        messageEvent.setLastRetry(isLastRetry);
        List<ErrorCodeMapping> errorCodeMappings = getErrorCodeMappingList(errorCodeMapping);
        when(properties.getGatewaysWorkExecuteEvery()).thenReturn(1000);
        when(properties.getWorkersPerGateway()).thenReturn(1);
        when(properties.getSubmitSmResultQueue()).thenReturn("submit_sm_result");
        when(jedisCluster.llen(key)).thenReturn(1L).thenAnswer(invocationOnMock -> 0L);
        when(jedisCluster.lpop(key, 1)).thenReturn(List.of(messageEvent.toString()));
        when(errorCodeMappingConcurrentHashMap.get(String.valueOf(smppGateway.getMno()))).thenReturn(errorCodeMappings);
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        smppConnectionManager.connect();
        smppConnectionManager.startMessagesProcessor();
        toSleep(5);
        if (!originProtocol.equals("ss7")) {
            ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(jedisCluster, atLeastOnce()).rpush(fieldCaptor.capture(), valueCaptor.capture());
            MessageEvent deliverSmEvent = Converter.stringToObject(valueCaptor.getValue(), MessageEvent.class);
            assertEquals(originProtocol + "_dlr", fieldCaptor.getValue());
            assertEquals(deliverSmEvent.getMessageId(), messageEvent.getMessageId());
            assertEquals(messageEvent.getDestProtocol(), deliverSmEvent.getOriginProtocol());
            assertEquals(messageEvent.getOriginProtocol(), deliverSmEvent.getDestProtocol());
            assertEquals(messageEvent.getDestNetworkId(), deliverSmEvent.getOriginNetworkId());
            assertEquals(messageEvent.getOriginNetworkId(), deliverSmEvent.getDestNetworkId());
            assertEquals(messageEvent.getRoutingId(), deliverSmEvent.getRoutingId());
        } else {
            verify(jedisCluster, never()).rpush(anyString(), anyString());
        }
        smppConnectionManager.stopConnection();
    }

    @ParameterizedTest
    @MethodSource("destinationParameters")
    @DisplayName("Try to send message when error exists in alternate destination list")
    void startMessagesProcessorWhenErrorExistsInAlternateDestinationListThenCheckSubmitSmValues(int port, String destinationProtocol, List<RoutingRule> routingRules) {
        executorService.submit(new SmppServerMock(port, null, new NegativeResponseException(8)));
        Gateway smppGateway = getSMPPGw(port);
        smppGateway.setSessionsNumber(1);
        smppGateway.setRetryAlternateDestinationErrorCode("8");
        String key = smppGateway.getNetworkId() + "_smpp_message";
        MessageEvent messageEvent = getMessageEvent();
        when(properties.getGatewaysWorkExecuteEvery()).thenReturn(1000);
        when(properties.getWorkersPerGateway()).thenReturn(1);
        when(properties.getSubmitSmResultQueue()).thenReturn("submit_sm_result");
        when(jedisCluster.llen(key)).thenReturn(1L).thenAnswer(invocationOnMock -> 0L);
        when(jedisCluster.lpop(key, 1)).thenReturn(List.of(messageEvent.toString()));
        when(routingRulesConcurrentHashMap.get(messageEvent.getOriginNetworkId())).thenReturn(routingRules);
        smppConnectionManager = new SmppConnectionManager(jedisCluster, smppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, properties, cdrProcessor);
        smppConnectionManager.connect();
        smppConnectionManager.startMessagesProcessor();
        toSleep(5);
        ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        if (!destinationProtocol.isEmpty()) {
            verify(jedisCluster, atLeastOnce()).rpush(fieldCaptor.capture(), valueCaptor.capture());
            MessageEvent submitSmEventToRetry = Converter.stringToObject(valueCaptor.getValue(), MessageEvent.class);
            assertEquals(submitSmEventToRetry.getDestNetworkId() + "_" + destinationProtocol + "_message", fieldCaptor.getValue());
            assertEquals(submitSmEventToRetry.getMessageId(), messageEvent.getMessageId());
            assertTrue(submitSmEventToRetry.isRetry());
            assertEquals(String.valueOf(messageEvent.getDestNetworkId()), submitSmEventToRetry.getRetryDestNetworkId());
        } else {
            verify(jedisCluster).rpush(fieldCaptor.capture(), valueCaptor.capture());
            MessageEvent deliverSmEvent = Converter.stringToObject(valueCaptor.getValue(), MessageEvent.class);
            assertEquals("smpp_dlr", fieldCaptor.getValue());
            assertEquals(deliverSmEvent.getMessageId(), messageEvent.getMessageId());
        }
        smppConnectionManager.stopConnection();
    }

    private static int invokePrivateMethod(Object targetObject, Class<?>[] parameterTypes, Object... parameters)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = targetObject.getClass().getDeclaredMethod("handleException", parameterTypes);
        method.setAccessible(true);
        return (int) method.invoke(targetObject, parameters);
    }

    private static Stream<Arguments> exceptionSendMessageParameters() {
        return Stream.of(
                Arguments.of(new ResponseTimeoutException(), 6001),
                Arguments.of(new NegativeResponseException(8), 6003)
        );
    }

    private static Stream<Arguments> exceptionParameters() {
        return Stream.of(
                Arguments.of(new PDUException(), 4444),
                Arguments.of(new InvalidResponseException("Testing Invalid Response Exception"), 6666),
                Arguments.of(new IOException(), 7777),
                Arguments.of(new Exception(), 8888)
        );
    }

    private static Stream<Arguments> errorParameters() {
        return Stream.of(
                //Origin protocol is http, validity period is 0 and last retry is false
                Arguments.of(8, 7782, "http", 0L, false),
                //Origin protocol is smpp, validity period is 0 and last retry is false
                Arguments.of(9, 7783, "smpp", 0L, false),
                //Origin protocol is ss7, validity period is 360 and last retry is true
                Arguments.of(9, 7784, "ss7", 360L, true)
        );
    }

    private static Stream<Arguments> destinationParameters() {
        return Stream.of(
                Arguments.of(7785, "http", getRoutingRuleList(3, "http")),
                Arguments.of(7786, "smpp", getRoutingRuleList(3, "smpp")),
                Arguments.of(7787, "ss7", getRoutingRuleList(3, "ss7")),
                Arguments.of(7788, "", getRoutingRuleList(3, "")),
                Arguments.of(7789, "", getRoutingRuleList(4, "")),
                Arguments.of(7790, "", List.of()),
                Arguments.of(7791, "", null)
        );
    }

    private static Gateway getSMPPGw(int port) {
        return Gateway.builder()
                .networkId(3)
                .name("smppgw")
                .systemId("smppgw")
                .password("1234")
                .ip("127.0.0.1")
                .port(port)
                .bindType("TRANSCEIVER")
                .systemType("")
                .interfaceVersion("IF_50")
                .sessionsNumber(1)
                .addressTON(1)
                .addressNPI(4)
                .addressRange("")
                .tps(1)
                .status("STARTED")
                .enabled(1)
                .enquireLinkPeriod(30000)
                .enquireLinkTimeout(5000)
                .requestDLR(1)
                .noRetryErrorCode("65")
                .retryAlternateDestinationErrorCode("640")
                .bindTimeout(5000)
                .bindRetryPeriod(10000)
                .pduTimeout(3000)
                .pduProcessorDegree(1)
                .threadPoolSize(100)
                .mno(1)
                .tlvMessageReceiptId(true)
                .messageIdDecimalFormat(false)
                .protocol("SMPP")
                .autoRetryErrorCode("64,65,66")
                .encodingIso88591(3)
                .encodingGsm7(1)
                .encodingUcs2(2)
                .splitMessage(false)
                .splitSmppType("TLV")
                .build();
    }

    private static MessageEvent getMessageEvent() {
        return MessageEvent.builder()
                .id("1722446896082-12194920127675")
                .messageId("1722446896082-12194920127675")
                .registeredDelivery(1)
                .originNetworkId(6)
                .systemId("smppgw")
                .deliverSmId("1")
                .dataCoding(0)
                .sourceAddrNpi(1)
                .sourceAddrTon(4)
                .sourceAddr("50510201020")
                .originProtocol("SMPP")
                .routingId(3)
                .retryDestNetworkId("")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .shortMessage("TESTING")
                .validityPeriod(160)
                .esmClass(0)
                .checkSubmitSmResponse(true)
                .build();
    }

    private static List<ErrorCodeMapping> getErrorCodeMappingList(Integer errorCode) {
        return List.of(ErrorCodeMapping.builder().errorCode(errorCode).deliveryErrorCode(55).deliveryStatus("DELIVRD").build());
    }

    private static List<RoutingRule> getRoutingRuleList(int id, String protocol) {
        RoutingRule.Destination destination = new RoutingRule.Destination();
        destination.setPriority(1);
        destination.setNetworkId(6);
        destination.setProtocol(protocol);
        destination.setNetworkType("GW");
        List<RoutingRule.Destination> destinations = List.of(destination);
        return List.of(
                RoutingRule.builder()
                        .id(id)
                        .originNetworkId(6)
                        .sriResponse(false)
                        .destination(destinations)
                        .newSourceAddrTon(-1)
                        .newSourceAddrNpi(-1)
                        .newDestAddrTon(-1)
                        .newDestAddrNpi(-1)
                        .dropMapSri(false)
                        .networkIdToMapSri(-1)
                        .networkIdToPermanentFailure(-1)
                        .dropTempFailure(false)
                        .networkIdTempFailure(-1)
                        .checkSriResponse(false)
                        .originProtocol(protocol)
                        .originNetworkType("SP")
                        .hasFilterRules(false)
                        .hasActionRules(false)
                        .build()
        );
    }

    static void toSleep(long seconds) {
        try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
            executorService.schedule(() -> {
            }, seconds, TimeUnit.SECONDS);
        }
    }
}
