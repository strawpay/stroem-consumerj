package io.stroem.consumerj.issuer;

/**
 * Used to collect the success/error info from receiving a message
 */
public class StroemReceiveMessageResult {

    public static enum StatusCode {
        OK(1),                          // All worked well

        // Errors detected at consumer-side
        ILLEGAL_STATE(2),               // We cannot accept this message at this stage
        WRONG_ISSUER_STROEM_VERSION(3), // We do not accept the server's Stroem version
        INSUFFICIENT_MONEY(4),          // Not enough money in the channel to pay
        UNKNOWN_MESSAGE_TYPE(5),        // We do not recognice this message type
        CORRUPT_MESSAGE(6),             // We can't read this message

        // Issuer server error
        IE_ERROR(21),                   // Issuer sends ERROR

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

    StatusCode statusCode;
    // If we should change stroem step, this will be set
    StroemStep newStroemStep;

    String errorMessage;

    // Used for statusCode results
    public StroemReceiveMessageResult() {
        this.statusCode = StatusCode.OK;
        this.newStroemStep = null;
        this.errorMessage = null;
    }

    // Used for statusCode results
    public StroemReceiveMessageResult(StroemStep newStroemStep) {
        this.statusCode = StatusCode.OK;
        this.newStroemStep = newStroemStep;
        this.errorMessage = null;
    }

    // Used for failures
    public StroemReceiveMessageResult(StatusCode statusCode, String errorMessage) {
        this.statusCode = statusCode;
        this.newStroemStep = null;
        this.errorMessage = errorMessage;
    }

    public boolean isSuccessful() {
        return statusCode == StatusCode.OK;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public StroemStep getNewStroemStep() {
        return newStroemStep;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
