package com.paicbd.module.utils;

import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.util.DeliveryReceiptState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UtilsTest {

    @Mock
    private MessageEvent mockMessageEvent;

    @Captor
    private ArgumentCaptor<List<UtilsRecords.OptionalParameter>> optionalParametersCaptor;

    @Test
    void testMapDeliveryReceiptState_shouldReturnEnrouteForValue1() {
        assertEquals(DeliveryReceiptState.ENROUTE, Utils.mapDeliveryReceiptState(1));
    }

    @Test
    void testMapDeliveryReceiptState_shouldReturnDelivrdForValue2() {
        assertEquals(DeliveryReceiptState.DELIVRD, Utils.mapDeliveryReceiptState(2));
    }

    @Test
    void testMapDeliveryReceiptState_shouldReturnExpiredForValue3() {
        assertEquals(DeliveryReceiptState.EXPIRED, Utils.mapDeliveryReceiptState(3));
    }

    @Test
    void testMapDeliveryReceiptState_shouldReturnDeletedForValue4() {
        assertEquals(DeliveryReceiptState.DELETED, Utils.mapDeliveryReceiptState(4));
    }

    @Test
    void testMapDeliveryReceiptState_shouldReturnUndelivForValue5() {
        assertEquals(DeliveryReceiptState.UNDELIV, Utils.mapDeliveryReceiptState(5));
    }

    @Test
    void testMapDeliveryReceiptState_shouldReturnAcceptdForValue6() {
        assertEquals(DeliveryReceiptState.ACCEPTD, Utils.mapDeliveryReceiptState(6));
    }

    @Test
    void testMapDeliveryReceiptState_shouldReturnRejectdForValue8() {
        assertEquals(DeliveryReceiptState.REJECTD, Utils.mapDeliveryReceiptState(8));
    }

    @Test
    void testMapDeliveryReceiptState_shouldReturnUnknownForDefaultValue() {
        assertEquals(DeliveryReceiptState.UNKNOWN, Utils.mapDeliveryReceiptState(99));
    }

    @Test
    void testAddConcatenatedTlvSmsDetails_shouldAddParametersToExistingList() {
        var existingParameters = new ArrayList<UtilsRecords.OptionalParameter>();
        when(mockMessageEvent.getOptionalParameters()).thenReturn(existingParameters);
        when(mockMessageEvent.getMsgReferenceNumber()).thenReturn("12345");
        when(mockMessageEvent.getTotalSegment()).thenReturn(10);
        when(mockMessageEvent.getSegmentSequence()).thenReturn(1);

        Utils.addConcatenatedTlvSmsDetails(mockMessageEvent);

        verify(mockMessageEvent, never()).setOptionalParameters(any());
        assertEquals(3, existingParameters.size());

        assertEquals(OptionalParameter.Tag.SAR_MSG_REF_NUM.code(), existingParameters.get(0).tag());
        assertEquals("12345", existingParameters.get(0).value());

        assertEquals(OptionalParameter.Tag.SAR_TOTAL_SEGMENTS.code(), existingParameters.get(1).tag());
        assertEquals("10", existingParameters.get(1).value());

        assertEquals(OptionalParameter.Tag.SAR_SEGMENT_SEQNUM.code(), existingParameters.get(2).tag());
        assertEquals("1", existingParameters.get(2).value());
    }

    @Test
    void testAddConcatenatedTlvSmsDetails_shouldInitializeAndAddParametersWhenListIsNull() {
        mockMessageEvent.setOptionalParameters(new ArrayList<>());
        when(mockMessageEvent.getMsgReferenceNumber()).thenReturn("54321");
        when(mockMessageEvent.getTotalSegment()).thenReturn(5);
        when(mockMessageEvent.getSegmentSequence()).thenReturn(2);

        Utils.addConcatenatedTlvSmsDetails(mockMessageEvent);

        verify(mockMessageEvent).setOptionalParameters(optionalParametersCaptor.capture());
        var newParametersList = optionalParametersCaptor.getValue();

        assertNotNull(newParametersList);
    }

    @Test
    void testOptParamsContainsConcatenationTlv_shouldReturnTrueWhenSarMsgRefNumIsPresent() {
        var optionalParameters = Arrays.asList(
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.MESSAGE_PAYLOAD.code(), "value"),
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.SAR_MSG_REF_NUM.code(), "ref")
        );
        when(mockMessageEvent.getOptionalParameters()).thenReturn(optionalParameters);

        assertTrue(Utils.optParamsContainsConcatenationTlv(mockMessageEvent));
    }

    @Test
    void testOptParamsContainsConcatenationTlv_shouldReturnTrueWhenSarTotalSegmentsIsPresent() {
        var optionalParameters = Arrays.asList(
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.MESSAGE_PAYLOAD.code(), "value"),
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.SAR_TOTAL_SEGMENTS.code(), "ref")
        );
        when(mockMessageEvent.getOptionalParameters()).thenReturn(optionalParameters);
        assertTrue(Utils.optParamsContainsConcatenationTlv(mockMessageEvent));
    }

    @Test
    void testOptParamsContainsConcatenationTlv_shouldReturnTrueWhenSarSegmentSeqNumIsPresent() {
        var optionalParameters = Arrays.asList(
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.MESSAGE_PAYLOAD.code(), "value"),
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.SAR_SEGMENT_SEQNUM.code(), "ref")
        );
        when(mockMessageEvent.getOptionalParameters()).thenReturn(optionalParameters);
        assertTrue(Utils.optParamsContainsConcatenationTlv(mockMessageEvent));
    }

    @Test
    void testOptParamsContainsConcatenationTlv_shouldReturnFalseWhenNoConcatenationTlvIsPresent() {
        var optionalParameters = Arrays.asList(
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.MESSAGE_PAYLOAD.code(), "value"),
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.RECEIPTED_MESSAGE_ID.code(), "value")
        );
        when(mockMessageEvent.getOptionalParameters()).thenReturn(optionalParameters);

        assertFalse(Utils.optParamsContainsConcatenationTlv(mockMessageEvent));
    }

    @Test
    void testOptParamsContainsConcatenationTlv_shouldReturnFalseWhenOptionalParametersAreNull() {
        when(mockMessageEvent.getOptionalParameters()).thenReturn(null);

        assertFalse(Utils.optParamsContainsConcatenationTlv(mockMessageEvent));
    }

    @Test
    void testContainsMessagePayloadTlv_shouldReturnTrueWhenMessagePayloadIsPresent() {
        var optionalParameters = Arrays.asList(
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.MESSAGE_STATE.code(), "1"),
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.MESSAGE_PAYLOAD.code(), "payload")
        );
        when(mockMessageEvent.getOptionalParameters()).thenReturn(optionalParameters);

        assertTrue(Utils.containsMessagePayloadTlv(mockMessageEvent));
    }

    @Test
    void testContainsMessagePayloadTlv_shouldReturnFalseWhenMessagePayloadIsNotPresent() {
        var optionalParameters = List.of(
                new UtilsRecords.OptionalParameter(OptionalParameter.Tag.MESSAGE_STATE.code(), "1")
        );
        when(mockMessageEvent.getOptionalParameters()).thenReturn(optionalParameters);

        assertFalse(Utils.containsMessagePayloadTlv(mockMessageEvent));
    }

    @Test
    void testContainsMessagePayloadTlv_shouldReturnFalseWhenOptionalParametersAreNull() {
        when(mockMessageEvent.getOptionalParameters()).thenReturn(null);
        assertFalse(Utils.containsMessagePayloadTlv(mockMessageEvent));
    }
}