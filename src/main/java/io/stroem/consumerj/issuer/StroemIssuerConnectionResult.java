package io.stroem.consumerj.issuer;

import io.stroem.consumerj.StroemIssuerConnection;
import io.stroem.proto.StroemProtos;

/**
 * Holds the StroemIssuerConnection or an error, sent from the Issuer
 *
 * The reason for this wrapping is that it's hard to throw Exceptions when dealing with futures.
 */
public class StroemIssuerConnectionResult {

    public static enum StatusCode {
        OK(1),                          // All worked well

        // Errors detected at consumer-side
        ISSUER_DOWN(3),                 // Issuer does not respond on the given URI (where can we set this?)
        WRONG_ISSUER_STROEM_VERSION(4), // We do not accept the server's Stroem version
        INSUFFICIENT_MONEY(5),          // Not enough money in the wallet to create the channel
        TCP_SOCKET_CLOSED(6),           // The TCP socket was closed (for some unknown reason)
        BAD_MESSAGE(7),                 // Issuer sent a message we cannot understand

        // Issuer Errors: Issuer responds with error code
        IE_TIMEOUT(11),                 // Protocol timeout occurred (one party hung).
        IE_NO_ACCEPTABLE_VERSION(12),   // We do not speak the Stroem version the other side asked for.
        IE_DURATION_UNACCEPTABLE(13),   // Request duration to long or short
        IE_WRONG_ISSUER_PUBLICKEY(14),  // (Rare error) Issuer public key mismatch
        IE_OTHER(15),

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

    private final StroemIssuerConnection stroemIssuerConnection;


    public StroemIssuerConnectionResult(StroemIssuerConnection stroemIssuerConnection) {
        this.stroemIssuerConnection = stroemIssuerConnection;
        this.statusCode = StatusCode.OK;
        this.errorMessage = null;
    }

    public StroemIssuerConnectionResult(StatusCode statusCode, String errorMessage) {
        this.stroemIssuerConnection = null;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    public StroemIssuerConnectionResult(StroemProtocolException e) {
        this.stroemIssuerConnection = null;

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
            case DURATION_UNACCEPTABLE:
                statusCode = StatusCode.IE_DURATION_UNACCEPTABLE;
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

    public StroemIssuerConnection getStroemIssuerConnection() {
        return stroemIssuerConnection;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
