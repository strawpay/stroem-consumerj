package io.stroem.consumerj.issuer;

import org.bitcoinj.protocols.payments.PaymentProtocolException;

/**
 * <p>StroemPaymentProtocolException to provide the following :</p>
 * <ul>
 * <li>Extra exception types to the PaymentProtocolException.</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemPaymentProtocolException extends PaymentProtocolException {
  public StroemPaymentProtocolException(String msg) {
    super(msg);
  }

  public StroemPaymentProtocolException(Exception e) {
    super(e);
  }
}
