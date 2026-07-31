package com.paicbd.module.utils;

import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Generated;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.util.DeliveryReceiptState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class Utils {
    @Generated
    private Utils() {
        throw new IllegalStateException("Utility class");
    }

    public static DeliveryReceiptState mapDeliveryReceiptState(int decimalValue) {
        return switch (decimalValue) {
            case 1 -> DeliveryReceiptState.ENROUTE;
            case 2 -> DeliveryReceiptState.DELIVRD;
            case 3 -> DeliveryReceiptState.EXPIRED;
            case 4 -> DeliveryReceiptState.DELETED;
            case 5 -> DeliveryReceiptState.UNDELIV;
            case 6 -> DeliveryReceiptState.ACCEPTD;
            case 8 -> DeliveryReceiptState.REJECTD;
            default -> DeliveryReceiptState.UNKNOWN;
        };
    }

    public static void addConcatenatedTlvSmsDetails(MessageEvent messageEvent) {
        if (messageEvent.getOptionalParameters() == null) {
            messageEvent.setOptionalParameters(new ArrayList<>());
        }

        var sarReferenceNumber = new UtilsRecords.OptionalParameter(
                OptionalParameter.Tag.SAR_MSG_REF_NUM.code(), messageEvent.getMsgReferenceNumber());
        var sarTotalSegments = new UtilsRecords.OptionalParameter(
                OptionalParameter.Tag.SAR_TOTAL_SEGMENTS.code(), String.valueOf(messageEvent.getTotalSegment()));
        var sarSequenceNumber = new UtilsRecords.OptionalParameter(
                OptionalParameter.Tag.SAR_SEGMENT_SEQNUM.code(), String.valueOf(messageEvent.getSegmentSequence()));

        messageEvent.getOptionalParameters()
                .addAll(Arrays.asList(sarReferenceNumber, sarTotalSegments, sarSequenceNumber));
    }

    public static boolean optParamsContainsConcatenationTlv(MessageEvent messageEvent) {
        if (Objects.isNull(messageEvent.getOptionalParameters())) {
            return false;
        }

        return messageEvent.getOptionalParameters().stream()
                .anyMatch(optionalParameter ->
                        optionalParameter.tag() == OptionalParameter.Tag.SAR_MSG_REF_NUM.code()
                        || optionalParameter.tag() == OptionalParameter.Tag.SAR_TOTAL_SEGMENTS.code()
                        || optionalParameter.tag() == OptionalParameter.Tag.SAR_SEGMENT_SEQNUM.code());
    }

    public static boolean containsMessagePayloadTlv(MessageEvent messageEvent) {
        if (Objects.isNull(messageEvent.getOptionalParameters())) {
            return false;
        }

        return messageEvent.getOptionalParameters().stream()
                .anyMatch(optionalParameter ->
                        optionalParameter.tag() == OptionalParameter.Tag.MESSAGE_PAYLOAD.code());
    }
}
