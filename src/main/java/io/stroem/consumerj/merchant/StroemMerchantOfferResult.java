package io.stroem.consumerj.merchant;

import io.stroem.consumerj.StroemMerchantSession;

/**
 * Holds the PaymentRequest or an error, sent from the Merchant
 *
 * The reason for this wrapping is that it's hard to throw Exceptions when dealing with futures.
 */
public class StroemMerchantOfferResult {

    public static enum StatusCode {
        OK(1),                   // All worked well
        WRONG_ISSUER(2),         // The issuer is not accepted
        INVALID_URI(3),          // The URI given was not a real URI
        INVALID_STROEM_URI(4),   // The URI given was not a correct Stroem URI
        MERCHANT_DOWN(5),        // Merchant does not respond on the given URI
        MERCHANT_RESPONDS_WITH_ERROR_CODE(6),
        ERROR(7);                // General error

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

    private final StroemMerchantSession stroemMerchantSession;


    public StroemMerchantOfferResult(StroemMerchantSession stroemMerchantSession) {
        this.stroemMerchantSession = stroemMerchantSession;
        this.statusCode = StatusCode.OK;
        this.errorMessage = null;
    }

    public StroemMerchantOfferResult(StatusCode statusCode, String errorMessage) {
        this.stroemMerchantSession = null;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    /**
     * @return true if the protocol session was estabilshed
     */
    public boolean isOk() {
        return this.statusCode == StatusCode.OK;
    }

    public StroemMerchantSession getStroemMerchantSession() {
        return stroemMerchantSession;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
