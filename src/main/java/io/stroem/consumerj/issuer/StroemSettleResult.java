package io.stroem.consumerj.issuer;

import io.stroem.consumerj.StroemIssuerConnection;
import io.stroem.proto.StroemProtos;

/**
 * Holds the an error, sent from the Issuer during settle
 *
 * The reason for this wrapping is that it's hard to throw Exceptions when dealing with futures.
 */
public class StroemSettleResult {

    public static enum StatusCode {
        OK(1),                          // All worked well

        // Errors detected at consumer-side
        ISSUER_DOWN(2),                 // Issuer does not respond on the given URI (where can we set this?)
        TCP_SOCKET_CLOSED(3),           // The TCP socket was closed (for some unknown reason)
        BAD_MESSAGE(4),                 // Issuer sent a message we cannot understand

        // Issuer Errors: Issuer responds with error code
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

    public StroemSettleResult() {
        this.statusCode = StatusCode.OK;
        this.errorMessage = null;
    }

    public StroemSettleResult(StatusCode statusCode, String errorMessage) {
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }


    /**
     * @return true if the protocol session was estabilshed
     */
    public boolean isOk() {
        return this.statusCode == StatusCode.OK;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
