package com.paicbd.module.smpp;

import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.SubmitSmResponseEvent;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.EnquireLink;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.util.InvalidDeliveryReceiptException;
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
import redis.clients.jedis.JedisCluster;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageReceiverListenerImplTest {
    private static final String DEFAULT_SHORT_MESSAGE = "Hi from unit test!";
    private static final String HEX_DEL_RECEIPT_MESSAGE = "id:A5BDF10C sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message";
    private static final String HEX_DELIVER_SM_ID = "A5BDF10C";
    private static final String DEC_DELIVER_SM_ID = String.valueOf(Long.parseLong(HEX_DELIVER_SM_ID, 16));
    private static final String DEC_DEL_RECEIPT_MESSAGE = String.format("id:%s sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message", DEC_DELIVER_SM_ID);

    @Mock
    private Gateway gatewayMock;

    @Mock
    private JedisCluster jedisClusterMock;

    @Mock
    private CdrProcessor cdrProcessorMock;

    @InjectMocks
    private MessageReceiverListenerImpl messageReceiverListener;

    @BeforeEach
    public void setUp() {
        messageReceiverListener = new MessageReceiverListenerImpl(
                gatewayMock,
                jedisClusterMock,
                "preDeliver",
                "preMessage",
                "submitSmResult",
                cdrProcessorMock
        );
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
    @DisplayName("OnAcceptDeliverSm when error on lpush then deliverSm is not added to queue")
    void onAcceptDeliverSmWhenErrorOnLpushThenDeliverIsNotAddedToQueue() throws InvalidDeliveryReceiptException {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(HEX_DEL_RECEIPT_MESSAGE.getBytes());

        OptionalParameter receiptMessageId = new OptionalParameter.Receipted_message_id(HEX_DELIVER_SM_ID);
        deliverSmMock.setOptionalParameters(receiptMessageId);

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);
        when(jedisClusterMock.hget(anyString(), anyString())).thenThrow(new RuntimeException("Error on put message in queue"));

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy).addDeliverSmInQ(eq(deliverSmMock), any());
        verify(jedisClusterMock).hget(anyString(), anyString());
        verify(jedisClusterMock, never()).lpush(anyString(), anyString());
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when success mo message then deliverSm is added to queue")
    void onAcceptDeliverSmWhenSuccessMoMessageThenDeliverIsAddedToQueue() throws InvalidDeliveryReceiptException {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.DEFAULT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);
        ArgumentCaptor<MessageEvent> messageEventArgumentCaptor = ArgumentCaptor.forClass(MessageEvent.class);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy).addDeliverSmInQ(eq(deliverSmMock), messageEventArgumentCaptor.capture());

        MessageEvent deliverSmCaptured = messageEventArgumentCaptor.getValue();
        verify(jedisClusterMock, never()).hget(anyString(), anyString());

        assertEquals(DEFAULT_SHORT_MESSAGE, deliverSmCaptured.getShortMessage());
        assertEquals(0, deliverSmCaptured.getDestNetworkId());
        assertNull(deliverSmCaptured.getDestProtocol());
        assertNull(deliverSmCaptured.getDestNetworkType());
        assertNotNull(deliverSmCaptured.getOriginProtocol());
        assertNotNull(deliverSmCaptured.getOriginNetworkType());
        assertTrue(deliverSmCaptured.getOriginNetworkId() > 0);
        assertFalse(deliverSmCaptured.getCheckSubmitSmResponse());
        assertEquals(gatewayMock.getSystemId(), deliverSmCaptured.getSystemId());
        assertEquals(0, deliverSmCaptured.getRegisteredDelivery());
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when delivery receipt success with optional parameters then deliverSm is added to queue")
    void onAcceptDeliverSmWhenDeliverReceiptSuccessWithOptionalParametersThenDeliverIsAddedToQueue() throws InvalidDeliveryReceiptException {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(HEX_DEL_RECEIPT_MESSAGE.getBytes());

        OptionalParameter receiptMessageId = new OptionalParameter.Receipted_message_id(HEX_DELIVER_SM_ID);
        deliverSmMock.setOptionalParameters(receiptMessageId);

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);
        ArgumentCaptor<MessageEvent> messageEventArgumentCaptor = ArgumentCaptor.forClass(MessageEvent.class);

        SubmitSmResponseEvent submitSmResponseEvent = SubmitSmResponseEvent
                .builder()
                .submitSmServerId("1234")
                .hashId(HEX_DELIVER_SM_ID)
                .submitSmId(HEX_DELIVER_SM_ID)
                .originProtocol("SMPP")
                .originNetworkType("SP")
                .originNetworkId(4)
                .systemId("smppgw")
                .build();
        when(jedisClusterMock.hget("submitSmResult", HEX_DELIVER_SM_ID)).thenReturn(submitSmResponseEvent.toString());

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy).addDeliverSmInQ(eq(deliverSmMock), messageEventArgumentCaptor.capture());

        MessageEvent deliverSmCaptured = messageEventArgumentCaptor.getValue();
        verify(jedisClusterMock).hget(anyString(), anyString());
        assertEquals(HEX_DEL_RECEIPT_MESSAGE, deliverSmCaptured.getShortMessage());
        assertEquals(0, deliverSmCaptured.getDestNetworkId());
        assertNull(deliverSmCaptured.getDestProtocol());
        assertNull(deliverSmCaptured.getDestNetworkType());
        assertNotNull(deliverSmCaptured.getOriginProtocol());
        assertNotNull(deliverSmCaptured.getOriginNetworkType());
        assertTrue(deliverSmCaptured.getOriginNetworkId() > 0);
        assertTrue(deliverSmCaptured.getCheckSubmitSmResponse());
        assertEquals(gatewayMock.getSystemId(), deliverSmCaptured.getSystemId());
        assertEquals(0, deliverSmCaptured.getRegisteredDelivery());
        assertEquals(1, deliverSmCaptured.getOptionalParameters().size());

        assertEquals(submitSmResponseEvent.getSubmitSmServerId(), deliverSmCaptured.getMessageId());
        assertEquals(submitSmResponseEvent.getParentId(), deliverSmCaptured.getParentId());
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when delivery receipt success using Decimal Format without optional parameters then deliverSm is added to queue")
    void onAcceptDeliverSmWhenIsNotTlvUsingDecimalFormatThenDeliverIsAddedToQueue() throws InvalidDeliveryReceiptException {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(DEC_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(DEC_DEL_RECEIPT_MESSAGE.getBytes());

        gatewayMock.setMessageIdDecimalFormat(true);
        gatewayMock.setTlvMessageReceiptId(false);
        messageReceiverListener.setGateway(gatewayMock);

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);
        ArgumentCaptor<MessageEvent> messageEventArgumentCaptor = ArgumentCaptor.forClass(MessageEvent.class);

        SubmitSmResponseEvent submitSmResponseEvent = SubmitSmResponseEvent
                .builder()
                .submitSmServerId("1234")
                .hashId(DEC_DELIVER_SM_ID)
                .submitSmId(DEC_DELIVER_SM_ID)
                .originProtocol("SMPP")
                .originNetworkType("SP")
                .originNetworkId(4)
                .systemId("smppgw")
                .build();
        when(jedisClusterMock.hget("submitSmResult", HEX_DELIVER_SM_ID)).thenReturn(submitSmResponseEvent.toString());

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy).addDeliverSmInQ(eq(deliverSmMock), messageEventArgumentCaptor.capture());

        MessageEvent deliverSmCaptured = messageEventArgumentCaptor.getValue();
        verify(jedisClusterMock).hget(anyString(), anyString());
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

        assertEquals(submitSmResponseEvent.getSubmitSmServerId(), deliverSmCaptured.getMessageId());
        assertEquals(submitSmResponseEvent.getParentId(), deliverSmCaptured.getParentId());
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when delivery receipt success using Decimal Format and SubmitSmResult not present then deliverSm is not added to queue")
    void onAcceptDeliverSmWhenIsNotTlvUsingDecimalFormatAndSubmitSmResultNotPresentTheNotPutInRedis() throws InvalidDeliveryReceiptException {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(HEX_DEL_RECEIPT_MESSAGE.getBytes());

        OptionalParameter receiptMessageId = new OptionalParameter.Receipted_message_id(HEX_DELIVER_SM_ID);
        deliverSmMock.setOptionalParameters(receiptMessageId);

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);
        when(jedisClusterMock.hget("submitSmResult", HEX_DELIVER_SM_ID)).thenReturn(null);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy).addDeliverSmInQ(any(DeliverSm.class), any(MessageEvent.class));
        verify(jedisClusterMock, never()).lpush(anyString(), anyString());
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when messageId must be in Optional Parameters but is not present then not added to queue")
    void onAcceptDeliverSmWhenMessageIdInOptionalParametersNotPresentThenNotAddedToQueue() throws InvalidDeliveryReceiptException {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(HEX_DEL_RECEIPT_MESSAGE.getBytes());

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy).addDeliverSmInQ(any(DeliverSm.class), any(MessageEvent.class));

        verifyNoInteractions(jedisClusterMock);
        verifyNoInteractions(cdrProcessorMock);
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when invalid delivery receipt then not added to queue")
    void onAcceptDeliverSmWhenInvalidDeliveryReceiptThenNotAddedToQueue() throws InvalidDeliveryReceiptException {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy, never()).addDeliverSmInQ(any(DeliverSm.class), any(MessageEvent.class));

        verifyNoInteractions(jedisClusterMock);
        verifyNoInteractions(cdrProcessorMock);
    }

    @Test
    @DisplayName("OnAcceptDeliverSm when invalid ESMClass then not added to queue")
    void onAcceptDeliverSmWhenInvalidEsmClassThenNotAddedToQueue() throws InvalidDeliveryReceiptException {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.ESME_DEL_ACK.value());
        deliverSmMock.setId(HEX_DELIVER_SM_ID);
        deliverSmMock.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());

        MessageReceiverListenerImpl messageReceiverListenerSpy = spy(messageReceiverListener);

        messageReceiverListenerSpy.onAcceptDeliverSm(deliverSmMock);
        verify(messageReceiverListenerSpy, never()).getDeliverSmEvent(deliverSmMock);
        verify(messageReceiverListenerSpy, never()).addDeliverSmInQ(any(DeliverSm.class), any(MessageEvent.class));

        verifyNoInteractions(jedisClusterMock);
        verifyNoInteractions(cdrProcessorMock);
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
            assertThrows(ProcessRequestException.class, () -> spy.onAcceptSubmitSm(submitSm, session));

            verify(spy, never()).addDeliverSmInQ(any(), any());

            verifyNoInteractions(jedisClusterMock);
            verifyNoInteractions(cdrProcessorMock);
        }
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x03, 0x08})
    @DisplayName("OnAcceptSubmitSm when valid data coding then add to queue")
    void onAcceptSubmitSmWhenValidDataCodingThenAddToQueue(byte dataCoding) throws ProcessRequestException {
        SubmitSm submitSm = new SubmitSm();
        submitSm.setDataCoding(dataCoding);
        submitSm.setValidityPeriod("000000000500000R");
        submitSm.setShortMessage(DEFAULT_SHORT_MESSAGE.getBytes());

        SMPPSession session = mock(SMPPSession.class);
        var spy = spy(messageReceiverListener);
        spy.onAcceptSubmitSm(submitSm, session);
        verify(spy).addSubmitSmInQ(any(), any());
        verify(jedisClusterMock).lpush(anyString(), anyString());
        verify(spy).getSubmitSmEvent(any(), any());
        verify(spy).setDataToMessageEvent(any(), any());
    }

    @ParameterizedTest
    @ValueSource(bytes = {0x00, 0x03, 0x08})
    @DisplayName("OnAcceptSubmitSm when valid data coding then add to queue without require dlr")
    void onAcceptSubmitSmWhenIsValidDataCodingAndIsNotRequestingDlrThenAddToQueue(byte dataCoding) throws ProcessRequestException {
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
        verify(jedisClusterMock).lpush(anyString(), anyString());
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