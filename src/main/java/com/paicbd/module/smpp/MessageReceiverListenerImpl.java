package com.paicbd.module.smpp;

import com.paicbd.module.utils.AppProperties;
import com.paicbd.module.utils.Utils;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.kafka.KafkaTopicsConstants;
import com.paicbd.smsc.scylla.ScyllaManager;
import com.paicbd.smsc.scylla.ScyllaTablesConstants;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.EncodingUtils;
import com.paicbd.smsc.utils.GeneralSmscConstants;
import com.paicbd.smsc.utils.Generated;
import com.paicbd.smsc.utils.MessageIDGeneratorImpl;
import com.paicbd.smsc.utils.RequestDelivery;
import com.paicbd.smsc.utils.SmppUtils;
import com.paicbd.smsc.utils.Watcher;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.EnquireLink;
import org.jsmpp.bean.MessageRequest;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.extra.ProcessRequestException;
import org.jsmpp.session.DataSmResult;
import org.jsmpp.session.MessageReceiverListener;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.Session;
import org.jsmpp.session.SubmitSmResult;
import org.jsmpp.util.DeliveryReceiptState;
import org.jsmpp.util.MessageIDGenerator;
import org.jsmpp.util.MessageId;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.paicbd.module.utils.Constants.ORIGIN_GATEWAY_TYPE;
import static com.paicbd.smsc.utils.EncodingUtils.GSM7_DATA_CODINGS;
import static com.paicbd.smsc.utils.EncodingUtils.UCS2_DATA_CODINGS;
import static org.jsmpp.SMPPConstant.STAT_ESME_RSYSERR;

/**
 * @author <a href="mailto:enmanuelcalero61@gmail.com"> Enmanuel Calero </a>
 * @author <a href="mailto:ndiazobed@gmail.com"> Obed Navarrete </a>
 */
@Slf4j
public class MessageReceiverListenerImpl implements MessageReceiverListener {
    private static final Set<Short> REQUIRED_TAGS_FOR_EMPTY_DLR = Set.of(
            OptionalParameter.Tag.RECEIPTED_MESSAGE_ID.code(),
            OptionalParameter.Tag.MESSAGE_STATE.code()
    );

    private static final Map<DeliveryReceiptState, String> ERROR_CODE_BY_DELIVERY_RECEIPT_STATE = Map.of(
            DeliveryReceiptState.ENROUTE, "000",
            DeliveryReceiptState.DELIVRD, "000",
            DeliveryReceiptState.EXPIRED, "060",
            DeliveryReceiptState.DELETED, "035",
            DeliveryReceiptState.UNDELIV, "034",
            DeliveryReceiptState.ACCEPTD, "000",
            DeliveryReceiptState.UNKNOWN, "255",
            DeliveryReceiptState.REJECTD, "009"
    );

    private final AtomicInteger receivedSubmitSm = new AtomicInteger(0);
    private final AtomicInteger receivedDeliverSm = new AtomicInteger(0);
    private final MessageIDGenerator messageIDGenerator = new MessageIDGeneratorImpl();
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AppProperties appProperties;

    @Setter
    private Gateway gateway;

    @Setter
    private ScyllaManager scyllaManager;


    public MessageReceiverListenerImpl(Gateway gateway, KafkaTemplate<String, String> kafkaTemplate, ScyllaManager scyllaManager, AppProperties appProperties) {
        this.gateway = gateway;
        this.kafkaTemplate = kafkaTemplate;
        this.scyllaManager = scyllaManager;
        this.appProperties = appProperties;
        new Watcher("SmppClient:DeliverSm-InboundDeliverSm-NetworkId" + gateway.getNetworkId(), receivedDeliverSm, 1);
        new Watcher("SmppClient:DeliverSm-InboundSubmitSm-NetworkId" + gateway.getNetworkId(), receivedSubmitSm, 1);
    }

    @Override
    public void onAcceptDeliverSm(final DeliverSm deliverSm) {
        final boolean isMo = MessageType.DEFAULT.containedIn(deliverSm.getEsmClass());
        final boolean isReceipt = MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass());

        if (!isMo && !isReceipt) {
            log.warn("Received DeliverSm with unsupported ESM class: {}", deliverSm.getEsmClass());
            return;
        }

        MessageEvent deliverSmEvent = getDeliverSmEvent(deliverSm);
        if (Objects.isNull(deliverSmEvent)) return;

        if (isReceipt) {
            prepareDlrForPublish(deliverSm, deliverSmEvent);
            String existsSubmitResp = scyllaManager.selectFromTable(ScyllaTablesConstants.SMPP_SUBMIT_SM_RESULT_TABLE, deliverSmEvent.getDeliverSmId());
            if (Objects.isNull(existsSubmitResp) || existsSubmitResp.isBlank()) return;
            UtilsRecords.SubmitSmResponseEvent submitSmResponse = Converter.stringToObject(existsSubmitResp, UtilsRecords.SubmitSmResponseEvent.class);
            if (!submitSmResponse.isFinalSegmentForSplitSmsc()) return;
        } else {
            prepareMoForPublish(deliverSmEvent);
        }

        publishDeliverSmToTopic(deliverSm, deliverSmEvent);
    }

    private void prepareDlrForPublish(DeliverSm deliverSm, MessageEvent deliverSmEvent) {
        try {
            boolean isHandledEncoding = GSM7_DATA_CODINGS.contains((int) deliverSm.getDataCoding()) || UCS2_DATA_CODINGS.contains((int) deliverSm.getDataCoding());
            DeliveryReceipt delReceipt = isHandledEncoding ? new DeliveryReceipt(deliverSmEvent.getDelReceipt()) :
                    deliverSm.getShortMessageAsDeliveryReceipt();
            if (this.gateway.isTlvMessageReceiptId()) {
                log.debug("Getting message_id from the TLV -> RECEIPTED_MESSAGE_ID (0x001E)");
                OptionalParameter receiptMessageId = deliverSm.getOptionalParameter(OptionalParameter.Tag.RECEIPTED_MESSAGE_ID);
                if (Objects.isNull(receiptMessageId)) {
                    log.error("The message_id is not present in the TLV, this deliver_sm can not be processed {}", delReceipt.toString());
                    return;
                }
                var recMsgId = (OptionalParameter.Receipted_message_id) receiptMessageId;
                deliverSmEvent.setDeliverSmId(getMessageId(recMsgId.getValueAsString()));
            } else {
                log.debug("Getting message_id from the DeliveryReceipt object");
                deliverSmEvent.setDeliverSmId(getMessageId(delReceipt.getId()));
            }

            deliverSmEvent.setStatus(delReceipt.getFinalStatus().name());
            deliverSmEvent.setErrorCode(
                    (delReceipt.getError() != null && delReceipt.getError().matches("^\\d+$"))
                            ? Integer.parseInt(delReceipt.getError())
                            : 0
            );
        } catch (Exception e) {
            log.error("Error on addDlrInQ: {}", e.getMessage(), e);
        }
    }

    private void prepareMoForPublish(MessageEvent deliverSmEvent) {
        MessageId messageId = messageIDGenerator.newMessageId();
        deliverSmEvent.setMessageId(messageId.toString());
        deliverSmEvent.setDeliverSmId(messageId.toString());
        deliverSmEvent.setParentId(messageId.toString());
        deliverSmEvent.setSmscMessagePriority(GeneralSmscConstants.MEDIUM_PRIORITY);
    }

    @Override
    @Generated
    public void onAcceptAlertNotification(AlertNotification alertNotification) {
        log.debug("onAcceptAlertNotification: {} {}", alertNotification.getSourceAddr(), alertNotification.getEsmeAddr());
    }

    @Override
    public DataSmResult onAcceptDataSm(final DataSm dataSm, final Session source)
            throws ProcessRequestException {
        log.debug("The data_sm is not implemented onAcceptDataSm: {} {} {}", source.getSessionId(), dataSm.getSourceAddr(), dataSm.getDestAddress());
        throw new ProcessRequestException("The data_sm is not implemented", STAT_ESME_RSYSERR);
    }

    @Override
    public SubmitSmResult onAcceptSubmitSm(SubmitSm submitSm, SMPPSession smppSession) {
        MessageId messageId = messageIDGenerator.newMessageId();
        addSubmitSmInQ(submitSm, messageId);

        return new SubmitSmResult(messageId, new OptionalParameter[0]);
    }

    protected void publishDeliverSmToTopic(DeliverSm deliverSm, MessageEvent deliverSmEvent) {
        prepareTlvForDeliverSm(deliverSm, deliverSmEvent);
        deliverSmEvent.setSystemId(this.gateway.getSystemId());
        deliverSmEvent.setOriginNetworkId(this.gateway.getNetworkId());
        deliverSmEvent.setOriginProtocol(this.gateway.getProtocol());
        deliverSmEvent.setOriginNetworkType(ORIGIN_GATEWAY_TYPE);
        kafkaTemplate.send(KafkaTopicsConstants.PRE_DELIVER_TOPIC, deliverSmEvent.toString());
        receivedDeliverSm.incrementAndGet();
    }

    private void prepareTlvForDeliverSm(DeliverSm deliverSm, MessageEvent deliverSmEvent) {
        if (deliverSm.getOptionalParameters() != null && deliverSm.getOptionalParameters().length > 0) {
            SmppUtils.setTLV(deliverSmEvent, deliverSm.getOptionalParameters());
            if (this.appProperties.isSmppRemoveDlrTlvs()) {
                log.debug("Removing TLV tags RECEIPTED_MESSAGE_ID and MESSAGE_STATE from deliver_sm ");
                deliverSmEvent.getOptionalParameters()
                        .removeIf(p -> p.tag() == OptionalParameter.Tag.RECEIPTED_MESSAGE_ID.code() ||
                                p.tag() == OptionalParameter.Tag.MESSAGE_STATE.code());
            }

        }
    }

    protected MessageEvent getDeliverSmEvent(DeliverSm deliverSm) {
        try {
            MessageEvent deliverSmEvent = new MessageEvent();
            int encodingType = SmppUtils.determineEncodingType(deliverSm.getDataCoding(), this.gateway);
            byte[] messagePayload = buildMessagePayload(deliverSmEvent, deliverSm);
            if (messagePayload.length == 0) {
                log.error("The deliver_sm is empty, and it does not contain the required tags for an empty DLR");
                return null;
            }

            this.setDataToMessageEvent(deliverSmEvent, deliverSm);
            deliverSmEvent.setId(System.currentTimeMillis() + "-" + System.nanoTime());
            deliverSmEvent.setRegisteredDelivery((int) deliverSm.getRegisteredDelivery());

            boolean isValidDataCoding = EncodingUtils.isValidDataCoding(encodingType);
            String decodedShortMessage;
            byte[] udhBytes;
            byte[] cleanedBytes;
            if (deliverSm.isUdhi()) {
                udhBytes = EncodingUtils.getUdhBytes(messagePayload);
                cleanedBytes = EncodingUtils.getCleanedBytes(messagePayload);
                decodedShortMessage = isValidDataCoding ? EncodingUtils.decodeMessage(deliverSm.getShortMessage(), encodingType)
                        : EncodingUtils.bytesToHex(cleanedBytes);
                EncodingUtils.parseUdh(udhBytes, deliverSmEvent);
            } else {
                decodedShortMessage = EncodingUtils.decodeMessage(messagePayload, encodingType);
                cleanedBytes = messagePayload;
            }

            deliverSmEvent.setDelReceipt(decodedShortMessage);
            deliverSmEvent.setMessageBytes(cleanedBytes);
            deliverSmEvent.setShortMessage(decodedShortMessage);

            deliverSmEvent.setSystemId(this.gateway.getSystemId());
            deliverSmEvent.setCheckSubmitSmResponse(MessageType.SMSC_DEL_RECEIPT.containedIn(deliverSm.getEsmClass()));
            deliverSmEvent.setRegisteredDelivery(RequestDelivery.NON_REQUEST_DLR.getValue()); // By default, DeliverReceipt or MO messages won't be registered for delivery
            deliverSmEvent.setEsmClass((int) deliverSm.getEsmClass());

            return deliverSmEvent;
        } catch (Exception e) {
            log.error("Error creating MessageEvent", e);
            return null;
        }
    }

    private byte[] buildMessagePayload(MessageEvent dlr, DeliverSm deliverSm) {
        byte[] messagePayload = deliverSm.getShortMessage().length > 0
                ? deliverSm.getShortMessage() : getMoMessageBytes(deliverSm);
        if (messagePayload.length == 0 && deliverSm.isSmscDeliveryReceipt()) {
            log.debug("The deliver_sm is empty, preparing an empty DLR");
            prepareDeliverReceipt(dlr, deliverSm);
            return deliverSm.getShortMessage();
        } else {
            try {
                dlr.setMessageId(deliverSm.getShortMessageAsDeliveryReceipt().getId().toUpperCase());
            } catch (Exception e) {
                log.error("Error extracting message_id from DeliveryReceipt: {}", e.getMessage(), e);
                dlr.setMessageId("UNKNOWN-ID");
            }
        }
        return messagePayload;
    }

    private void prepareDeliverReceipt(MessageEvent dlr, DeliverSm deliverSm) {
        if (deliverSm.getOptionalParameters() == null || deliverSm.getOptionalParameters().length == 0) {
            log.error("The deliver_sm is empty and does not contain the required tags for an empty DLR");
            return;
        }
        boolean containsRequiredTags = Arrays.stream(deliverSm.getOptionalParameters())
                .map(optionalParameter -> optionalParameter.tag)
                .collect(Collectors.toSet())
                .containsAll(REQUIRED_TAGS_FOR_EMPTY_DLR);
        if (containsRequiredTags) {
            DeliveryReceipt personalizedDeliveryReceipt = buildDeliveryReceiptFromBytes(dlr, deliverSm);
            byte[] processedBytes;
            if (GSM7_DATA_CODINGS.contains((int) deliverSm.getDataCoding())) {
                processedBytes = EncodingUtils.encodeMessage(personalizedDeliveryReceipt.toString(), EncodingUtils.GSM7);
            } else if (UCS2_DATA_CODINGS.contains((int) deliverSm.getDataCoding())) {
                processedBytes = EncodingUtils.encodeMessage(personalizedDeliveryReceipt.toString(), EncodingUtils.UCS2);
            } else {
                Charset defaultCharset = StandardCharsets.ISO_8859_1; // Default for binary and reserved data coding
                processedBytes = personalizedDeliveryReceipt.toString().getBytes(defaultCharset);
            }

            deliverSm.setShortMessage(processedBytes);
        } else {
            log.error("The deliver_sm is empty and does not contain the required tags for an empty DLR: {}", deliverSm);
        }
    }

    private static DeliveryReceipt buildDeliveryReceiptFromBytes(MessageEvent dlr, DeliverSm deliverSm) {
        int stateInt = getMessageStateTlvValue(deliverSm);
        String messageIdString = getMessageIdTlvValue(deliverSm);
        dlr.setMessageId(messageIdString.toUpperCase());
        DeliveryReceiptState dlrState = Utils.mapDeliveryReceiptState(stateInt);
        String errorCode = ERROR_CODE_BY_DELIVERY_RECEIPT_STATE.get(dlrState);
        boolean isSuccess = Objects.equals(errorCode, "000");
        return new DeliveryReceipt(messageIdString, 1, isSuccess ? 1 : 0, new Date(), new Date(), dlrState, errorCode, "");
    }

    private static int getMessageStateTlvValue(DeliverSm deliverSm) {
        return Arrays.stream(deliverSm.getOptionalParameters())
                .filter(OptionalParameter.Message_state.class::isInstance)
                .map(OptionalParameter.Message_state.class::cast)
                .map(OptionalParameter.Message_state::getValue)
                .findFirst()
                .map(Byte::toUnsignedInt)
                .orElse(DeliveryReceiptState.UNKNOWN.value());
    }

    private static String getMessageIdTlvValue(DeliverSm deliverSm) {
        return Arrays.stream(deliverSm.getOptionalParameters())
                .filter(OptionalParameter.Receipted_message_id.class::isInstance)
                .map(OptionalParameter.Receipted_message_id.class::cast)
                .map(OptionalParameter.Receipted_message_id::getValue)
                .findFirst()
                .map(aid -> new OptionalParameter.Receipted_message_id(aid).getValueAsString())
                .orElse("UNKNOWN-ID");
    }

    private byte[] getMoMessageBytes(DeliverSm deliverSm) {
        return deliverSm.getShortMessage().length > 0 ?
                deliverSm.getShortMessage() : SmppUtils.getMessagePayloadValue(deliverSm);
    }

    private String getMessageId(String id) {
        if (this.gateway.isMessageIdDecimalFormat()) {
            log.debug("The message_id is in decimal format");
            return Long.toHexString(Long.parseLong(id)).toUpperCase();
        }
        log.debug("The message_id is in hexadecimal format");
        return SmppConnectionManager.cleanAndUpperString(id);
    }

    @Override
    public void onAcceptEnquireLink(EnquireLink enquireLink, Session source) {
        MessageReceiverListener.super.onAcceptEnquireLink(enquireLink, source);
    }

    /**
     * handling input submitSm messages
     */
    protected void addSubmitSmInQ(SubmitSm submitSm, MessageId messageId) {
        MessageEvent submitSmEvent = createSubmitSmEvent(submitSm, messageId);
        submitSmEvent.setOriginNetworkType(ORIGIN_GATEWAY_TYPE);
        submitSmEvent.setOriginProtocol("SMPP");
        submitSmEvent.setStringValidityPeriod(submitSm.getValidityPeriod());
        submitSmEvent.setSmscMessagePriority(GeneralSmscConstants.MEDIUM_PRIORITY);
        kafkaTemplate.send(KafkaTopicsConstants.PRE_MESSAGE_MEDIUM_TOPIC, submitSmEvent.toString());
        receivedSubmitSm.incrementAndGet();
    }

    private MessageEvent createSubmitSmEvent(SubmitSm submitSm, MessageId messageId) {
        MessageEvent event = getSubmitSmEvent(submitSm, gateway);
        event.setSystemId(gateway.getSystemId());
        event.setOriginNetworkId(gateway.getNetworkId());
        event.setId(System.currentTimeMillis() + "-" + System.nanoTime());
        event.setMessageId(messageId.getValue());
        event.setParentId(messageId.getValue());

        if (submitSm.getOptionalParameters() != null && submitSm.getOptionalParameters().length >= 1) {
            SmppUtils.setTLV(event, submitSm.getOptionalParameters());
        }

        return event;
    }

    protected MessageEvent getSubmitSmEvent(SubmitSm submitSm, Gateway gateway) {
        int encodingType = SmppUtils.determineEncodingType(submitSm.getDataCoding(), gateway);
        String decodedMessage = EncodingUtils.decodeMessage(submitSm.getShortMessage(), encodingType);
        MessageEvent submitSmEvent = new MessageEvent();
        submitSmEvent.setRetry(false);
        submitSmEvent.setRetryDestNetworkId("");
        submitSmEvent.setRegisteredDelivery((int) submitSm.getRegisteredDelivery());
        this.setDataToMessageEvent(submitSmEvent, submitSm);
        submitSmEvent.setShortMessage(decodedMessage);
        return submitSmEvent;
    }

    public void setDataToMessageEvent(MessageEvent messageEvent, MessageRequest messageRequest) {
        messageEvent.setCommandId(messageRequest.getCommandId());
        messageEvent.setCommandLength(messageRequest.getCommandLength());
        messageEvent.setCommandStatus(messageRequest.getCommandStatus());
        messageEvent.setServiceType(messageRequest.getServiceType());
        messageEvent.setProtocolId(messageRequest.getProtocolId());
        messageEvent.setPriorityFlag(messageRequest.getPriorityFlag());
        messageEvent.setReplaceIfPresent(messageRequest.getReplaceIfPresent());
        messageEvent.setScheduleDeliveryTime(messageRequest.getScheduleDeliveryTime());
        messageEvent.setSmDefaultMsgId(messageRequest.getSmDefaultMsgId());

        messageEvent.setCommandStatus(messageRequest.getCommandStatus());
        messageEvent.setSequenceNumber(messageRequest.getSequenceNumber());
        messageEvent.setSourceAddrTon((int) messageRequest.getSourceAddrTon());
        messageEvent.setSourceAddrNpi((int) messageRequest.getSourceAddrNpi());
        messageEvent.setSourceAddr(messageRequest.getSourceAddr());
        messageEvent.setDestAddrTon((int) messageRequest.getDestAddrTon());
        messageEvent.setDestAddrNpi((int) messageRequest.getDestAddrNpi());
        messageEvent.setDestinationAddr(messageRequest.getDestAddress());
        messageEvent.setEsmClass((int) messageRequest.getEsmClass());
        long validityPeriod = Objects.isNull(messageRequest.getValidityPeriod()) ? 0
                : Converter.smppValidityPeriodToSeconds(messageRequest.getValidityPeriod());
        messageEvent.setValidityPeriod(validityPeriod);
        messageEvent.setDataCoding((int) messageRequest.getDataCoding());
        messageEvent.setSmDefaultMsgId(messageRequest.getSmDefaultMsgId());
    }
}
