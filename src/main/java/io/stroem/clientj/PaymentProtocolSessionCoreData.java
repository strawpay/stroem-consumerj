package io.stroem.clientj;

import org.bitcoinj.core.Coin;

import org.bitcoin.protocols.payments.Protos;

import javax.annotation.Nullable;
import java.util.Date;

/**
 * These fields corresponds to the fields with the same name in
 * {@link org.bitcoinj.protocols.payments.PaymentSession} and {@link StroemPaymentProtocolSession}.
 *
 * (The aim is to move this class to bitcoinj and make {@link org.bitcoinj.protocols.payments.PaymentSession} extend it too)
 */
public class PaymentProtocolSessionCoreData {

    private Protos.PaymentRequest paymentRequest;

    private Coin totalValue;
    private Date creationDate;
    private Date expiryDate;
    private String memo;
    private String paymentUrl;

    private byte[] merchantData;

    public PaymentProtocolSessionCoreData(Protos.PaymentRequest paymentRequest) {
        this.paymentRequest = paymentRequest;
    }

    public PaymentProtocolSessionCoreData(Protos.PaymentRequest paymentRequest,
                                          Coin totalValue, Date creationDate, Date expiryDate, String memo, String paymentUrl,
                                          Protos.PaymentDetails paymentDetails, byte[] merchantData) {

        this.paymentRequest = paymentRequest;
        this.totalValue = totalValue;
        this.creationDate = creationDate;
        this.expiryDate = expiryDate;
        this.memo = memo;
        this.paymentUrl = paymentUrl;
        this.merchantData = merchantData;
    }

    public void init( Coin totalValue, Date creationDate, Date expiryDate, String memo, String paymentUrl,
                      Protos.PaymentDetails paymentDetails, byte[] merchantData) {

        this.totalValue = totalValue;
        this.creationDate = creationDate;
        this.expiryDate = expiryDate;
        this.memo = memo;
        this.paymentUrl = paymentUrl;
        this.merchantData = merchantData;
    }


    /**
     * Returns the protobuf that this object was instantiated with.
     *
     *  @return Note: this is NOT the same as Protos.PaymentRequest object, but will be after bitcoinj merge
     */
    public Protos.PaymentRequest getPaymentRequest() {
        return paymentRequest;
    }

    /**
     * Returns the memo included by the merchant in the payment request, or null if not found.
     */
    @Nullable
    public String getMemo() {
        return memo;
    }

    /**
     * Returns the total amount of bitcoins requested.
     */
    public Coin getValue() {
        return totalValue;
    }

    /**
     * Returns the date that the payment request was generated.
     * (Use new instance since Date is unreliable)
     */
    public Date getDate() {
        return new Date(creationDate.getTime());
    }

    /**
     * Returns the expires time of the payment request, or null if none.
     */
    @Nullable
    public Date getExpires() {
        return expiryDate;
    }

    /**
     * This should always be called before attempting to call sendPayment.
     */
    public boolean isExpired() {
        if (expiryDate != null) {
            return System.currentTimeMillis() > expiryDate.getTime();
        } else {
            return false;
        }
    }

    /**
     * Returns the payment url where the Payment message should be sent.
     * Returns null if no payment url was provided in the PaymentRequest.
     */
    @Nullable
    public String getPaymentUrl() {
        return paymentUrl;
    }

    /**
     * Returns the merchant data included by the merchant in the payment request, or null if none.
     */
    @Nullable
    public byte[] getMerchantData() {
        return merchantData;
    }

}
