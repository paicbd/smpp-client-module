package com.paicbd.module.smpp;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.kafka.KafkaTopicsConstants;
import com.paicbd.smsc.scylla.ScyllaManager;
import com.paicbd.smsc.scylla.ScyllaTablesConstants;
import com.paicbd.smsc.utils.Converter;
import org.awaitility.Awaitility;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.EnquireLink;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageReceiverListenerImplTest {
    private static final String DEFAULT_SHORT_MESSAGE = "Hi from unit test!";
    private static final String HEX_DEL_RECEIPT_MESSAGE = "id:A5BDF10C sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message";
    private static final String HEX_DELIVER_SM_ID = "A5BDF10C";
    private static final String DEC_DELIVER_SM_ID = String.valueOf(Long.parseLong(HEX_DELIVER_SM_ID, 16));
    private static final String DEC_DEL_RECEIPT_MESSAGE = String.format("id:%s sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message", DEC_DELIVER_SM_ID);

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private MessageReceiverListenerImpl messageReceiverListener;

    @Mock
    private Gateway gatewayMock;

    @Mock
    private ScyllaManager scyllaManagerMock;

    @BeforeEach
    void setUp() {
        messageReceiverListener = new MessageReceiverListenerImpl(gatewayMock, kafkaTemplate, scyllaManagerMock, appProperties);
        gatewayMock = Gateway.builder()
                .networkId(1)
                .name("SMPP-Operator")
                .systemId("op_01_smpp_gw")
                .password("1234")
                .ip("127.0.0.1")
                .port(2779)
                .bindType("TRANSCEIVER")
                .requestDLR(1)
                .systemType("cmt")
                .interfaceVersion("IF_50")
                .sessionsNumber(10)
                .addressTON(0)
                .addressNPI(0)
                .addressRange(null)
                .tps(10)
                .successSession(0)
                .status("STARTED")
                .enabled(0)
                .enquireLinkPeriod(30000)
                .enquireLinkTimeout(0)
                .requestDLR(1)
                .mno(1)
                .tlvMessageReceiptId(true)
                .messageIdDecimalFormat(false)
                .protocol("SMPP")
                .autoRetryErrorCode("")
                .encodingIso88591(3)
                .encodingGsm7(0)
                .encodingUcs2(2)
                .splitMessage(false)
                .splitSmppType("TLV")
                .build();
        messageReceiverListener.setGateway(gatewayMock);
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when success mo message then deliverSm is emitted in kafka topic")
    void onAcceptDeliverSmWhenSuccessMoMessageThenDeliverIsEmittedInKafka() {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.DEFAULT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);
        ArgumentCaptor<MessageEvent> messageEventArgumentCaptor = ArgumentCaptor.forClass(MessageEvent.class);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(messageReceiverListenerSpy, atLeastOnce()).getDeliverSmEvent(deliverSmMock));
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> verify(messageReceiverListenerSpy, atLeastOnce()).publishDeliverSmToTopic(eq(deliverSmMock), messageEventArgumentCaptor.capture()));

        MessageEvent deliverSmCaptured = messageEventArgumentCaptor.getValue();

        assertEquals(DEFAULT_SHORT_MESSAGE, deliverSmCaptured.getShortMessage());
        assertEquals(0, deliverSmCaptured.getDestNetworkId());
        assertNull(deliverSmCaptured.getDestProtocol());
        assertNull(deliverSmCaptured.getDestNetworkType());
        assertNotNull(deliverSmCaptured.getOriginProtocol());
        assertNotNull(deliverSmCaptured.getOriginNetworkType());
        assertTrue(deliverSmCaptured.getOriginNetworkId() > 0);
        assertNotNull(deliverSmCaptured.getSmscMessagePriority());
        assertFalse(deliverSmCaptured.getCheckSubmitSmResponse());
        assertEquals(gatewayMock.getSystemId(), deliverSmCaptured.getSystemId());
        assertEquals(0, deliverSmCaptured.getRegisteredDelivery());
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when delivery receipt success with optional parameters then deliverSm is added to queue")
    void onAcceptDeliverSmWhenDeliverReceiptSuccessWithOptionalParametersThenDeliverIsAddedToQueue() {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(HEX_DEL_RECEIPT_MESSAGE.getBytes());

        OptionalParameter receiptMessageId = new OptionalParameter.Receipted_message_id(HEX_DELIVER_SM_ID);
        deliverSmMock.setOptionalParameters(receiptMessageId);
        when(appProperties.isSmppRemoveDlrTlvs()).thenReturn(true);
        MessageEvent submitResp = new MessageEvent();
        String submitRespJson = Converter.valueAsString(submitResp);

        when(scyllaManagerMock.selectFromTable(
                ScyllaTablesConstants.SMPP_SUBMIT_SM_RESULT_TABLE,
                HEX_DELIVER_SM_ID
        )).thenReturn(submitRespJson);

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);
        ArgumentCaptor<MessageEvent> messageEventArgumentCaptor = ArgumentCaptor.forClass(MessageEvent.class);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy).publishDeliverSmToTopic(eq(deliverSmMock), messageEventArgumentCaptor.capture());

        MessageEvent deliverSmCaptured = messageEventArgumentCaptor.getValue();
        verify(kafkaTemplate).send(anyString(), anyString());
        assertEquals(HEX_DEL_RECEIPT_MESSAGE, deliverSmCaptured.getShortMessage());
        assertEquals(0, deliverSmCaptured.getDestNetworkId());
        assertNull(deliverSmCaptured.getDestProtocol());
        assertNull(deliverSmCaptured.getDestNetworkType());
        assertNotNull(deliverSmCaptured.getOriginProtocol());
        assertNotNull(deliverSmCaptured.getOriginNetworkType());
        assertNull(deliverSmCaptured.getSmscMessagePriority());
        assertTrue(deliverSmCaptured.getOriginNetworkId() > 0);
        assertTrue(deliverSmCaptured.getCheckSubmitSmResponse());
        assertEquals(gatewayMock.getSystemId(), deliverSmCaptured.getSystemId());
        assertEquals(0, deliverSmCaptured.getRegisteredDelivery());
        assertEquals(0, deliverSmCaptured.getOptionalParameters().size()); // MessageId tlv will be always removed
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when delivery receipt success using Decimal Format without optional parameters then deliverSm is added to queue")
    void onAcceptDeliverSmWhenIsNotTlvUsingDecimalFormatThenDeliverIsAddedToQueue() {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(DEC_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(DEC_DEL_RECEIPT_MESSAGE.getBytes());

        gatewayMock.setMessageIdDecimalFormat(true);
        gatewayMock.setTlvMessageReceiptId(false);
        messageReceiverListener.setGateway(gatewayMock);

        MessageEvent submitResp = new MessageEvent();
        submitResp.setSplitForSmsc(false);
        String submitRespJson = Converter.valueAsString(submitResp);

        when(scyllaManagerMock.selectFromTable(
                ScyllaTablesConstants.SMPP_SUBMIT_SM_RESULT_TABLE,
                HEX_DELIVER_SM_ID
        )).thenReturn(submitRespJson);

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);
        ArgumentCaptor<MessageEvent> messageEventArgumentCaptor = ArgumentCaptor.forClass(MessageEvent.class);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy).publishDeliverSmToTopic(eq(deliverSmMock), messageEventArgumentCaptor.capture());

        verify(kafkaTemplate).send(anyString(), anyString());

        MessageEvent deliverSmCaptured = messageEventArgumentCaptor.getValue();
        assertEquals(DEC_DEL_RECEIPT_MESSAGE, deliverSmCaptured.getShortMessage());
        assertEquals(0, deliverSmCaptured.getDestNetworkId());
        assertNull(deliverSmCaptured.getDestProtocol());
        assertNull(deliverSmCaptured.getDestNetworkType());
        assertNotNull(deliverSmCaptured.getOriginProtocol());
        assertEquals("SMPP", deliverSmCaptured.getOriginProtocol());
        assertEquals("GW", deliverSmCaptured.getOriginNetworkType());
        assertNotNull(deliverSmCaptured.getOriginNetworkType());
        assertTrue(deliverSmCaptured.getOriginNetworkId() > 0);
        assertTrue(deliverSmCaptured.getCheckSubmitSmResponse());
        assertEquals(gatewayMock.getSystemId(), deliverSmCaptured.getSystemId());
        assertEquals(0, deliverSmCaptured.getRegisteredDelivery());
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when messageId must be in Optional Parameters but is not present then not added to queue")
    void onAcceptDeliverSmWhenMessageIdInOptionalParametersNotPresentThenNotAddedToQueue() {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(HEX_DEL_RECEIPT_MESSAGE.getBytes());

        when(scyllaManagerMock.selectFromTable(
                eq(ScyllaTablesConstants.SMPP_SUBMIT_SM_RESULT_TABLE),
                isNull()
        )).thenReturn(null);

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when invalid delivery receipt then not added to queue")
    void onAcceptDeliverSmWhenInvalidDeliveryReceiptThenNotAddedToQueue() {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(new byte[]{});

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy, never()).publishDeliverSmToTopic(any(DeliverSm.class), any(MessageEvent.class));
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when invalid ESMClass then not added to queue")
    void onAcceptDeliverSmWhenInvalidEsmClassThenNotAddedToQueue() {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.ESME_DEL_ACK.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy, never()).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy, never()).publishDeliverSmToTopic(any(DeliverSm.class), any(MessageEvent.class));
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x01, 0x02, 0x04, 0x05, 0x06, 0x07, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10})
    @DisplayName("OnAcceptSubmitSm when invalid data coding then throw process request exception")
    void onAcceptSubmitSmWhenInvalidDataCodingThenThrowProcessRequestException(byte dataCoding) {
        SubmitSm submitSm = new SubmitSm();
        submitSm.setDataCoding(dataCoding);
        submitSm.setValidityPeriod("000000000500000R");
        submitSm.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());

        try (SMPPSession session = new SMPPSession()) {
            var spy = spy(messageReceiverListener);
            assertDoesNotThrow(() -> spy.onAcceptSubmitSm(submitSm, session));
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(kafkaTemplate).send(keyCaptor.capture(), messageCaptor.capture());
            assertEquals(KafkaTopicsConstants.PRE_MESSAGE_MEDIUM_TOPIC, keyCaptor.getValue());

            MessageEvent messageEvent = Converter.stringToObject(messageCaptor.getValue(), MessageEvent.class);
            assertNotNull(messageEvent);

            assertEquals("GW", messageEvent.getOriginNetworkType());
            assertEquals("SMPP", messageEvent.getOriginProtocol());
        }
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x03, 0x08})
    @DisplayName("OnAcceptSubmitSm when valid data coding then add to queue")
    void onAcceptSubmitSmWhenValidDataCodingThenAddToQueue(byte dataCoding) {
        SubmitSm submitSm = new SubmitSm();
        submitSm.setDataCoding(dataCoding);
        submitSm.setValidityPeriod("000000000500000R");
        submitSm.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());

        SMPPSession session = mock(SMPPSession.class);
        var spy = spy(messageReceiverListener);
        spy.onAcceptSubmitSm(submitSm, session);
        verify(spy).addSubmitSmInQ(any(), any());
        verify(kafkaTemplate).send(anyString(), anyString());
        verify(spy).getSubmitSmEvent(any(), any());
        verify(spy).setDataToMessageEvent(any(), any());
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x03, 0x08})
    @DisplayName("OnAcceptSubmitSm when valid data coding then add to queue without require dlr")
    void onAcceptSubmitSmWhenIsValidDataCodingAndIsNotRequestingDlrThenAddToQueue(byte dataCoding) {
        gatewayMock.setRequestDLR(0);
        messageReceiverListener.setGateway(gatewayMock);

        SubmitSm submitSm = new SubmitSm();
        submitSm.setDataCoding(dataCoding);
        submitSm.setValidityPeriod("000000000500000R");
        submitSm.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());
        OptionalParameter receiptMessageId = new OptionalParameter.Receipted_message_id(HEX_DELIVER_SM_ID);
        submitSm.setOptionalParameters(receiptMessageId);

        SMPPSession session = mock(SMPPSession.class);
        var spy = spy(messageReceiverListener);
        spy.onAcceptSubmitSm(submitSm, session);
        verify(spy).addSubmitSmInQ(any(), any());
        verify(kafkaTemplate).send(anyString(), anyString());
        verify(spy).getSubmitSmEvent(any(), any());
        verify(spy).setDataToMessageEvent(any(), any());
    }

    @Test
    @DisplayName("OnAcceptEnquireLink then not thrown exception")
    void onAcceptEnquireLinkWhenReceiveEnquireLinkThenNotThrownException() {
        EnquireLink enquireLink = new EnquireLink();
        Session session = mock(Session.class);
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptEnquireLink(enquireLink, session));
    }

    @Test
    @DisplayName("OnAcceptDataSm then throw process request exception")
    void onAcceptDataSmWhenReceiveDataSmThenThrowProcessRequestException() {
        DataSm dataSm = new DataSm();
        Session session = mock(Session.class);
        assertThrows(ProcessRequestException.class, () -> messageReceiverListener.onAcceptDataSm(dataSm, session));
    }
}