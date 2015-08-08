package io.stroem.consumerj.issuer;

import io.stroem.api.Messages;
import io.stroem.promissorynote.PaymentInstrument;
import io.stroem.proto.StroemProtos;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

/**
 * <p>This class is supposed to be used by the wallet to get the hash to sign, and to use
 * this signature when signing over the promissory note (negotiate it) to the merchant:</p>
 * <ul>
 * <li>Call getHashToSign() to get the hash </li>
 * <li>Call negotiate() to sign over the promissory note</li>
 * </ul>
 * <p>(This is just a wrapper around the NegotiateInfo trait in Stroem)</p>
 *
 * @since 0.0.1
 */
public class StroemNegotiator {

  PaymentInstrument.NegotiateInfo negotiationInfo;

  /**
   * Constructor
   *
   * @param negotiationInfo A Scala object that this Java class wraps
   */
  public StroemNegotiator(PaymentInstrument.NegotiateInfo negotiationInfo) {
    this.negotiationInfo = negotiationInfo;
  }

  /**
   * Use this method to get the hash of the blockNegotiationInfo. This hash must be signed by the wallet
   *
   * @return The hash you must sign
   */
  public Sha256Hash getHashToSign() {
    return (Sha256Hash) negotiationInfo.hashToSign();
  }

  /**
   * Use this method to generate the message we should send to the merchant
   *
   * @param hashSignature The signature of the hash
   * @return A Stroem Message that should be sent to the merchant
   */
  public StroemProtos.StroemMessage negotiate(ECKey.ECDSASignature hashSignature) {
    PaymentInstrument.PromissoryNote promissoryNote = negotiationInfo.negotiate(hashSignature);
    return Messages.newPromissoryNoteMessage(promissoryNote);
  }
}
