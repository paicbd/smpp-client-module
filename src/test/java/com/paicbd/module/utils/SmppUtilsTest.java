package com.paicbd.module.utils;

import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.SmppEncoding;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameters;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"org.mockito.*"})
class SmppUtilsTest {

    @Test
    void determineEncodingType() {
        Gateway gateway = new Gateway();

        gateway.setEncodingGsm7(SmppEncoding.GSM7);
        int result1 = SmppUtils.determineEncodingType(SmppEncoding.DCS_0, gateway);
        assertEquals(SmppEncoding.GSM7, result1);

        gateway.setEncodingUcs2(SmppEncoding.UCS2);
        int result2 = SmppUtils.determineEncodingType(SmppEncoding.DCS_8, gateway);
        assertEquals(SmppEncoding.UCS2, result2);

        gateway.setEncodingIso88591(SmppEncoding.ISO88591);
        int result3 = SmppUtils.determineEncodingType(SmppEncoding.DCS_3, gateway);
        assertEquals(SmppEncoding.ISO88591, result3);

        assertThrows(IllegalStateException.class, () -> SmppUtils.determineEncodingType(99, gateway));
    }

    @Test
    void testPrivateConstructor() throws NoSuchMethodException {
        Constructor<SmppUtils> constructor = SmppUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(InvocationTargetException.class, constructor::newInstance);
    }

    @Test
    void getTLV_optionalParametersNull() {
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setOptionalParameters(null);
        OptionalParameter[] result = SmppUtils.getTLV(messageEvent);
        assertEquals(0, result.length);
    }

    @Test
    void getTLV_optionalParametersWithTagNull() {
        MessageEvent messageEvent = new MessageEvent();
        UtilsRecords.OptionalParameter op1 = new UtilsRecords.OptionalParameter((short) 1600, "1"); // NON_EXISTING_TAG
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(op1);
        messageEvent.setOptionalParameters(optionalParameters);
        OptionalParameter[] result = SmppUtils.getTLV(messageEvent);

        assertEquals(0, result.length);
    }

    @Test
    void getTLV() {
        MessageEvent messageEvent = new MessageEvent();
        UtilsRecords.OptionalParameter op1 = new UtilsRecords.OptionalParameter((short) 30, "1234567890");
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(op1);
        messageEvent.setOptionalParameters(optionalParameters);

        UtilsRecords.OptionalParameter[] expected = new UtilsRecords.OptionalParameter[1];
        expected[0] = new UtilsRecords.OptionalParameter((short) 30, "1234567890");

        OptionalParameter[] result = SmppUtils.getTLV(messageEvent);
        assertEquals(expected[0].tag(), result[0].tag);
        int totalLength = 4 + expected[0].value().length(); // 4 is the length of tag and length fields
        assertEquals(totalLength, result[0].serialize().length);
    }

    @Test
    void getTLV_ADDITIONAL_STATUS_INFO_TEXT() {
        MessageEvent messageEvent = new MessageEvent();
        UtilsRecords.OptionalParameter op1 = new UtilsRecords.OptionalParameter((short) 30, "123454"); // RECEIPTED_MESSAGE_ID
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(op1);
        messageEvent.setOptionalParameters(optionalParameters);
        UtilsRecords.OptionalParameter[] expected = new UtilsRecords.OptionalParameter[1];
        expected[0] = new UtilsRecords.OptionalParameter((short) 30, "123454");
        OptionalParameter[] result = SmppUtils.getTLV(messageEvent);
        assertEquals(expected[0].tag(), result[0].tag);
        int totalLength = 4 + expected[0].value().length();
        assertEquals(totalLength, result[0].serialize().length);
    }

    @Test
    void getTLV_ADDITIONAL_BROADCAST_REP_NUM() {
        MessageEvent messageEvent = new MessageEvent();
        UtilsRecords.OptionalParameter op1 = new UtilsRecords.OptionalParameter((short) 1540, "1"); // BROADCAST_FREQUENCY_INTERVAL
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(op1);
        messageEvent.setOptionalParameters(optionalParameters);

        UtilsRecords.OptionalParameter[] expected = new UtilsRecords.OptionalParameter[1];
        expected[0] = new UtilsRecords.OptionalParameter((short) 1540, "1");
        OptionalParameter[] result = SmppUtils.getTLV(messageEvent);
        assertEquals(expected[0].tag(), result[0].tag);
        int totalLength = 5 + expected[0].value().length();
        assertEquals(totalLength, result[0].serialize().length);
    }

    @Test
    void getTLV_NUMBER_OF_MESSAGES() {
        MessageEvent messageEvent = new MessageEvent();
        UtilsRecords.OptionalParameter op1 = new UtilsRecords.OptionalParameter((short) 772, "1"); // NUMBER_OF_MESSAGES
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(op1);
        messageEvent.setOptionalParameters(optionalParameters);

        UtilsRecords.OptionalParameter[] expected = new UtilsRecords.OptionalParameter[1];
        expected[0] = new UtilsRecords.OptionalParameter((short) 772, "1");
        OptionalParameter[] result = SmppUtils.getTLV(messageEvent);
        assertEquals(expected[0].tag(), result[0].tag);
        int totalLength = 4 + expected[0].value().length();
        assertEquals(totalLength, result[0].serialize().length);
    }

    @Test
    void getTLV_DEFAULT_CASE() {
        MessageEvent messageEvent = new MessageEvent();
        UtilsRecords.OptionalParameter op1 = new UtilsRecords.OptionalParameter((short) 23, "10"); // QOS_TIME_TO_LIVE
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(op1);
        messageEvent.setOptionalParameters(optionalParameters);

        UtilsRecords.OptionalParameter[] expected = new UtilsRecords.OptionalParameter[1];
        expected[0] = new UtilsRecords.OptionalParameter((short) 23, "10");
        OptionalParameter[] result = SmppUtils.getTLV(messageEvent);

        assertEquals(expected[0].tag(), result[0].tag);
    }

    @Test
    void getTLV_catchException() {
        MessageEvent messageEvent = new MessageEvent();
        String invalidValue = "notANumber";

        UtilsRecords.OptionalParameter op1 = new UtilsRecords.OptionalParameter((short) 23, invalidValue); // QOS_TIME_TO_LIVE
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(op1);
        messageEvent.setOptionalParameters(optionalParameters);
        assertThrows(IllegalStateException.class, () -> SmppUtils.getTLV(messageEvent));
    }

    @Test
    void setTLV() {
        OptionalParameter[] optionalParameters = new OptionalParameter[1];
        optionalParameters[0] = OptionalParameters.deserialize((short) 30, "1234567890".getBytes()); // DEST_ADDR_SUBUNIT
        List<UtilsRecords.OptionalParameter> result = SmppUtils.setTLV(optionalParameters);
        assertEquals(1, result.size());
    }

    @Test
    void testDeserializeTLV_1() throws Exception {
        assertEquals("1", invokeDeserializeTLV((short) 5, new byte[]{1})); // DEST_ADDR_SUBUNIT
        assertEquals("2", invokeDeserializeTLV((short) 6, new byte[]{2})); // DEST_NETWORK_TYPE
        assertEquals("3", invokeDeserializeTLV((short) 7, new byte[]{3})); // DEST_BEARER_TYPE
        assertEquals("5", invokeDeserializeTLV((short) 13, new byte[]{5})); // SOURCE_ADDR_SUBUNIT
        assertEquals("6", invokeDeserializeTLV((short) 14, new byte[]{6})); // SOURCE_NETWORK_TYPE
        assertEquals("7", invokeDeserializeTLV((short) 15, new byte[]{7})); // SOURCE_BEARER_TYPE
        assertEquals("8", invokeDeserializeTLV((short) 16, new byte[]{8})); // SOURCE_TELEMATICS_ID
        assertEquals("23", invokeDeserializeTLV((short) 23, ByteBuffer.allocate(4).putInt(23).array())); // QOS_TIME_TO_LIVE
        assertEquals("25", invokeDeserializeTLV((short) 25, new byte[]{25})); // PAYLOAD_TYPE
        assertEquals("clean", invokeDeserializeTLV((short) 29, "clean ".getBytes(StandardCharsets.UTF_8))); // ADDITIONAL_STATUS_INFO_TEXT
        assertEquals("abcd1234", invokeDeserializeTLV((short) 30, "abcd1234 ".getBytes(StandardCharsets.UTF_8))); // RECEIPTED_MESSAGE_ID
        assertEquals("48", invokeDeserializeTLV((short) 48, new byte[]{48})); // MS_MSG_WAIT_FACILITIES
        assertEquals("2", invokeDeserializeTLV((short) 513, new byte[]{2})); // PRIVACY_INDICATOR
        assertEquals("{", invokeDeserializeTLV((short) 514, new byte[]{123})); // SOURCE_SUBADDRESS
        assertEquals("{", invokeDeserializeTLV((short) 515, new byte[]{123})); // DEST_SUBADDRESS
        assertEquals("516", invokeDeserializeTLV((short) 516, ByteBuffer.allocate(2).putShort((short) 516).array())); // USER_MESSAGE_REFERENCE
        assertEquals("0", invokeDeserializeTLV((short) 517, ByteBuffer.allocate(2).putShort((short) 2).array())); // USER_RESPONSE_CODE
        assertEquals("2", invokeDeserializeTLV((short) 522, ByteBuffer.allocate(2).putShort((short) 2).array())); // SOURCE_PORT
    }

    @Test
    void testDeserializeTLV_2() throws Exception {
        assertEquals("2", invokeDeserializeTLV((short) 523, ByteBuffer.allocate(2).putShort((short) 2).array())); // DESTINATION_PORT
        assertEquals("2", invokeDeserializeTLV((short) 524, ByteBuffer.allocate(2).putShort((short) 2).array())); // SAR_MSG_REF_NUM
        assertEquals("1", invokeDeserializeTLV((short) 525, new byte[]{1})); // LANGUAGE_INDICATOR
        assertEquals("1", invokeDeserializeTLV((short) 526, new byte[]{1})); // SAR_TOTAL_SEGMENTS
        assertEquals("1", invokeDeserializeTLV((short) 527, new byte[]{1})); // SAR_SEGMENT_SEQNUM
        assertEquals("1", invokeDeserializeTLV((short) 528, new byte[]{1})); // SC_INTERFACE_VERSION
        assertEquals("1", invokeDeserializeTLV((short) 770, new byte[]{1})); // CALLBACK_NUM_PRES_IND
        assertEquals("d", invokeDeserializeTLV((short) 771, new byte[]{100})); // CALLBACK_NUM_ATAG
        assertEquals("1", invokeDeserializeTLV((short) 772, new byte[]{1})); // NUMBER_OF_MESSAGES
        assertEquals("d", invokeDeserializeTLV((short) 897, new byte[]{100})); // CALLBACK_NUM
        assertEquals("1", invokeDeserializeTLV((short) 1056, new byte[]{1})); // DPF_RESULT
        assertEquals("1", invokeDeserializeTLV((short) 1057, new byte[]{1})); // SET_DPF
        assertEquals("1", invokeDeserializeTLV((short) 1058, new byte[]{1})); // MS_AVAILABILITY_STATUS
        assertEquals("d", invokeDeserializeTLV((short) 1059, new byte[]{100})); // NETWORK_ERROR_CODE
        assertEquals("1060", invokeDeserializeTLV((short) 1060, "1060".getBytes(StandardCharsets.UTF_8))); // MESSAGE_PAYLOAD
        assertEquals("1", invokeDeserializeTLV((short) 1061, new byte[]{1})); // DELIVERY_FAILURE_REASON
        assertEquals("1", invokeDeserializeTLV((short) 1062, new byte[]{1})); // MORE_MESSAGES_TO_SEND
        assertEquals("1", invokeDeserializeTLV((short) 1063, new byte[]{1})); // MESSAGE_STATE
        assertEquals("1", invokeDeserializeTLV((short) 1064, new byte[]{1})); // CONGESTION_STATE
        assertEquals("1", invokeDeserializeTLV((short) 1281, new byte[]{1})); // USSD_SERVICE_OP
        assertEquals("1", invokeDeserializeTLV((short) 1536, new byte[]{1})); // BROADCAST_CHANNEL_INDICATOR
        assertEquals("d", invokeDeserializeTLV((short) 1537, new byte[]{100})); // BROADCAST_CONTENT_TYPE
    }

    @Test
    void testDeserializeTLV_3() throws Exception {
        assertEquals("d", invokeDeserializeTLV((short) 1538, new byte[]{100})); // BROADCAST_CONTENT_TYPE_INFO
        assertEquals("100", invokeDeserializeTLV((short) 1539, new byte[]{100})); // BROADCAST_MESSAGE_CLASS
        assertEquals("0", invokeDeserializeTLV((short) 1540, ByteBuffer.allocate(4).putInt(1540).array())); // BROADCAST_REP_NUM
        assertEquals("", invokeDeserializeTLV((short) -1, new byte[]{1})); // UNDEFINED
        assertEquals("i", invokeDeserializeTLV((short) 1541, new byte[]{105})); // BROADCAST_FREQUENCY_INTERVAL
        assertEquals("l", invokeDeserializeTLV((short) 1542, new byte[]{108})); // BROADCAST_AREA_IDENTIFIER
        assertEquals("109", invokeDeserializeTLV((short) 1543, new byte[]{109})); // BROADCAST_ERROR_STATUS
        assertEquals("110", invokeDeserializeTLV((short) 1544, new byte[]{110})); // BROADCAST_AREA_SUCCESS
        assertEquals("", invokeDeserializeTLV((short) 1545, new byte[]{111})); // BROADCAST_END_TIME
        assertEquals("p", invokeDeserializeTLV((short) 1546, new byte[]{112})); // BROADCAST_SERVICE_GROUP
        assertEquals("q", invokeDeserializeTLV((short) 1547, new byte[]{113})); // BILLING_IDENTIFICATION
        assertEquals("", invokeDeserializeTLV((short) 1548, new byte[]{114})); // SOURCE_NETWORK_ID
        assertEquals("", invokeDeserializeTLV((short) 1549, new byte[]{115})); // DEST_NETWORK_ID
        assertEquals("", invokeDeserializeTLV((short) 1550, new byte[]{116})); // SOURCE_NODE_ID
        assertEquals("u", invokeDeserializeTLV((short) 1551, new byte[]{117})); // DEST_NODE_ID
        assertEquals("v", invokeDeserializeTLV((short) 1552, new byte[]{118})); // DEST_ADDR_NP_RESOLUTION
        assertEquals("119", invokeDeserializeTLV((short) 1553, new byte[]{119})); // DEST_ADDR_NP_INFORMATION
        assertEquals("x", invokeDeserializeTLV((short) 1554, new byte[]{120})); // DEST_ADDR_NP_COUNTRY
        assertEquals("121", invokeDeserializeTLV((short) 4609, new byte[]{121})); // DISPLAY_TIME
        assertEquals("123", invokeDeserializeTLV((short) 4612, new byte[]{123})); // MS_VALIDITY
        assertEquals("124", invokeDeserializeTLV((short) 4876, new byte[]{124})); // ALERT_ON_MESSAGE_DELIVERY
        assertEquals("125", invokeDeserializeTLV((short) 4992, new byte[]{125})); // ITS_REPLY_TYPE
        assertEquals("d", invokeDeserializeTLV((short) 5377, new byte[]{100})); // VENDOR_SPECIFIC_SOURCE_MSC_ADDR
        assertEquals("d", invokeDeserializeTLV((short) 5378, new byte[]{100})); // VENDOR_SPECIFIC_DEST_MSC_ADDR
    }

    private static String invokeDeserializeTLV(short tagCode, byte[] content) throws Exception {
        Class<?> clazz = SmppUtils.class;
        Method method = clazz.getDeclaredMethod("deserializeTLV", short.class, byte[].class);
        method.setAccessible(true);
        return (String) method.invoke(null, tagCode, content);
    }
}