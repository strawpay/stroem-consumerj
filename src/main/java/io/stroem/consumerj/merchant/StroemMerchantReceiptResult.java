package io.stroem.consumerj.merchant;

import io.stroem.consumerj.StroemMerchantSession;
import io.stroem.consumerj.issuer.StroemPaymentReceipt;

/**
 * Holds the payment receipt or an error, sent from the Merchant
 *
 * The reason for this wrapping is that it's hard to throw Exceptions when dealing with futures.
 */
public class StroemMerchantReceiptResult {

    public static enum StatusCode {
        OK(1),                   // Protocol timeout occurred (one party hung).
        INVALID_URI(2),          // The URI given was not a real URI
        PAYMENT_SERVER_DOWN(3),  // No response on the URI
        PAYMENT_SERVER_RESPONDS_WITH_ERROR_CODE(4),
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

    private final StroemPaymentReceipt stroemPaymentReceipt;


    public StroemMerchantReceiptResult(StroemPaymentReceipt stroemPaymentReceipt) {
        this.stroemPaymentReceipt = stroemPaymentReceipt;
        this.statusCode = StatusCode.OK;
        this.errorMessage = null;
    }

    public StroemMerchantReceiptResult(StatusCode statusCode, String errorMessage) {
        this.stroemPaymentReceipt = null;
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
    }

    /**
     * @return true if the protocol session was estabilshed
     */
    public boolean isOk() {
        return this.statusCode == StatusCode.OK;
    }

    public StroemPaymentReceipt getStroemPaymentReceipt() {
        return stroemPaymentReceipt;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}