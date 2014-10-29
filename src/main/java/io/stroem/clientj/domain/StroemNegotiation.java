package io.stroem.clientj.domain;

import org.bitcoinj.core.Sha256Hash;
import com.google.protobuf.ByteString;

/**
 * A negotiation, i.e. one of the required signatures.
 */
public class StroemNegotiation {

  private final StroemPublicKey toTheOrderOf;
  private final StroemPaymentInfo paymentInfo; // Not signed, can be redacted but if present hash(paymentInfo) == paymentInfoHash
  private final Sha256Hash paymentInfoHash; // Signed
  private final ByteString signature;

  public StroemNegotiation(StroemPublicKey toTheOrderOf, StroemPaymentInfo paymentInfo, Sha256Hash paymentInfoHash, ByteString signature) {
    this.toTheOrderOf = toTheOrderOf;
    this.paymentInfo = paymentInfo;
    this.paymentInfoHash = paymentInfoHash;
    this.signature = signature;
  }

}
