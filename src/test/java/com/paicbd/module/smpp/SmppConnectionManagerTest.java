package com.paicbd.module.smpp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.MessagePart;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.ws.SocketSession;
import org.jsmpp.PDUStringException;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.MessageId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.powermock.api.mockito.PowerMockito.when;

@ExtendWith(MockitoExtension.class)
class SmppConnectionManagerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock(strictness = Mock.Strictness.LENIENT)
    JedisCluster jedisCluster;
    @Mock
    SocketSession socketSession;
    @Mock
    AppProperties properties;
    @Mock
    ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    @Mock
    ConcurrentMap<Integer, List<RoutingRule>> routingRulesConcurrentHashMap;
    @Mock
    MessageReceiverListenerImpl messageReceiverListener;
    @Mock
    CdrProcessor cdrProcessor;

    Gateway gateway;
    MessageEvent messageEvent;
    SmppConnectionManager smppConnectionManager;

    @BeforeEach
    void setUp() {
        gateway = new Gateway();
        gateway.setNetworkId(1);
        gateway.setName("smppgw");
        gateway.setSystemId("systemId");
        gateway.setPassword("password");
        gateway.setIp("192.168.100.20");
        gateway.setPort(7001);
        gateway.setBindType("TRANSCEIVER");
        gateway.setSystemType("");
        gateway.setInterfaceVersion("IF_50");
        gateway.setSessionsNumber(10);
        gateway.setAddressTON(0);
        gateway.setAddressNPI(0);
        gateway.setAddressRange(null);
        gateway.setTps(10);
        gateway.setSuccessSession(0);
        gateway.setStatus("STARTED");
        gateway.setEnabled(0);
        gateway.setEnquireLinkPeriod(30000);
        gateway.setEnquireLinkTimeout(0);
        gateway.setRequestDLR(true);
        gateway.setNoRetryErrorCode("");
        gateway.setRetryAlternateDestinationErrorCode("");
        gateway.setBindTimeout(5000);
        gateway.setBindRetryPeriod(10000);
        gateway.setPduTimeout(5000);
        gateway.setPduProcessorDegree(1);
        gateway.setThreadPoolSize(100);
        gateway.setMno(1);
        gateway.setTlvMessageReceiptId(false);
        gateway.setMessageIdDecimalFormat(false);
        gateway.setProtocol("SMPP");
        gateway.setAutoRetryErrorCode("");
        gateway.setEncodingIso88591(3);
        gateway.setEncodingGsm7(0);
        gateway.setEncodingUcs2(2);
        gateway.setSplitMessage(false);
        gateway.setSplitSmppType("TLV");
        gateway.setAutoRetryErrorCode("101,102");

        this.messageReceiverListener = new MessageReceiverListenerImpl(
                gateway,
                jedisCluster,
                "preDeliverQueue",
                "preMessageQueue",
                "submitSmResultQueue",
                cdrProcessor
        );

        this.smppConnectionManager = new SmppConnectionManager(
                jedisCluster,
                gateway,
                socketSession,
                errorCodeMappingConcurrentHashMap,
                routingRulesConcurrentHashMap,
                properties,
                cdrProcessor
        );
    }

    @Test
    void addInCacheTest() throws PDUStringException {
        messageEvent = new MessageEvent();
        messageEvent.setSystemId("systemId");
        messageEvent.setMessageId("pr1");
        messageEvent.setOriginProtocol("SMPP");
        messageEvent.setDestProtocol("SMPP");
        messageEvent.setOriginNetworkId(1);
        messageEvent.setOriginProtocol("SMPP");

        int registeredDelivery = 1;
        SubmitSmResult submitSmResult = new SubmitSmResult(new MessageId("id"), null);
        assertDoesNotThrow(() -> smppConnectionManager.addInCache(registeredDelivery, messageEvent, submitSmResult));

        int registeredDeliveryFalse = 0;
        MessageEvent submitSmFalse = new MessageEvent();
        SubmitSmResult submitSmResultFalse = new SubmitSmResult(new MessageId("id"), null);
        assertDoesNotThrow(() -> smppConnectionManager.addInCache(registeredDeliveryFalse, submitSmFalse, submitSmResultFalse));
    }

    @Test
    void sendToRetryProcessTest() throws JsonProcessingException {
        messageEvent = new MessageEvent();
        messageEvent.setSystemId("systemId");
        messageEvent.setMessageId("pr1");
        messageEvent.setOriginProtocol("SMPP");
        messageEvent.setDestProtocol("SMPP");
        messageEvent.setOriginNetworkId(1);
        messageEvent.setOriginProtocol("SMPP");

        // Handling noRetryErrorCode when errorCode is not in the list
        this.gateway.setNoRetryErrorCode("11,12,13,14,15");
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 11));

        messageEvent.setDestProtocol("HTTP");
        ConcurrentMap<String, List<ErrorCodeMapping>> ecm = new ConcurrentHashMap<>();
        ErrorCodeMapping errorCodeMapping = new ErrorCodeMapping();
        errorCodeMapping.setDeliveryStatus("UNDELIV");
        errorCodeMapping.setErrorCode(11);
        errorCodeMapping.setDeliveryErrorCode(11);
        List<ErrorCodeMapping> errorCodeMappings = List.of(errorCodeMapping);
        ecm.put("1", errorCodeMappings);
        this.smppConnectionManager = new SmppConnectionManager(
                jedisCluster,
                gateway,
                socketSession,
                ecm,
                routingRulesConcurrentHashMap,
                properties,
                cdrProcessor
        );

        // Handling noRetryErrorCode when errorCode is in the list
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 11));

        // Error Code Mapping not handled
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 12));

        // Origin Protocol is HTTP
        messageEvent.setOriginProtocol("HTTP");
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 12));

        // Origin Protocol SS7
        messageEvent.setOriginProtocol("SS7");
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 12));


        // Alternate Destination Error Code
        this.gateway.setRetryAlternateDestinationErrorCode("20,21,22,23");
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 20));

        // Alternative Routing Rule
        messageEvent.setRoutingId(1);
        messageEvent.setRetryDestNetworkId("1");
        String rr = "{\"id\":1,\"origin_network_id\":3,\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"is_sri_response\":false,\"destination\":[{\"priority\":1,\"network_id\":1,\"dest_protocol\":\"SMPP\",\"network_type\":\"GW\"},{\"priority\":2,\"network_id\":2,\"dest_protocol\":\"HTTP\",\"network_type\":\"GW\"}],\"new_source_addr\":\"\",\"new_source_addr_ton\":-1,\"new_source_addr_npi\":-1,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":-1,\"new_dest_addr_npi\":-1,\"add_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"check_sri_response\":false,\"origin_protocol\":\"SMPP\",\"origin_network_type\":\"SP\",\"has_filter_rules\":false,\"has_action_rules\":false}";
        RoutingRule routingRule = objectMapper.readValue(rr, RoutingRule.class);
        List<RoutingRule> routingRules = List.of(routingRule);
        ConcurrentMap<Integer, List<RoutingRule>> rrcm = new ConcurrentHashMap<>();
        rrcm.put(1, routingRules);
        this.smppConnectionManager = new SmppConnectionManager(
                jedisCluster,
                gateway,
                socketSession,
                ecm,
                rrcm,
                properties,
                cdrProcessor
        );

        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 20));

        // alternate for smpp
        messageEvent.setDestProtocol("SMPP");
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 20));

        // alternate for ss7
        messageEvent.setDestProtocol("SS7");
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 20));

        // alternate for unknown
        messageEvent.setDestProtocol("UNKNOWN");


        // Handling autoRetryErrorCode when errorCode is not in the list
        this.gateway.setAutoRetryErrorCode("64,65,66");
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 640));

        // Handling autoRetryErrorCode when errorCode is in the list
        assertDoesNotThrow(() -> this.smppConnectionManager.sendToRetryProcess(messageEvent, 65));
    }

    @Test
    void fetchAllItemsTest_sizeZero() {
        assertDoesNotThrow(() -> smppConnectionManager.fetchAllItems());
    }

    @Test
    void fetchAllItemsTest_sizeOne() {
        when(properties.getGatewaysWorkExecuteEvery()).thenReturn(1);
        when(jedisCluster.llen("1_smpp_message")).thenReturn(1L);
        when(this.properties.getWorkersPerGateway()).thenReturn(10);
        when(jedisCluster.lpop("1_smpp_message", 1)).thenReturn(List.of(
                "{\"msisdn\":null,\"id\":\"1719421854353-11028072268459\",\"message_id\":\"1719421854353-11028072268459\",\"system_id\":\"httpsp01\",\"deliver_sm_id\":null,\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":0,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"50510201020\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"50582368999\",\"esm_class\":0,\"validity_period\":\"60\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":0,\"short_message\":\"Prueba\",\"delivery_receipt\":null,\"status\":null,\"error_code\":null,\"check_submit_sm_response\":null,\"optional_parameters\":null,\"origin_network_type\":\"SP\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"GW\",\"dest_protocol\":\"HTTP\",\"dest_network_id\":1,\"routing_id\":1,\"address_nature_msisdn\":null,\"numbering_plan_msisdn\":null,\"remote_dialog_id\":null,\"local_dialog_id\":null,\"sccp_called_party_address_pc\":null,\"sccp_called_party_address_ssn\":null,\"sccp_called_party_address\":null,\"sccp_calling_party_address_pc\":null,\"sccp_calling_party_address_ssn\":null,\"sccp_calling_party_address\":null,\"global_title\":null,\"global_title_indicator\":null,\"translation_type\":null,\"smsc_ssn\":null,\"hlr_ssn\":null,\"msc_ssn\":null,\"map_version\":null,\"is_retry\":false,\"retry_dest_network_id\":null,\"retry_number\":null,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":0,\"accumulated_time\":0,\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"imsi\":null,\"network_node_number\":null,\"network_node_number_nature_of_address\":null,\"network_node_number_numbering_plan\":null,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":null,\"total_segment\":null,\"segment_sequence\":null,\"originator_sccp_address\":null,\"udhi\":null,\"udh_json\":null,\"parent_id\":null,\"is_dlr\":false,\"message_parts\":null}"
        ));
        assertDoesNotThrow(() -> smppConnectionManager.fetchAllItems());
    }

    @Test
    void connectTest() {
        assertDoesNotThrow(() -> smppConnectionManager.connect());
    }

    @Test
    void stopConnectionTest() {
        SMPPSession smppSession = new SMPPSession();
        this.smppConnectionManager.getSessions().add(smppSession);
        assertDoesNotThrow(() -> smppConnectionManager.stopConnection());
    }

    @Test
    void sendSubmitSmListTest() {
        messageEvent = new MessageEvent();
        messageEvent.setSystemId("systemId");
        messageEvent.setMessageId("pr1");
        messageEvent.setOriginProtocol("SMPP");
        messageEvent.setDestProtocol("SMPP");
        messageEvent.setOriginNetworkId(1);
        messageEvent.setDataCoding(0);

        var messageEvent1 = new MessageEvent();
        messageEvent1.setShortMessage("test");
        messageEvent1.setSystemId("systemId");
        messageEvent1.setMessageId("pr1");
        messageEvent1.setOriginProtocol("HTTP");
        messageEvent1.setDestProtocol("SMPP");
        messageEvent1.setOriginNetworkId(3);
        messageEvent1.setLastRetry(true);
        messageEvent1.setDataCoding(3);
        messageEvent1.setSourceAddrTon(1);
        messageEvent1.setSourceAddrNpi(0);
        messageEvent1.setSourceAddr("5555");
        messageEvent1.setDestAddrNpi(1);
        messageEvent1.setDestAddrTon(1);
        messageEvent1.setDestinationAddr("6666");
        messageEvent1.setEsmClass(1);
        messageEvent1.setRegisteredDelivery(1);

        List<MessagePart> messageParts =new ArrayList<>();
        MessagePart messagePart = new MessagePart();
        messagePart.setMessageId("1722365774740-13217874348222");
        messagePart.setShortMessage("La tecnologia avanza rapidamente transformando nuestras vidas");
        messagePart.setMsgReferenceNumber("1");
        messagePart.setSegmentSequence(1);
        messagePart.setTotalSegment(2);
        messagePart.setUdhJson("{\"message\":\"La tecnologia avanza rapidamente transformando nuestras vidas de maneras inimaginables. Cada innovacion trae nuevas oportunidades y desafios, obligandonos a ada\",\"0x00\":[1,2,1]}");

        MessagePart messagePart2 = new MessagePart();
        messagePart2.setMessageId("1722365774740-13217874348222");
        messagePart2.setShortMessage("La tecnologia avanza rapidamente transformando nuestras vidas");
        messagePart2.setMsgReferenceNumber("1");
        messagePart2.setSegmentSequence(2);
        messagePart2.setTotalSegment(2);
        messagePart2.setUdhJson("{\"message\":\"ptarnos continuamente. Mantenerse actualizado es esencial en este mundo cambiante.\",\"0x00\":[1,2,2]}");

        messageParts.add(messagePart);
        messageParts.add(messagePart2);

        messageEvent.setMessageParts(messageParts);
        List<MessageEvent> events = new ArrayList<>();
        events.add(messageEvent);
        events.add(messageEvent1);

        SMPPSession smppSession = new SMPPSession();
        smppConnectionManager.getSessions().add(smppSession);

        Assertions.assertDoesNotThrow(() -> smppConnectionManager.sendSubmitSmList(events));

        MessageEvent messageEvent2 = new MessageEvent();
        messageEvent2.setSystemId("systemId");
        messageEvent2.setMessageId("pr1");
        messageEvent2.setOriginProtocol("HTTP");
        messageEvent2.setDestProtocol("SMPP");
        messageEvent2.setOriginNetworkId(3);
        messageEvent2.setLastRetry(true);
        messageEvent2.setDataCoding(3);
        messageEvent2.setSourceAddrTon(1);
        messageEvent2.setSourceAddrNpi(0);
        messageEvent2.setSourceAddr("5555");
        messageEvent2.setDestAddrNpi(1);
        messageEvent2.setDestAddrTon(1);
        messageEvent2.setDestinationAddr("6666");
        messageEvent2.setEsmClass(1);
        messageEvent2.setRegisteredDelivery(1);
        messageEvent2.setMessageParts(messageParts);

        events.clear();
        events.add(messageEvent2);
        Assertions.assertDoesNotThrow(() -> smppConnectionManager.sendSubmitSmList(events));
    }
}