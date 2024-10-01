package com.paicbd.module.smpp;

import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.SubmitSmResponseEvent;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.EnquireLink;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.MessageMode;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
class MessageReceiverListenerImplTest {

    @Mock
    private Gateway gatewayMock;

    @Mock
    private JedisCluster jedisClusterMock;

    @Mock
    private CdrProcessor cdrProcessorMock;

    @Mock
    private MessageEvent messageEventMock;

    @Mock
    private Session sessionMock;

    @Mock
    private SMPPSession smppSessionMock;

    @InjectMocks
    private MessageReceiverListenerImpl messageReceiverListener;

    @BeforeEach
    public void setUp() {
        messageReceiverListener = new MessageReceiverListenerImpl(
                gatewayMock,
                jedisClusterMock,
                "preDeliverQueue",
                "preMessageQueue",
                "submitSmResultQueue",
                cdrProcessorMock
        );

        gatewayMock = new Gateway();
        gatewayMock.setNetworkId(1);
        gatewayMock.setName("smppgw");
        gatewayMock.setSystemId("systemId");
        gatewayMock.setPassword("password");
        gatewayMock.setIp("192.168.100.20");
        gatewayMock.setPort(7001);
        gatewayMock.setBindType("TRANSCEIVER");
        gatewayMock.setSystemType("");
        gatewayMock.setInterfaceVersion("IF_50");
        gatewayMock.setSessionsNumber(10);
        gatewayMock.setAddressTON(0);
        gatewayMock.setAddressNPI(0);
        gatewayMock.setAddressRange(null);
        gatewayMock.setTps(10);
        gatewayMock.setStatus("STARTED");
        gatewayMock.setEnabled(0);
        gatewayMock.setEnquireLinkPeriod(30000);
        gatewayMock.setEnquireLinkTimeout(0);
        gatewayMock.setRequestDLR(true);
        gatewayMock.setNoRetryErrorCode("");
        gatewayMock.setRetryAlternateDestinationErrorCode("");
        gatewayMock.setBindTimeout(5000);
        gatewayMock.setBindRetryPeriod(10000);
        gatewayMock.setPduTimeout(5000);
        gatewayMock.setPduProcessorDegree(1);
        gatewayMock.setThreadPoolSize(100);
        gatewayMock.setMno(1);
        gatewayMock.setTlvMessageReceiptId(false);
        gatewayMock.setMessageIdDecimalFormat(false);
        gatewayMock.setProtocol("SMPP");
        gatewayMock.setAutoRetryErrorCode("");
        gatewayMock.setEncodingIso88591(3);
        gatewayMock.setEncodingGsm7(0);
        gatewayMock.setEncodingUcs2(2);
        gatewayMock.setSplitMessage(false);
        gatewayMock.setSplitSmppType("TLV");
        
        messageReceiverListener.setGateway(gatewayMock);
    }

    @Test
    void testOnAcceptDeliverSm_WithValidDeliveryReceipt() {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.SMSC_DEL_RECEIPT.value());
        deliverSmMock.setId("1");
        deliverSmMock.setShortMessage("id:1 sub:001 dlvrd:001 submit date:2101010000 done date:2101010000 stat:DELIVRD err:000 text:Test Message".getBytes());
        deliverSmMock.setSmscDeliveryReceipt();
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptDeliverSm(deliverSmMock));

        // Gateway via TLV but not present
        gatewayMock.setTlvMessageReceiptId(true);
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptDeliverSm(deliverSmMock));

        // Gateway via TLV and present
        // Create Optional Parameter for Message Receipt ID
        OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
        deliverSmMock.setOptionalParameters(new OptionalParameter[]{messageReceiptId});
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptDeliverSm(deliverSmMock));

        // MessageId in Decimal Format
        gatewayMock.setMessageIdDecimalFormat(true);
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptDeliverSm(deliverSmMock));

        SubmitSmResponseEvent submitSmResponseEvent = new SubmitSmResponseEvent();
        submitSmResponseEvent.setSubmitSmServerId("123455");
        Mockito.when(jedisClusterMock.hget(anyString(), anyString())).thenReturn(submitSmResponseEvent.toString());
        gatewayMock.setTlvMessageReceiptId(false);
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptDeliverSm(deliverSmMock));
    }

    @Test
    void testGetDeliverSmEvent_WithInvalidMessageType() {
        DeliverSm deliverSmMock = new DeliverSm();
        deliverSmMock.setEsmClass(MessageType.DEFAULT.value());
        deliverSmMock.setId("1");
        deliverSmMock.setShortMessage("id:1 sub:001 dlvrd:001 submit date:2101010000lk done date:2101010000 stat:DELIVRD err:000 text:Test Message".getBytes());
        deliverSmMock.setSmscDeliveryReceipt();
        assertThrows(InvocationTargetException.class, () -> invokeGetDeliverSmEvent(messageReceiverListener, deliverSmMock));
    }

    @Test
    void onAcceptAlertNotification() {
        AlertNotification alertNotificationMock = new AlertNotification();
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptAlertNotification(alertNotificationMock));
    }

    @Test
    void onAcceptDataSm() {
        DataSm dataSmMock = new DataSm();
        assertThrows(ProcessRequestException.class, () -> messageReceiverListener.onAcceptDataSm(dataSmMock, sessionMock));
    }

    @Test
    void onAcceptSubmitSm() {
        // Test Invalid Data Coding
        SubmitSm ss = new SubmitSm();
        ss.setDataCoding((byte) 0x12);
        ss.setDataCoding((byte) 0x12);
        ss.setShortMessage("Test Message".getBytes());
        ss.setDestAddress("1234567890");
        ss.setSourceAddr("1234567890");
        ss.setDestAddrTon((byte) 0x01);
        ss.setDestAddrNpi((byte) 0x01);
        ss.setSourceAddrTon((byte) 0x01);
        ss.setSourceAddrNpi((byte) 0x01);

        assertThrows(ProcessRequestException.class, () -> messageReceiverListener.onAcceptSubmitSm(ss, smppSessionMock));

        // Test Valid Data Coding
        ss.setDataCoding((byte) 0x00);
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptSubmitSm(ss, smppSessionMock));

        // Test with Optional Parameter
        OptionalParameter messageReceiptId = new OptionalParameter.Receipted_message_id("1");
        ss.setOptionalParameters(messageReceiptId);
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptSubmitSm(ss, smppSessionMock));
    }

    @Test
    void getDeliverSm_MoMessage() {
        DeliverSm deliverSmMock = new DeliverSm();
        ESMClass esmClass = new ESMClass(MessageMode.STORE_AND_FORWARD, MessageType.DEFAULT, GSMSpecificFeature.DEFAULT);
        deliverSmMock.setEsmClass(esmClass.value());
        deliverSmMock.setId("1");
        deliverSmMock.setShortMessage("Test MO Message".getBytes());
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptDeliverSm(deliverSmMock));

        // Deliver SM mo with optional parameter
        OptionalParameter additionalStatusInfoText = new OptionalParameter.Additional_status_info_text("This is additional status info text");
        deliverSmMock.setOptionalParameters(additionalStatusInfoText);
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptDeliverSm(deliverSmMock));
    }

    @Test
    void onAcceptEnquireLink() {
        EnquireLink enquireLinkMock = new EnquireLink();
        assertDoesNotThrow(() -> messageReceiverListener.onAcceptEnquireLink(enquireLinkMock, sessionMock));
    }

    private static void invokeGetDeliverSmEvent(MessageReceiverListenerImpl listener, DeliverSm deliverSm) throws Exception {
        Class<?> clazz = MessageReceiverListenerImpl.class;
        Method method = clazz.getDeclaredMethod("getDeliverSmEvent", DeliverSm.class);
        method.setAccessible(true);
        method.invoke(listener, deliverSm);
    }
}