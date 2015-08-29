package io.stroem.consumerj.issuer;

import io.stroem.proto.StroemProtos;
import org.bitcoinj.protocols.channels.PaymentChannelCloseException;

/**
 * Holds the StroemNegotiator or an error, sent from the Issuer during increment payment
 *
 * The reason for this wrapping is that it's hard to throw Exceptions when dealing with futures.
 */
public class StroemIncrementPaymentResult {

    public static enum StatusCode {
        OK(1),                          // All worked well

        // Errors detected at consumer-side
        INTERRUPTED(2),                 // We were interrupted during waiting for answer
        TCP_SOCKET_CLOSED(3),           // The TCP socket was closed (for some unknown reason)
        INSUFFICIENT_MONEY(4),          // Not enough money in the channel to pay
        CHANNEL_CLOSED(5),              // The channel is closed (programming error, should have been discoverd earlier)
        CHANNEL_NOT_READY(6),           // The channel state is wrong (programming error, should not even be possible)

        // Issuer Errors: Issuer responds with error code
        IE_TIMEOUT(11),                 // Protocol timeout occurred (one party hung).
        IE_NO_ACCEPTABLE_VERSION(12),   // We do not speak the Stroem version the other side asked for.
        IE_WRONG_ISSUER_PUBLICKEY(13),  // (Rare error) Issuer public key mismatch
        IE_OTHER(14),

        // Payment Channel server error
        IE_PAYMENT_CHANNEL_ERROR(21),   // Issuer sends Payment Channel ERROR


        ERROR(100);                     // General error

        int id;
        StatusCode(int id) {
            this.id = id;
        }

        static StatusCode fromId(int id) {
            for (StatusCode errorCode : StatusCode.values())
                if (errorCode.id == id)
                    return errorCode;
            return ERROR;
        }
    }

    private final StatusCode statusCode;
    private final String errorMessage;

    private final StroemNegotiator stroemNegotiator;


    public StroemIncrementPaymentResult(StroemNegotiator stroemNegotiator) {
        this.stroemNegotiator = stroemNegotiator;
        this.statusCode = StatusCode.OK;
        this.errorMessage = null;
    }

    public StroemIncrementPaymentResult(StatusCode statusCode, String errorMessage) {
        this.stroemNegotiator = null;

        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    public StroemIncrementPaymentResult(PaymentChannelCloseException e) {
        this.stroemNegotiator = null;

        this.statusCode = StatusCode.IE_PAYMENT_CHANNEL_ERROR;
        if (PaymentChannelCloseException.CloseReason.REMOTE_SENT_ERROR == e.getCloseReason()) {
            this.errorMessage = "Error from Server, see logs for detailed info"; // The only possible case right now
        } else {
            this.errorMessage = "We got close reason: " + e.getCloseReason();
        }
    }

    // TODO Use this for Stroem errors
    public StroemIncrementPaymentResult(StroemProtocolException e) {
        this.stroemNegotiator = null;

        StroemProtos.Error.ErrorCode code = e.getCode();
        StatusCode statusCode;
        String errorMessage;
        switch (code) {
            case TIMEOUT:
                statusCode = StatusCode.IE_TIMEOUT;
                errorMessage = e.getMessage();
                break;
            case NO_ACCEPTABLE_VERSION:
                statusCode = StatusCode.IE_NO_ACCEPTABLE_VERSION;
                errorMessage = e.getMessage();
                break;
            case WRONG_ISSUER_PUBLICKEY:
                statusCode = StatusCode.IE_WRONG_ISSUER_PUBLICKEY;
                errorMessage = e.getMessage();
                break;
            default:
                statusCode = StatusCode.IE_OTHER;
                errorMessage = e.getMessage();
        }

        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    /**
     * @return true if the protocol session was estabilshed
     */
    public boolean isOk() {
        return this.statusCode == StatusCode.OK;
    }

    public StroemNegotiator getStroemNegotiator() {
        return stroemNegotiator;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
