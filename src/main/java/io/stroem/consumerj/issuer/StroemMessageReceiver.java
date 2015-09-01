package io.stroem.consumerj.issuer;

import io.stroem.consumerj.StroemIssuerConnection;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.protocols.channels.PaymentChannelClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.stroem.proto.StroemProtos;
import org.bitcoin.paymentchannel.Protos;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkState;



/**
 * Handles incoming Stroem messages.
 */
public class StroemMessageReceiver {
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(StroemMessageReceiver.class);

    private final PaymentChannelClient paymentChannelClient;

    private StroemEntity issuerGivenEntity; // Who the issuer claims to be

    // Call this method to get the issuer's Entity after the channel has been initiated
    public StroemEntity getIssuerGivenEntity() {
        return issuerGivenEntity;
    }

    public StroemMessageReceiver(PaymentChannelClient paymentChannelClient) {
        this.paymentChannelClient = paymentChannelClient;
    }

    public StroemReceiveMessageResult receiveMessage(StroemProtos.StroemMessage msg, StroemStep previousStep) {

        switch (msg.getType()) {
            case PAYMENTCHANNEL_MESSAGE:
                checkState(msg.hasPaymentChannelMessage());
                return readPaymentChannelMessage(msg.getPaymentChannelMessage());
            case STROEM_SERVER_VERSION:
                checkState(msg.hasStroemServerVersion());
                return receiveStroemVersion(msg.getStroemServerVersion(), previousStep);
            case PROMISSORY_NOTE:
                checkState(msg.hasPromissoryNote());
                return receivePromissoryNote(msg.getPromissoryNote());
            case ERROR: // Only for Stroem Errors
                checkState(msg.hasError());
                return receiveError(msg.getError());
            default:
                String errMsg = "Received unknown Stroem message type " + msg.getType().name();
                log.debug(errMsg);
                return new StroemReceiveMessageResult(StroemReceiveMessageResult.StatusCode.UNKNOWN_MESSAGE_TYPE, errMsg);
        }
    }

    /**
     * Just extract the payment channel message and send it to PaymentChannelClient.
     */
    private StroemReceiveMessageResult readPaymentChannelMessage(StroemProtos.PaymentChannelMessage msg) {
        ByteString byteString = msg.getPaymentChannelMessage();
        try {
            Protos.TwoWayChannelMessage paymentChannelMsg = Protos.TwoWayChannelMessage.newBuilder().mergeFrom(byteString).build();
            String text = "Received a StroemMessage of type PaymentChannel: " + paymentChannelMsg.getType();
            if (Protos.TwoWayChannelMessage.MessageType.ERROR == paymentChannelMsg.getType()) {
                // Ignore these kind of errors
                log.debug(text + " (this ERROR will be picked up later in the destroyConnection() callback) ");
            } else {
                log.debug(text);
            }

            paymentChannelClient.receiveMessage(paymentChannelMsg);
            return new StroemReceiveMessageResult(); // Even ERROR counts as success at this level, since Stroem does not "see" it
        } catch (InvalidProtocolBufferException e) {
            return new StroemReceiveMessageResult(StroemReceiveMessageResult.StatusCode.CORRUPT_MESSAGE,
                    "Unable to read the payment channel protobuf message: ");
        } catch (InsufficientMoneyException e) {
            return new StroemReceiveMessageResult(StroemReceiveMessageResult.StatusCode.INSUFFICIENT_MONEY, e.getMessage());

        }
    }

    /**
     * Check that the server version is same as we have.
     * If so, open the payment channel
     */
    private StroemReceiveMessageResult receiveStroemVersion(StroemProtos.StroemServerVersion msg, StroemStep step)  {
        log.debug("Received Stroem server version");
        if(step == StroemStep.WAITING_FOR_SERVER_STROM_VERSION) {
            int serverVersion = msg.getVersion();
            if(serverVersion == StroemIssuerConnection.CLIENT_STROEM_VERSION) {
                paymentChannelClient.connectionOpen();
                issuerGivenEntity = new StroemEntity(msg.getEntity());
                return new StroemReceiveMessageResult(StroemStep.WAITING_FOR_PAYMENT_CHANNEL_INITIATE);
            } else {
                return new StroemReceiveMessageResult(StroemReceiveMessageResult.StatusCode.WRONG_ISSUER_STROEM_VERSION,
                        "Server version should be " + StroemIssuerConnection.CLIENT_STROEM_VERSION + " but was " + serverVersion);
            }
        } else {
            return new StroemReceiveMessageResult(StroemReceiveMessageResult.StatusCode.ILLEGAL_STATE ,
                    "Can't get STROM_VERSION from server before client sent STROM_VERSION");
        }
    }

    /**
     * The Promissory Note was sent in a separate Stroem message
     */
    private StroemReceiveMessageResult receivePromissoryNote(StroemProtos.PromissoryNote msg) {
        log.debug("Received Stroem promissory note");
        //  TODO: Not needed yet, since we get the PN inside the ACK as of now
        throw new IllegalStateException("not impl");

    }

    /**
     * Try to recognize the error code.
     */
    private StroemReceiveMessageResult receiveError(StroemProtos.Error msg) {
        log.debug("Received a Stroem ERROR: " + msg.getCode().name());
        String expl = msg.hasExplanation() ? msg.getExplanation() : "(none)";
        String err = "Server sent ERROR " + msg.getCode().name() + " with explanation " +  expl;
        return new StroemReceiveMessageResult(StroemReceiveMessageResult.StatusCode.IE_ERROR, err);
    }

}
