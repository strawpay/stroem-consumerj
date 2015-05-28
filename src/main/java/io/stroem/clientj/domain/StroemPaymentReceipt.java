package io.stroem.clientj.domain;

import org.bitcoinj.core.ECKey;

import java.util.Date;

/**
 * <p>StroemPaymentReceipt to provide the following :</p>
 * <ul>
 * <li>A DTO for the confirmation data given by the merchant server after the purchase.</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemPaymentReceipt {

  private final StroemPaymentHash stroemPaymentHash;
  private final Date signedAt;
  private final ECKey.ECDSASignature signature;

  public StroemPaymentReceipt(StroemPaymentHash stroemPaymentHash, Date signedAt, ECKey.ECDSASignature signature) {
    this.stroemPaymentHash = stroemPaymentHash;
    this.signedAt = signedAt;
    this.signature = signature;
  }

  public StroemPaymentHash getStroemPaymentHash() {
    return stroemPaymentHash;
  }

  public Date getSignedAt() {
    return signedAt;
  }

  public ECKey.ECDSASignature getSignature() {
    return signature;
  }
}
