package com.paicbd.module.utils;

import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameters;
import org.jsmpp.util.HexUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.paicbd.smsc.utils.SmppEncoding.DCS_0;
import static com.paicbd.smsc.utils.SmppEncoding.DCS_3;
import static com.paicbd.smsc.utils.SmppEncoding.DCS_8;

@Slf4j
public class SmppUtils {
    private SmppUtils() {
        throw new IllegalStateException("Utility Class");
    }

    public static int determineEncodingType(int encodingType, Gateway gateway) {
        return switch (encodingType) {
            case DCS_0 -> gateway.getEncodingGsm7();
            case DCS_8 -> gateway.getEncodingUcs2();
            case DCS_3 -> gateway.getEncodingIso88591();
            default -> throw new IllegalStateException("Unexpected value when determining encoding type: " + encodingType);
        };
    }

    private static String deserializeTLV(short tagCode, byte[] content) {
        OptionalParameter.Tag tag = OptionalParameter.Tag.valueOf(tagCode);
        if (tag == null) {
            log.warn("Optional Parameter Tag not recognized for deserialization on deserializeTLV: {}", tagCode);
            return "";
        }

        return switch (tag) {
            case DEST_ADDR_SUBUNIT -> Byte.toString(new OptionalParameter.Dest_addr_subunit(content).getValue());
            case DEST_NETWORK_TYPE -> Byte.toString(new OptionalParameter.Dest_network_type(content).getValue());
            case DEST_BEARER_TYPE -> Byte.toString(new OptionalParameter.Dest_bearer_type(content).getValue());
            case DEST_TELEMATICS_ID -> new OptionalParameter.Dest_telematics_id(content).getValue() + "";
            case SOURCE_ADDR_SUBUNIT -> Byte.toString(new OptionalParameter.Source_addr_subunit(content).getValue());
            case SOURCE_NETWORK_TYPE -> Byte.toString(new OptionalParameter.Source_network_type(content).getValue());
            case SOURCE_BEARER_TYPE -> Byte.toString(new OptionalParameter.Source_bearer_type(content).getValue());
            case SOURCE_TELEMATICS_ID -> Byte.toString(new OptionalParameter.Source_telematics_id(content).getValue());
            case QOS_TIME_TO_LIVE -> new OptionalParameter.Qos_time_to_live(content).getValue() + "";
            case PAYLOAD_TYPE -> Byte.toString(new OptionalParameter.Payload_type(content).getValue());
            case ADDITIONAL_STATUS_INFO_TEXT ->
                    new OptionalParameter.Additional_status_info_text(content).getValueAsString();
            case RECEIPTED_MESSAGE_ID -> new OptionalParameter.Receipted_message_id(content).getValueAsString();
            case MS_MSG_WAIT_FACILITIES ->
                    Byte.toString(new OptionalParameter.Ms_msg_wait_facilities(content).getValue());
            case PRIVACY_INDICATOR -> Byte.toString(new OptionalParameter.Privacy_indicator(content).getValue());
            case SOURCE_SUBADDRESS -> new OptionalParameter.Source_subaddress(content).getValueAsString();
            case DEST_SUBADDRESS -> new OptionalParameter.Dest_subaddress(content).getValueAsString();
            case USER_MESSAGE_REFERENCE -> new OptionalParameter.User_message_reference(content).getValue() + "";
            case USER_RESPONSE_CODE -> Byte.toString(new OptionalParameter.User_response_code(content).getValue());
            case SOURCE_PORT -> new OptionalParameter.Source_port(content).getValue() + "";
            case DESTINATION_PORT -> new OptionalParameter.Destination_port(content).getValue() + "";
            case SAR_MSG_REF_NUM -> new OptionalParameter.Sar_msg_ref_num(content).getValue() + "";
            case LANGUAGE_INDICATOR -> Byte.toString(new OptionalParameter.Language_indicator(content).getValue());
            case SAR_TOTAL_SEGMENTS -> Byte.toString(new OptionalParameter.Sar_total_segments(content).getValue());
            case SAR_SEGMENT_SEQNUM -> Byte.toString(new OptionalParameter.Sar_segment_seqnum(content).getValue());
            case SC_INTERFACE_VERSION -> Byte.toString(new OptionalParameter.Sc_interface_version(content).getValue());
            case CALLBACK_NUM_PRES_IND ->
                    Byte.toString(new OptionalParameter.Callback_num_pres_ind(content).getValue());
            case CALLBACK_NUM_ATAG -> new OptionalParameter.Callback_num_atag(content).getValueAsString();
            case NUMBER_OF_MESSAGES -> Byte.toString(new OptionalParameter.Number_of_messages(content).getValue());
            case CALLBACK_NUM -> new OptionalParameter.Callback_num(content).getValueAsString();
            case DPF_RESULT -> Byte.toString(new OptionalParameter.Dpf_result(content).getValue());
            case SET_DPF -> Byte.toString(new OptionalParameter.Set_dpf(content).getValue());
            case MS_AVAILABILITY_STATUS ->
                    Byte.toString(new OptionalParameter.Ms_availability_status(content).getValue());
            case NETWORK_ERROR_CODE -> new OptionalParameter.Network_error_code(content).getValueAsString();
            case MESSAGE_PAYLOAD -> new OptionalParameter.Message_payload(content).getValueAsString();
            case DELIVERY_FAILURE_REASON ->
                    Byte.toString(new OptionalParameter.Delivery_failure_reason(content).getValue());
            case MORE_MESSAGES_TO_SEND ->
                    Byte.toString(new OptionalParameter.More_messages_to_send(content).getValue());
            case MESSAGE_STATE -> Byte.toString(new OptionalParameter.Message_state(content).getValue());
            case CONGESTION_STATE -> Byte.toString(new OptionalParameter.Congestion_state(content).getValue());
            case USSD_SERVICE_OP -> Byte.toString(new OptionalParameter.Ussd_service_op(content).getValue());
            case BROADCAST_CHANNEL_INDICATOR ->
                    Byte.toString(new OptionalParameter.Broadcast_channel_indicator(content).getValue());
            case BROADCAST_CONTENT_TYPE -> new OptionalParameter.Broadcast_content_type(content).getValueAsString();
            case BROADCAST_CONTENT_TYPE_INFO ->
                    new OptionalParameter.Broadcast_content_type_info(content).getValueAsString();
            case BROADCAST_MESSAGE_CLASS ->
                    Byte.toString(new OptionalParameter.Broadcast_message_class(content).getValue());
            case BROADCAST_REP_NUM -> new OptionalParameter.Broadcast_rep_num(content).getValue() + "";
            case BROADCAST_FREQUENCY_INTERVAL ->
                    new OptionalParameter.Broadcast_frequency_interval(content).getValueAsString();
            case BROADCAST_AREA_IDENTIFIER ->
                    new OptionalParameter.Broadcast_area_identifier(content).getValueAsString();
            case BROADCAST_ERROR_STATUS -> new OptionalParameter.Broadcast_error_status(content).getValue() + "";
            case BROADCAST_AREA_SUCCESS ->
                    Byte.toString(new OptionalParameter.Broadcast_area_success(content).getValue());
            case BROADCAST_END_TIME -> new OptionalParameter.Broadcast_end_time(content).getValueAsString();
            case BROADCAST_SERVICE_GROUP -> new OptionalParameter.Broadcast_service_group(content).getValueAsString();
            case BILLING_IDENTIFICATION -> new OptionalParameter.Billing_identification(content).getValueAsString();
            case SOURCE_NETWORK_ID -> new OptionalParameter.Source_network_id(content).getValueAsString();
            case DEST_NETWORK_ID -> new OptionalParameter.Dest_network_id(content).getValueAsString();
            case SOURCE_NODE_ID -> new OptionalParameter.Source_node_id(content).getValueAsString();
            case DEST_NODE_ID -> new OptionalParameter.Dest_node_id(content).getValueAsString();
            case DEST_ADDR_NP_RESOLUTION ->
                    Byte.toString(new OptionalParameter.Dest_addr_np_resolution(content).getValue());
            case DEST_ADDR_NP_INFORMATION -> new OptionalParameter.Dest_addr_np_information(content).getValueAsString();
            case DEST_ADDR_NP_COUNTRY -> new OptionalParameter.Dest_addr_np_country(content).getValueAsString();
            case DISPLAY_TIME -> Byte.toString(new OptionalParameter.Display_time(content).getValue());
            case SMS_SIGNAL -> new OptionalParameter.Sms_signal(content).getValue() + "";
            case MS_VALIDITY -> Byte.toString(new OptionalParameter.Ms_validity(content).getValue());
            case ALERT_ON_MESSAGE_DELIVERY ->
                    Byte.toString(new OptionalParameter.Alert_on_message_delivery(content).getValue());
            case ITS_REPLY_TYPE -> Byte.toString(new OptionalParameter.Its_reply_type(content).getValue());
            case ITS_SESSION_INFO -> new OptionalParameter.Its_session_info(content).getValue() + "";
            case VENDOR_SPECIFIC_SOURCE_MSC_ADDR ->
                    new OptionalParameter.Vendor_specific_source_msc_addr(content).getValueAsString();
            case VENDOR_SPECIFIC_DEST_MSC_ADDR ->
                    new OptionalParameter.Vendor_specific_dest_msc_addr(content).getValueAsString();
        };
    }

    public static OptionalParameter[] getTLV(MessageEvent smppMessage) {
        List<OptionalParameter> optionalParameters = new ArrayList<>();
        try {
            if (smppMessage.getOptionalParameters() != null) {
                smppMessage.getOptionalParameters().forEach(tlv -> {
                    OptionalParameter.Tag tag = OptionalParameter.Tag.valueOf(tlv.tag());
                    if (tag == null) {
                        log.warn("Optional Parameter Tag not recognized for deserialization on getTLV: {}", tlv.tag());
                    } else {
                        switch (tag) {
                            case ADDITIONAL_STATUS_INFO_TEXT,
                                 RECEIPTED_MESSAGE_ID,
                                 BROADCAST_END_TIME,
                                 DEST_NETWORK_ID,
                                 SOURCE_NETWORK_ID,
                                 BROADCAST_AREA_IDENTIFIER,
                                 BROADCAST_CONTENT_TYPE,
                                 BROADCAST_CONTENT_TYPE_INFO,
                                 BROADCAST_FREQUENCY_INTERVAL,
                                 BROADCAST_SERVICE_GROUP,
                                 CALLBACK_NUM,
                                 CALLBACK_NUM_ATAG,
                                 DEST_ADDR_NP_COUNTRY,
                                 DEST_ADDR_NP_INFORMATION,
                                 DEST_NODE_ID,
                                 DEST_SUBADDRESS,
                                 MESSAGE_PAYLOAD,
                                 NETWORK_ERROR_CODE,
                                 SOURCE_NODE_ID,
                                 SOURCE_SUBADDRESS,
                                 VENDOR_SPECIFIC_DEST_MSC_ADDR,
                                 VENDOR_SPECIFIC_SOURCE_MSC_ADDR,
                                 BILLING_IDENTIFICATION:
                                optionalParameters.add(OptionalParameters.deserialize(tlv.tag(), tlv.value().getBytes()));
                                break;

                            case BROADCAST_REP_NUM,
                                 DEST_TELEMATICS_ID,
                                 DESTINATION_PORT,
                                 ITS_SESSION_INFO,
                                 SMS_SIGNAL,
                                 SOURCE_PORT,
                                 USER_MESSAGE_REFERENCE,
                                 SAR_MSG_REF_NUM:
                                short shortValue = Short.parseShort(tlv.value());
                                ByteBuffer buffer = ByteBuffer.allocate(2);
                                buffer.putShort(shortValue);
                                byte[] shortBytes = buffer.array();
                                optionalParameters.add(OptionalParameters.deserialize(tlv.tag(), shortBytes));
                                break;

                            case ALERT_ON_MESSAGE_DELIVERY,
                                 BROADCAST_AREA_SUCCESS,
                                 BROADCAST_CHANNEL_INDICATOR,
                                 BROADCAST_MESSAGE_CLASS,
                                 CALLBACK_NUM_PRES_IND,
                                 CONGESTION_STATE,
                                 DELIVERY_FAILURE_REASON,
                                 DEST_ADDR_NP_RESOLUTION,
                                 DEST_ADDR_SUBUNIT,
                                 DEST_BEARER_TYPE,
                                 DEST_NETWORK_TYPE,
                                 DISPLAY_TIME,
                                 DPF_RESULT,
                                 ITS_REPLY_TYPE,
                                 LANGUAGE_INDICATOR,
                                 MESSAGE_STATE,
                                 MORE_MESSAGES_TO_SEND,
                                 MS_AVAILABILITY_STATUS,
                                 MS_MSG_WAIT_FACILITIES,
                                 MS_VALIDITY,
                                 NUMBER_OF_MESSAGES,
                                 PAYLOAD_TYPE,
                                 PRIVACY_INDICATOR,
                                 SAR_SEGMENT_SEQNUM,
                                 SAR_TOTAL_SEGMENTS,
                                 SC_INTERFACE_VERSION,
                                 SET_DPF,
                                 SOURCE_ADDR_SUBUNIT,
                                 SOURCE_BEARER_TYPE,
                                 SOURCE_NETWORK_TYPE,
                                 SOURCE_TELEMATICS_ID,
                                 USSD_SERVICE_OP:
                                byte result = Byte.parseByte(tlv.value());
                                String hex = HexUtil.convertByteToHexString(result);
                                byte[] bytes = HexUtil.convertHexStringToBytes(hex);
                                optionalParameters.add(OptionalParameters.deserialize(tlv.tag(), bytes));
                                break;

                            default:
                                int intValue = Integer.parseInt(tlv.value());
                                String hexValue = HexUtil.convertByteToHexString((byte) intValue);
                                byte[] bytesValue = HexUtil.convertHexStringToBytes(hexValue);
                                optionalParameters.add(OptionalParameters.deserialize(tlv.tag(), bytesValue));
                        }
                    }
                });
            }
        } catch (Exception ex) {
            log.error("Error on get the optional parameters ", ex);
            throw new IllegalStateException("Error on get the optional parameters ", ex);
        }
        return optionalParameters.toArray(new OptionalParameter[0]);
    }

    public static List<UtilsRecords.OptionalParameter> setTLV(OptionalParameter[] optionalParameters) {
        List<UtilsRecords.OptionalParameter> optionalParamList = new ArrayList<>();
        try {
            for (OptionalParameter optionalParameter : optionalParameters) {
                byte[] bytesOptionalParams = optionalParameter.serialize();
                byte[] bytesTag = Arrays.copyOfRange(bytesOptionalParams, 0, 2);
                byte[] bytesValue = Arrays.copyOfRange(bytesOptionalParams, 4, bytesOptionalParams.length);
                short tag = ByteBuffer.wrap(bytesTag).getShort();
                optionalParamList.add(new UtilsRecords.OptionalParameter(tag, SmppUtils.deserializeTLV(tag, bytesValue)));
            }
        } catch (Exception ex) {
            log.error("Error on try to get the Optional Parameters ", ex);
        }
        return optionalParamList;
    }
}
