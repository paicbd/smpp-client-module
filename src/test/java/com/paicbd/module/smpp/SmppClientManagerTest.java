package com.paicbd.module.smpp;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.paicbd.module.utils.Constants.PARAM_UPDATE_STATUS;
import static com.paicbd.module.utils.Constants.STOPPED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmppClientManagerTest {
    @Mock
    CdrProcessor cdrProcessor;
    @Mock
    JedisCluster jedisCluster;
    @Mock
    AppProperties appProperties;
    @Mock
    SocketSession socketSession;
    @Mock
    ConcurrentMap<Integer, SmppConnectionManager> smppConnectionManagerList;
    @Mock
    ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    @Mock
    ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap;
    @InjectMocks
    SmppClientManager smppClientManager;

    @Test
    @DisplayName("Init When Empty Lists Then Success")
    void initWhenEmptyListsThenSuccess() {
        var realSmppConnectionManagerList = new ConcurrentHashMap<Integer, SmppConnectionManager>();
        var realErrorCodeMappingConcurrentHashMap = new ConcurrentHashMap<String, List<ErrorCodeMapping>>();
        var realRoutingRulesConcurrentHashMap = new ConcurrentHashMap<Integer, List<RoutingRule>>();

        var smppConnectionManagerListFake = spy(realSmppConnectionManagerList);
        var errorCodeMappingConcurrentHashMapFake = spy(realErrorCodeMappingConcurrentHashMap);
        var routingRulesConcurrentHashMapFake = spy(realRoutingRulesConcurrentHashMap);

        var clientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerListFake,
                errorCodeMappingConcurrentHashMapFake,
                routingRulesConcurrentHashMapFake
        );
        var spy = spy(clientManager);
        spy.startManager();

        assertEquals(0, smppConnectionManagerListFake.size());
        assertEquals(0, errorCodeMappingConcurrentHashMapFake.size());
        assertEquals(0, routingRulesConcurrentHashMapFake.size());
    }

    @Test
    @DisplayName("Init When Data In Redis Then Load Maps Successfully")
    void initWhenDataInRedisThenLoadMapsSuccessfully() {
        SmppConnectionManager smppConnectionManager = new SmppConnectionManager(
                jedisCluster,
                Gateway.builder()
                        .networkId(1)
                        .systemId("op_01_smpp_gw")
                        .password("1234")
                        .mno(1)
                        .protocol("SMPP")
                        .build(),
                socketSession,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap,
                appProperties,
                cdrProcessor
        );

        ErrorCodeMapping errorCodeMapping = ErrorCodeMapping.builder()
                .errorCode(88)
                .deliveryErrorCode(0)
                .deliveryStatus("UNDELIV")
                .build();

        RoutingRule.Destination destination = new RoutingRule.Destination();
        destination.setPriority(1);
        destination.setNetworkId(3);
        destination.setProtocol("HTTP");
        destination.setNetworkType("GW");

        RoutingRule routingRule = RoutingRule.builder()
                .originNetworkId(1)
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .destination(List.of(destination))
                .build();

        var realSmppConnectionManagerList = new ConcurrentHashMap<Integer, SmppConnectionManager>();
        var realErrorCodeMappingConcurrentHashMap = new ConcurrentHashMap<String, List<ErrorCodeMapping>>();
        var realRoutingRulesConcurrentHashMap = new ConcurrentHashMap<Integer, List<RoutingRule>>();

        realSmppConnectionManagerList.put(1, smppConnectionManager);
        realErrorCodeMappingConcurrentHashMap.put("1", List.of(errorCodeMapping));
        realRoutingRulesConcurrentHashMap.put(1, List.of(routingRule));

        var smppConnectionManagerListFake = spy(realSmppConnectionManagerList);
        var errorCodeMappingConcurrentHashMapFake = spy(realErrorCodeMappingConcurrentHashMap);
        var routingRulesConcurrentHashMapFake = spy(realRoutingRulesConcurrentHashMap);

        var clientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerListFake,
                errorCodeMappingConcurrentHashMapFake,
                routingRulesConcurrentHashMapFake
        );

        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(appProperties.getRoutingRulesHash()).thenReturn("routing_rules");

        when(jedisCluster.hgetAll("gateways")).thenReturn(
                Map.of("1", smppConnectionManager.getGateway().toString(),
                        "2", Gateway.builder().networkId(2).protocol("HTTP").build().toString()));
        when(jedisCluster.hgetAll("error_code_mapping")).thenReturn(Map.of("1", Converter.valueAsString(List.of(errorCodeMapping))));
        when(jedisCluster.hgetAll("routing_rules")).thenReturn(Map.of("1", Converter.valueAsString(List.of(routingRule))));

        var spy = spy(clientManager);
        spy.startManager();

        assertEquals(1, smppConnectionManagerListFake.size());
        assertEquals(1, errorCodeMappingConcurrentHashMapFake.size());
        assertEquals(1, routingRulesConcurrentHashMapFake.size());
    }

    @Test
    @DisplayName("Update Gateway Non Existing In Redis Then Do Nothing")
    void updateGatewayWhenNonExistingInRedisThenDoNothing() {
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget("gateways", "1")).thenReturn(null);

        var spy = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        spy.updateGateway("1");
        verify(jedisCluster).hget("gateways", "1");

        verifyNoInteractions(cdrProcessor);
        verifyNoInteractions(socketSession);
        verifyNoInteractions(smppConnectionManagerList);
    }

    @Test
    @DisplayName("Update Gateway Existing In Redis But Not SMPP, Not Put Or Update Gateway")
    void updateGatewayWhenExistingInRedisButNotSmppThenNotPutOrUpdateGateway() {
        Gateway httpGateway = Gateway.builder()
                .protocol("HTTP")
                .networkId(1)
                .build();

        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget("gateways", "1")).thenReturn(httpGateway.toString());

        var spy = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        spy.updateGateway("1");
        verify(jedisCluster).hget("gateways", "1");

        verifyNoInteractions(cdrProcessor);
        verifyNoInteractions(socketSession);
        verifyNoInteractions(smppConnectionManagerList);
    }

    @Test
    @DisplayName("Update Gateway Existing In Redis And Is SMPP, Put Or Update Gateway")
    void updateGatewayWhenExistingInRedisAndIsSmppButKeyNonExistentInSmppConnectionManagerListThenSuccess() {
        Gateway smppGateway = Gateway.builder()
                .networkId(1)
                .systemId("op_01_smpp_gw")
                .password("1234")
                .mno(1)
                .protocol("SMPP")
                .build();

        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget("gateways", "1")).thenReturn(smppGateway.toString());

        ConcurrentMap<Integer, SmppConnectionManager> realMap = new ConcurrentHashMap<>();
        ConcurrentMap<Integer, SmppConnectionManager> smppConnectionManagerListSpy = spy(realMap);

        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerListSpy,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );
        SmppClientManager spy = spy(smppClientManager);

        spy.updateGateway("1");
        verify(jedisCluster).hget("gateways", "1");

        ArgumentCaptor<Integer> keyCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<SmppConnectionManager> valueCaptor = ArgumentCaptor.forClass(SmppConnectionManager.class);
        verify(smppConnectionManagerListSpy).put(keyCaptor.capture(), valueCaptor.capture());

        assertEquals(valueCaptor.getValue(), smppConnectionManagerListSpy.get(1));
        assertTrue(smppConnectionManagerListSpy.containsKey(keyCaptor.getValue()));

        assertNotNull(smppConnectionManagerListSpy.get(1));
        assertEquals(1, smppConnectionManagerListSpy.size());
        assertEquals(1, smppConnectionManagerListSpy.get(1).getGateway().getNetworkId());
        assertEquals("op_01_smpp_gw", smppConnectionManagerListSpy.get(1).getGateway().getSystemId());
        assertEquals("1234", smppConnectionManagerListSpy.get(1).getGateway().getPassword());
        assertEquals(1, smppConnectionManagerListSpy.get(1).getGateway().getMno());
        assertEquals("SMPP", smppConnectionManagerListSpy.get(1).getGateway().getProtocol());

        verifyNoInteractions(cdrProcessor);
    }

    @Test
    @DisplayName("Update Gateway Existing In Redis And Is SMPP, Put Or Update Gateway")
    void updateGatewayWhenExistingInRedisAndIsSmppButKeyExistentInSmppConnectionManagerListThenSuccess() {
        Gateway previousSmppGateway = Gateway.builder()
                .networkId(1)
                .name("SMPP-Operator before update")
                .systemId("op_01_smpp_gw")
                .password("1234")
                .mno(1)
                .protocol("SMPP")
                .addressRange("")
                .build();
        ConcurrentMap<Integer, SmppConnectionManager> realMap = new ConcurrentHashMap<>();
        realMap.put(1, new SmppConnectionManager(jedisCluster, previousSmppGateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, appProperties, cdrProcessor));
        ConcurrentMap<Integer, SmppConnectionManager> smppConnectionManagerListSpy = spy(realMap);

        Gateway updatedSmppGateway = Gateway.builder()
                .networkId(1)
                .name("SMPP-Operator after update")
                .systemId("op_01_smpp_gw")
                .password("1234")
                .mno(2)
                .protocol("SMPP")
                .pduProcessorDegree(10)
                .threadPoolSize(100)
                .bindRetryPeriod(30000)
                .addressRange("1234")
                .build();
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget("gateways", "1")).thenReturn(updatedSmppGateway.toString());

        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerListSpy,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );
        SmppClientManager spy = spy(smppClientManager);

        spy.updateGateway("1");
        verify(jedisCluster).hget("gateways", "1");

        assertNotNull(smppConnectionManagerListSpy.get(1));
        assertEquals(1, smppConnectionManagerListSpy.size());
        assertEquals(1, smppConnectionManagerListSpy.get(1).getGateway().getNetworkId());

        assertEquals("SMPP-Operator after update", smppConnectionManagerListSpy.get(1).getGateway().getName());
        assertEquals(2, smppConnectionManagerListSpy.get(1).getGateway().getMno());
        assertNotEquals(previousSmppGateway.toString(), smppConnectionManagerListSpy.get(1).getGateway().toString());
        assertNotEquals(previousSmppGateway.getMno(), smppConnectionManagerListSpy.get(1).getGateway().getMno());
        assertNotEquals(previousSmppGateway.getName(), smppConnectionManagerListSpy.get(1).getGateway().getName());
        assertNotEquals(previousSmppGateway.getPduProcessorDegree(), smppConnectionManagerListSpy.get(1).getGateway().getPduProcessorDegree());
        assertNotEquals(previousSmppGateway.getThreadPoolSize(), smppConnectionManagerListSpy.get(1).getGateway().getThreadPoolSize());
        assertNotEquals(previousSmppGateway.getBindRetryPeriod(), smppConnectionManagerListSpy.get(1).getGateway().getBindRetryPeriod());
        assertNotEquals(previousSmppGateway.getAddressRange(), smppConnectionManagerListSpy.get(1).getGateway().getAddressRange());

        verifyNoInteractions(cdrProcessor);
    }

    @Test
    @DisplayName("Connect Gateway When Gateway Not Exists Then Do Nothing")
    void connectGatewayWhenGatewayNotExistsThenDoNothing() {
        when(smppConnectionManagerList.get(1)).thenReturn(null);
        var clientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );
        var spy = spy(clientManager);

        spy.connectGateway("1");
        verify(smppConnectionManagerList).get(1);

        verifyNoInteractions(cdrProcessor);
        verifyNoInteractions(socketSession);
    }

    @Test
    @DisplayName("Connect Gateway When Gateway Exists Then Success")
    void connectGatewayWhenGatewayExistsThenSuccess() {
        Gateway gateway = Gateway.builder()
                .networkId(1)
                .systemId("op_01_smpp_gw")
                .password("1234")
                .status(STOPPED)
                .enabled(0)
                .mno(1)
                .protocol("SMPP")
                .build();

        SmppConnectionManager smppConnectionManager = new SmppConnectionManager(jedisCluster, gateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, appProperties, cdrProcessor);
        var spySmppConnectionManager = spy(smppConnectionManager);
        when(smppConnectionManagerList.get(1)).thenReturn(spySmppConnectionManager);

        var clientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );
        var spy = spy(clientManager);

        spy.connectGateway("1");
        verify(spySmppConnectionManager).connect();
        assertEquals("STARTED", smppConnectionManager.getGateway().getStatus());
        assertEquals(1, smppConnectionManager.getGateway().getEnabled());
    }

    @Test
    @DisplayName("Connect Gateway When Force Error Then Not Execute Connect And Not Update Status")
    void connectGatewayWhenForceErrorThenNotExecuteConnectAndNotUpdateStatus() {
        Gateway gateway = Gateway.builder()
                .networkId(1)
                .systemId("op_01_smpp_gw")
                .password("1234")
                .status(STOPPED)
                .enabled(0)
                .mno(1)
                .protocol("SMPP")
                .build();

        var smppConnectionManagerListInstance = new ConcurrentHashMap<Integer, SmppConnectionManager>();
        var smppConnectionManager = new SmppConnectionManager(
                jedisCluster, gateway, socketSession, errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap, appProperties, cdrProcessor);
        smppConnectionManagerListInstance.put(gateway.getNetworkId(), smppConnectionManager);
        var smppConnectionManagerSpy = spy(smppConnectionManagerListInstance);

        when(smppConnectionManagerSpy.get(1))
                .thenThrow(new RuntimeException("Forced error"))
                .thenReturn(smppConnectionManager);

        var clientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerSpy,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );
        var spy = spy(clientManager);
        spy.connectGateway("1");

        assertEquals(STOPPED, smppConnectionManagerSpy.get(1).getGateway().getStatus());
        assertEquals(0, smppConnectionManagerSpy.get(1).getGateway().getEnabled());
    }

    @Test
    @DisplayName("Stop Gateway When Gateway Not Exists Then Do Nothing")
    void stopGatewayWhenGatewayNotExistsThenDoNothing() {
        when(smppConnectionManagerList.get(1)).thenReturn(null);
        var clientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );
        var spy = spy(clientManager);

        spy.stopGateway("1");
        verify(smppConnectionManagerList).get(1);

        verifyNoInteractions(cdrProcessor);
        verifyNoInteractions(socketSession);
    }

    @Test
    @DisplayName("Stop Gateway When Gateway Exists Then Success")
    void stopGatewayWhenGatewayExistsThenSuccess() {
        Gateway gateway = Gateway.builder()
                .networkId(1)
                .systemId("op_01_smpp_gw")
                .password("1234")
                .status("STARTED")
                .enabled(1)
                .mno(1)
                .protocol("SMPP")
                .build();

        SmppConnectionManager smppConnectionManager = new SmppConnectionManager(jedisCluster, gateway, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, appProperties, cdrProcessor);
        var spySmppConnectionManager = spy(smppConnectionManager);
        when(smppConnectionManagerList.get(1)).thenReturn(spySmppConnectionManager);

        var clientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );
        var spy = spy(clientManager);

        spy.stopGateway("1");
        verify(socketSession).sendStatus("1", PARAM_UPDATE_STATUS, STOPPED);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Update Error Code Mapping When ErrorCode Mapping Exists Then Update")
    void updateErrorCodeMappingWhenErrorCodeMappingNotExistsThenDoNothing() {
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), "1")).thenReturn(null);

        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        smppClientManager.updateErrorCodeMapping("1");

        verify(jedisCluster).hget(appProperties.getKeyErrorCodeMapping(), "1");
        verify(errorCodeMappingConcurrentHashMap).remove("1");
        verify(errorCodeMappingConcurrentHashMap, never()).put("1", eq(any(List.class)));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Update Error Code Mapping When ErrorCode Mapping Exists Then Update")
    void updateErrorCodeMappingWhenErrorCodeMappingExistsThenUpdate() {
        ErrorCodeMapping errorCodeMapping = ErrorCodeMapping.builder()
                .errorCode(88)
                .deliveryErrorCode(0)
                .deliveryStatus("UNDELIV")
                .build();
        List<ErrorCodeMapping> errorCodeMappingList = List.of(errorCodeMapping);

        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), "1"))
                .thenReturn(Converter.valueAsString(errorCodeMappingList));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<ErrorCodeMapping>> captor = ArgumentCaptor.forClass(List.class);
        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        smppClientManager.updateErrorCodeMapping("1");
        verify(jedisCluster).hget(appProperties.getKeyErrorCodeMapping(), "1");
        verify(errorCodeMappingConcurrentHashMap, never()).remove("1");
        verify(errorCodeMappingConcurrentHashMap).put(keyCaptor.capture(), captor.capture());
        assertEquals(Converter.valueAsString(errorCodeMappingList), Converter.valueAsString(captor.getValue()));
    }

    @Test
    @DisplayName("Update Routing Rules When Routing Rules Not Exists Then Do Nothing")
    void onDeleteGatewayWhenGatewayNotExistsThenDoNothing() {
        String stringNetworkId = "2";
        int networkId = Integer.parseInt(stringNetworkId);
        SmppConnectionManager smppConnectionManager = mock(SmppConnectionManager.class);
        smppConnectionManagerList = spy(new ConcurrentHashMap<>());

        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        smppClientManager.deleteGateway(stringNetworkId);
        verify(smppConnectionManagerList, never()).remove(networkId);
        verify(smppConnectionManager, never()).stopConnection();
    }

    @Test
    @DisplayName("On Delete Gateway When Gateway Exists Then Remove From List And Stop Connection")
    void onDeleteGatewayWhenGatewayExistsThenRemoveFromListAndStopConnection() {
        String stringNetworkId = "1";
        int networkId = Integer.parseInt(stringNetworkId);
        var smppConnectionManager = mock(SmppConnectionManager.class);

        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        when(smppConnectionManagerList.get(networkId)).thenReturn(smppConnectionManager);
        smppClientManager.deleteGateway(stringNetworkId);
        verify(smppConnectionManagerList).remove(networkId);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Update Routing Rule When Routing Rule Not Exists Then Do Nothing")
    void updateRoutingRuleWhenRoutingRuleNotExistsThenDoNothing() {
        when(appProperties.getRoutingRulesHash()).thenReturn("routing_rules");
        when(jedisCluster.hget(appProperties.getRoutingRulesHash(), "1")).thenReturn(null);

        smppClientManager.updateRoutingRule("1");
        verify(routingRulesConcurrentHashMap, never()).put(anyInt(), any(List.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Update Routing Rule When Routing Rule Is Present Then Update Map")
    void updateRoutingRuleWhenRoutingRuleIsPresentThenUpdateMap() {
        RoutingRule.Destination destination = new RoutingRule.Destination();
        destination.setPriority(1);
        destination.setNetworkId(3);
        destination.setProtocol("HTTP");
        destination.setNetworkType("GW");

        RoutingRule routingRule = RoutingRule.builder()
                .originNetworkId(1)
                .originNetworkType("SP")
                .originProtocol("SMPP")
                .destination(List.of(destination))
                .build();

        routingRulesConcurrentHashMap = spy(new ConcurrentHashMap<>());

        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        when(appProperties.getRoutingRulesHash()).thenReturn("routing_rules");
        when(jedisCluster.hget(appProperties.getRoutingRulesHash(), "1")).thenReturn(Converter.valueAsString(List.of(routingRule)));

        smppClientManager.updateRoutingRule("1");
        verify(routingRulesConcurrentHashMap).put(anyInt(), any(List.class));

        assertEquals(1, routingRulesConcurrentHashMap.size());
        assertEquals(Converter.valueAsString(List.of(routingRule)), Converter.valueAsString(routingRulesConcurrentHashMap.get(1)));
    }

    @Test
    @DisplayName("Delete Routing Rule, always remove from map")
    void deleteRoutingRuleWhenReceiveRequestThenRemoveFromMap() {
        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        smppClientManager.deleteRoutingRule("6");
        verify(routingRulesConcurrentHashMap).remove(6);
    }

    @Test
    @DisplayName("Pre Destroy When Called Then Stop All Connections")
    void preDestroyWhenCalledThenStopAllConnections() {
        Gateway gw1 = Gateway.builder()
                .networkId(1)
                .systemId("op_01_smpp_gw")
                .password("1234")
                .mno(1)
                .protocol("SMPP")
                .build();

        Gateway gw2 = Gateway.builder()
                .networkId(2)
                .systemId("op_02_smpp_gw")
                .password("1234")
                .mno(1)
                .protocol("SMPP")
                .build();

        SmppConnectionManager smppConnectionManager1 = new SmppConnectionManager(jedisCluster, gw1, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, appProperties, cdrProcessor);
        SmppConnectionManager smppConnectionManager2 = new SmppConnectionManager(jedisCluster, gw2, socketSession, errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, appProperties, cdrProcessor);

        smppConnectionManager1 = spy(smppConnectionManager1);
        smppConnectionManager2 = spy(smppConnectionManager2);

        smppConnectionManagerList = spy(new ConcurrentHashMap<>());
        smppConnectionManagerList.put(1, smppConnectionManager1);
        smppConnectionManagerList.put(2, smppConnectionManager2);

        smppClientManager = new SmppClientManager(
                cdrProcessor,
                jedisCluster,
                appProperties,
                socketSession,
                smppConnectionManagerList,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap
        );

        smppClientManager.stopAllConnections();

        verify(smppConnectionManager1).stopConnection();
        verify(smppConnectionManager2).stopConnection();
    }
}