package io.stroem.clientj.domain;

/**
 * <p>StroemPaymentHash to provide the following :</p>
 * <ul>
 * <li>A wrapper for hash value</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemPaymentHash {

  private final long hashValue;

  public StroemPaymentHash(long hashValue) {
    this.hashValue = hashValue;
  }

  public long getHashValue() {
    return hashValue;
  }
}
