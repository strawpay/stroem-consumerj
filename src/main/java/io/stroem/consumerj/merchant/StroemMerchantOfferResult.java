package io.stroem.consumerj.merchant;

import io.stroem.consumerj.StroemPaymentProtocolSession;

/**
 * Holds the PaymentRequest or an error, sent from the Merchant
 *
 * The reason for this wrapping is that it's hard to throw Exceptions when dealing with futures.
 */
public class StroemMerchantOfferResult {

    public static enum StatusCode {
        OK(1),                   // Protocol timeout occurred (one party hung).
        WRONG_ISSUER(2),         // The issuer is not accepted
        INVALID_URI(3),          // The URI given was not a real URI
        INVALID_STROEM_URI(4),   // The URI given was not a correct Stroem URI
        ERROR(5);                // General error

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

    private final StroemPaymentProtocolSession stroemPaymentProtocolSession;


    public StroemMerchantOfferResult(StroemPaymentProtocolSession stroemPaymentProtocolSession) {
        this.stroemPaymentProtocolSession = stroemPaymentProtocolSession;
        this.statusCode = StatusCode.OK;
        this.errorMessage = null;
    }

    public StroemMerchantOfferResult(StatusCode statusCode, String errorMessage) {
        this.stroemPaymentProtocolSession = null;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    /**
     * @return true if the protocol session was estabilshed
     */
    public boolean isOk() {
        return this.statusCode == StatusCode.OK;
    }

    public StroemPaymentProtocolSession getStroemPaymentProtocolSession() {
        return stroemPaymentProtocolSession;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
