package com.paicbd.module.smpp;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmppClientManagerTest {
    @Mock
    CdrProcessor cdrProcessor;
    @Mock(strictness = Mock.Strictness.LENIENT)
    JedisCluster jedisCluster;
    @Mock(strictness = Mock.Strictness.LENIENT)
    AppProperties appProperties;
    @Mock
    SocketSession socketSession;
    @Mock
    ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    @Mock
    ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap;
    @InjectMocks
    SmppClientManager smppClientManager;

    @BeforeEach
    void setUp() {
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateway");
        when(jedisCluster.hkeys(appProperties.getKeyGatewayRedis())).thenReturn(Set.of("smppgw"));
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "smppgw")).thenReturn(
                "{\"network_id\":1,\"name\":\"smppgw\",\"system_id\":\"smppgw\",\"password\":\"1234\",\"ip\":\"192.168.100.20\",\"port\":7001,\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":10,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"500\",\"tps\":10,\"status\":\"STOPPED\",\"enabled\":0,\"enquire_link_period\":30000,\"enquire_link_timeout\":0,\"request_dlr\":true,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"active_sessions_numbers\":0,\"protocol\":\"SMPP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":3,\"encoding_gsm7\":0,\"encoding_ucs2\":2,\"split_message\":false,\"split_smpp_type\":\"TLV\"}"
        );

        when((appProperties.getKeyErrorCodeMapping())).thenReturn("error_code_mapping");
        when(jedisCluster.hkeys(appProperties.getKeyErrorCodeMapping())).thenReturn(Set.of("1"));
        when(jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), "1")).thenReturn(
                "[{\"error_code\":100,\"delivery_error_code\":55,\"delivery_status\":\"DELIVRD\"}]"
        );

        when(appProperties.getRoutingRulesHash()).thenReturn("routing_rules");
        when(jedisCluster.hgetAll("routing_rules")).thenReturn(
                Map.of(
                        "3", "[{\"id\":1,\"origin_network_id\":3,\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"is_sri_response\":false,\"destination\":[{\"priority\":1,\"network_id\":1,\"dest_protocol\":\"SMPP\",\"network_type\":\"GW\"}],\"new_source_addr\":\"\",\"new_source_addr_ton\":-1,\"new_source_addr_npi\":-1,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":-1,\"new_dest_addr_npi\":-1,\"add_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"check_sri_response\":false,\"origin_protocol\":\"SMPP\",\"origin_network_type\":\"SP\",\"has_filter_rules\":false,\"has_action_rules\":false}]"
                )
        );
    }

    @Test
    void startManagerTest() {
        // All found
        assertDoesNotThrow(() -> smppClientManager.startManager());

        // All not found
        when(jedisCluster.hkeys(appProperties.getKeyGatewayRedis())).thenReturn(Set.of());
        when(jedisCluster.hkeys(appProperties.getKeyErrorCodeMapping())).thenReturn(Set.of());
        when(jedisCluster.hgetAll(appProperties.getRoutingRulesHash())).thenReturn(Map.of());
        assertDoesNotThrow(() -> smppClientManager.startManager());

        // Exception
        when(jedisCluster.hkeys(appProperties.getKeyErrorCodeMapping())).thenReturn(Set.of("{invalidJson}"));
        when(jedisCluster.hgetAll(appProperties.getRoutingRulesHash())).thenReturn(Map.of("3", "{invalidJson}"));
        when(jedisCluster.hkeys(appProperties.getKeyGatewayRedis())).thenReturn(Set.of("{invalidJson}"));
        assertThrows(IllegalArgumentException.class, () -> smppClientManager.startManager());
    }

    @Test
    void startManager_gwParamsZeroTest() {
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "smppgw")).thenReturn(
                "{\"network_id\":1,\"name\":\"smppgw\",\"system_id\":\"smppgw\",\"password\":\"1234\",\"ip\":\"192.168.100.20\",\"port\":7001,\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":10,\"address_ton\":0,\"address_npi\":0,\"address_range\":null,\"tps\":10,\"status\":\"STOPPED\",\"enabled\":0,\"enquire_link_period\":30000,\"enquire_link_timeout\":0,\"request_dlr\":true,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":0,\"pdu_timeout\":5000,\"pdu_degree\":0,\"thread_pool_size\":0,\"mno_id\":1,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"active_sessions_numbers\":0,\"protocol\":\"SMPP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":3,\"encoding_gsm7\":0,\"encoding_ucs2\":2,\"split_message\":false,\"split_smpp_type\":\"TLV\"}"
        );
        assertDoesNotThrow(() -> smppClientManager.startManager());
    }

    @Test
    void updateGatewayTest() {
        // gatewayInRaw Null
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "ignored")).thenReturn(null);
        assertDoesNotThrow(() -> smppClientManager.updateGateway("ignored"));

        // gateway is not SMPP
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "smppgw")).thenReturn(
                "{\"network_id\":1,\"name\":\"smppgw\",\"system_id\":\"smppgw\",\"password\":\"1234\",\"ip\":\"192.168.100.20\",\"port\":7001,\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":10,\"address_ton\":0,\"address_npi\":0,\"address_range\":null,\"tps\":10,\"status\":\"STOPPED\",\"enabled\":0,\"enquire_link_period\":30000,\"enquire_link_timeout\":0,\"request_dlr\":true,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":0,\"pdu_timeout\":5000,\"pdu_degree\":0,\"thread_pool_size\":0,\"mno_id\":1,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"active_sessions_numbers\":0,\"protocol\":\"HTTP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":3,\"encoding_gsm7\":0,\"encoding_ucs2\":2,\"split_message\":false,\"split_smpp_type\":\"TLV\"}"
        );
        assertDoesNotThrow(() -> smppClientManager.updateGateway("smppgw"));

        // Cases for containsKey
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "smppgw")).thenReturn(
                "{\"network_id\":1,\"name\":\"smppgw\",\"system_id\":\"smppgw\",\"password\":\"1234\",\"ip\":\"192.168.100.20\",\"port\":7001,\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":10,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"500\",\"tps\":10,\"status\":\"STOPPED\",\"enabled\":0,\"enquire_link_period\":30000,\"enquire_link_timeout\":0,\"request_dlr\":true,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"active_sessions_numbers\":0,\"protocol\":\"SMPP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":3,\"encoding_gsm7\":0,\"encoding_ucs2\":2,\"split_message\":false,\"split_smpp_type\":\"TLV\"}"
        );
        Gateway gateway = new Gateway();
        SmppConnectionManager smppConnectionManager = new SmppConnectionManager(jedisCluster, gateway, socketSession,
                errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, appProperties, cdrProcessor);
        ConcurrentMap<String, SmppConnectionManager> newMap = new ConcurrentHashMap<>();
        newMap.put("smppgw", smppConnectionManager);

        // no containsKey
        assertDoesNotThrow(() -> smppClientManager.updateGateway("smppgw"));

        // containsKey
        this.smppClientManager = new SmppClientManager(cdrProcessor, jedisCluster, appProperties, socketSession, newMap,
                errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap);
        assertDoesNotThrow(() -> this.smppClientManager.updateGateway("smppgw"));
    }

    @Test
    void connectGatewayTest() {
        // connectionManager does not exist for the systemId
        assertDoesNotThrow(() -> smppClientManager.connectGateway("doesNotExists"));

        // connecting gateway
        ConcurrentMap<String, SmppConnectionManager> newMap = getStringSmppConnectionManagerConcurrentMap();
        this.smppClientManager = new SmppClientManager(cdrProcessor, jedisCluster, appProperties, socketSession, newMap,
                errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap);
        assertDoesNotThrow(() -> this.smppClientManager.connectGateway("smppgw"));
    }

    @Test
    void stopGatewayTest() {
        // connectionManager does not exist for the systemId
        assertDoesNotThrow(() -> smppClientManager.stopGateway("doesNotExists"));

        // stopping gateway
        ConcurrentMap<String, SmppConnectionManager> newMap = getStringSmppConnectionManagerConcurrentMap();
        this.smppClientManager = new SmppClientManager(cdrProcessor, jedisCluster, appProperties, socketSession, newMap,
                errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap);
        assertDoesNotThrow(() -> this.smppClientManager.stopGateway("smppgw"));
    }

    @Test
    void deleteGatewayTest() {
        // connectionManager does not exist for the systemId
        assertDoesNotThrow(() -> smppClientManager.deleteGateway("doesNotExists"));

        // deleting gateway
        ConcurrentMap<String, SmppConnectionManager> newMap = getStringSmppConnectionManagerConcurrentMap();
        this.smppClientManager = new SmppClientManager(cdrProcessor, jedisCluster, appProperties, socketSession, newMap,
                errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap);
        assertDoesNotThrow(() -> this.smppClientManager.deleteGateway("smppgw"));
    }

    @Test
    void updateErrorCodeMappingTest() {
        // errorCodeMapping does not exist for the mnoId
        assertDoesNotThrow(() -> smppClientManager.updateErrorCodeMapping("doesNotExists"));

        // updating errorCodeMapping
        when(jedisCluster.hkeys(appProperties.getKeyErrorCodeMapping())).thenReturn(Set.of("1"));
        when(jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), "1")).thenReturn(
                "[{\"error_code\":100,\"delivery_error_code\":55,\"delivery_status\":\"DELIVRD\"}]"
        );
        assertDoesNotThrow(() -> smppClientManager.updateErrorCodeMapping("1"));

        // JsonProcessingException does not throw
        when(jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), "1")).thenReturn("{invalidJson}");
        assertDoesNotThrow(() -> smppClientManager.updateErrorCodeMapping("1"));
    }

    @Test
    void updateRoutingRuleTest() {
        // routingRule does not exist for the networkId
        assertDoesNotThrow(() -> smppClientManager.updateRoutingRule("doesNotExists"));

        // Ok
        when(jedisCluster.hget(appProperties.getRoutingRulesHash(), "3")).thenReturn(
                "[{\"id\":1,\"origin_network_id\":3,\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"is_sri_response\":false,\"destination\":[{\"priority\":1,\"network_id\":1,\"dest_protocol\":\"SMPP\",\"network_type\":\"GW\"}],\"new_source_addr\":\"\",\"new_source_addr_ton\":-1,\"new_source_addr_npi\":-1,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":-1,\"new_dest_addr_npi\":-1,\"add_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"check_sri_response\":false,\"origin_protocol\":\"SMPP\",\"origin_network_type\":\"SP\",\"has_filter_rules\":false,\"has_action_rules\":false}]"
        );
        assertDoesNotThrow(() -> smppClientManager.updateRoutingRule("3"));

        // JsonProcessingException does not throw
        when(jedisCluster.hget(appProperties.getRoutingRulesHash(), "3")).thenReturn(
                "3", "[{\"id\":1,\"origin_network_id\":3,\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"is_sri_response\":false,\"destination\":[{\"priority\":1,\"network_id\":1,\"dest_protocol\":\"SMPP\",\"network_type\":\"GW\"}],\"new_source_addr\":\"\",\"new_source_addr_ton\":-1,\"new_source_addr_npi\":-1,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":-1,\"new_dest_addr_npi\":-1,\"add_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"check_sri_response\":false,\"origin_protocol\":\"SMPP\",\"origin_network_type\":\"SP\",\"has_filter_rules\":false,\"has_action_rules\":false}]"
        );
        assertDoesNotThrow(() -> smppClientManager.updateRoutingRule("3"));
    }

    @Test
    void deleteRoutingRuleTest() {
        // routingRule does not exist for the networkId
        assertDoesNotThrow(() -> smppClientManager.deleteRoutingRule("6"));
        assertThrows(NumberFormatException.class, () -> smppClientManager.deleteRoutingRule("invalidId"));

        // deleting routingRule
        when(jedisCluster.hgetAll("routing_rules")).thenReturn(
                Map.of(
                        "3", "[{\"id\":1,\"origin_network_id\":3,\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"is_sri_response\":false,\"destination\":[{\"priority\":1,\"network_id\":1,\"dest_protocol\":\"SMPP\",\"network_type\":\"GW\"}],\"new_source_addr\":\"\",\"new_source_addr_ton\":-1,\"new_source_addr_npi\":-1,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":-1,\"new_dest_addr_npi\":-1,\"add_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"check_sri_response\":false,\"origin_protocol\":\"SMPP\",\"origin_network_type\":\"SP\",\"has_filter_rules\":false,\"has_action_rules\":false}]"
                )
        );
        assertDoesNotThrow(() -> smppClientManager.deleteRoutingRule("3"));

        // JsonProcessingException does not throw
        when(jedisCluster.hgetAll("routing_rules")).thenReturn(Map.of("3", "{invalidJson}"));
        assertDoesNotThrow(() -> smppClientManager.deleteRoutingRule("3"));
    }

    @Test
    void stopAllConnectionsTest() {
        // Empty
        assertDoesNotThrow(() -> smppClientManager.stopAllConnections());

        // Not Empty
        ConcurrentMap<String, SmppConnectionManager> newMap = getStringSmppConnectionManagerConcurrentMap();
        this.smppClientManager = new SmppClientManager(cdrProcessor, jedisCluster, appProperties, socketSession, newMap,
                errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap);
        assertDoesNotThrow(() -> this.smppClientManager.stopAllConnections());
    }

    private ConcurrentMap<String, SmppConnectionManager> getStringSmppConnectionManagerConcurrentMap() {
        Gateway gateway = new Gateway();
        gateway.setSessionsNumber(3);
        gateway.setSystemId("smppgw");
        gateway.setName("smppgw");
        SmppConnectionManager smppConnectionManager = new SmppConnectionManager(jedisCluster, gateway, socketSession,
                errorCodeMappingConcurrentHashMap, routingRulesConcurrentHashMap, appProperties, cdrProcessor);
        ConcurrentMap<String, SmppConnectionManager> newMap = new ConcurrentHashMap<>();
        newMap.put("smppgw", smppConnectionManager);
        return newMap;
    }
}