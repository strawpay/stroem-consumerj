package io.stroem.consumerj.issuer;

import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

/**
 * <p>StroemPaymentChannelConfiguration to provide the following :</p>
 * <ul>
 *   <li>Additional information related to a Stroem payment channel that is not stored in (or hard to get from)
 *     the bitcoinj payment channel</li>
 *   <li>Use this object to create a new Stroem payment channel.</li>
 * </ul>
 *
 *
 */
public class StroemPaymentChannelConfiguration {


  // ----------------- Required fields ------------------------

  /**
   * We will use the issuerUri as the key to find the Stroem issuer instance
   * (if there is an issuer instance present)
   */
  private String issuerUri;

  /**
   * The payment channel's serverId.
   * The "soft" id of the channel.
   * Many channels can have the same server id, but only one of them can open channel at the same time.
   *
   * Often, the serverId will be set to the host name of the issuer uri.
   */
  private String serverId;

  /**
   * How much value (in satoshis) is locked up into the channel.
   */
  private Coin maxValue;

  /**
   * The duration of the payment channel, in seconds
   */
  private long timeoutSeconds;

  /**
   * The public part of the ECKey used during channel creation.
   */
  private byte[] publicEcKey;


  // ----------------- Optional fields ------------------------
  /**
   * A wrapper for the serverId (and sometimes issuerUri)
   */
  @Nullable
  private StroemId stroemId;

  /**
   * The miner's fee added in satoshi, otherwise Optional.absent()
   */
  @Nullable
  private Coin minerFee;

  /**
   * The fiat value corresponding to the max value of the channel.
   * Used if you want to record this amount for accounting.
   */
  @Nullable
  private Double fiatMaxValue;

  /**
   * The name of the fiat currence of "fiat_max_value"
   */
  @Nullable
  private String fiatCurrency;

  /**
   * A note can be added when the payment channel is created
   */
  @Nullable
  private String note;


  /**
   * Constructor used when constructing a completely new Stroem payment channel.
   * Using the StroemIdSimple for identification of the channel.
   */
  public StroemPaymentChannelConfiguration(StroemIdSimple stroemId, Coin maxValue,
                                           long timeoutSeconds, byte[] publicEcKey, @Nullable Coin minerFee,
                                           @Nullable Double fiatMaxValue, @Nullable String fiatCurrency, @Nullable String note) {
    this(stroemId.getIssuerURI().toString(), stroemId.getServerId(), maxValue,
      timeoutSeconds, publicEcKey, minerFee,
      fiatMaxValue, fiatCurrency, note);
    this.stroemId = stroemId;
  }

  /**
   * Constructor used when constructing a completely new Stroem payment channel.
   */
  public StroemPaymentChannelConfiguration(String issuerUri, String serverId, Coin maxValue,
                                           long timeoutSeconds, byte[] publicEcKey, @Nullable Coin minerFee,
                                           @Nullable Double fiatMaxValue, @Nullable String fiatCurrency, @Nullable String note) {
    this.issuerUri = issuerUri;
    this.serverId = serverId;
    this.maxValue = maxValue;
    this.timeoutSeconds = timeoutSeconds;
    this.publicEcKey = publicEcKey;
    this.minerFee = minerFee;
    this.fiatMaxValue = fiatMaxValue;
    this.fiatCurrency = fiatCurrency;
    this.note = note;
  }


  public String getIssuerUri() {
    return issuerUri;
  }

  public String getServerId() {
    return serverId;
  }

  public String getIssuerHost() {
    try {
      URI uri = new URI(issuerUri);
      return uri.getHost();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("URI should be valid by now: " + issuerUri);
    }
  }


  public Coin getMaxValue() {
    return maxValue;
  }

  public long getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public byte[] getPublicEcKey() {
    return publicEcKey;
  }

  @Nullable
  public StroemId getStroemId() {
    return stroemId;
  }

  @Nullable
  public Coin getMinerFee() {
    return minerFee;
  }

  @Nullable
  public Double getFiatMaxValue() {
    return fiatMaxValue;
  }

  @Nullable
  public String getFiatCurrency() {
    return fiatCurrency;
  }

  @Nullable
  public String getNote() {
    return note;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StroemPaymentChannelConfiguration that = (StroemPaymentChannelConfiguration) o;

    if (timeoutSeconds != that.timeoutSeconds) return false;
    if (issuerUri != null ? !issuerUri.equals(that.issuerUri) : that.issuerUri != null) return false;
    if (serverId != null ? !serverId.equals(that.serverId) : that.serverId != null) return false;
    if (maxValue != null ? !maxValue.equals(that.maxValue) : that.maxValue != null) return false;
    if (!Arrays.equals(publicEcKey, that.publicEcKey)) return false;
    if (stroemId != null ? !stroemId.equals(that.stroemId) : that.stroemId != null) return false;
    if (minerFee != null ? !minerFee.equals(that.minerFee) : that.minerFee != null) return false;
    if (fiatMaxValue != null ? !fiatMaxValue.equals(that.fiatMaxValue) : that.fiatMaxValue != null) return false;
    if (fiatCurrency != null ? !fiatCurrency.equals(that.fiatCurrency) : that.fiatCurrency != null) return false;
    return !(note != null ? !note.equals(that.note) : that.note != null);

  }

  @Override
  public int hashCode() {
    int result = issuerUri != null ? issuerUri.hashCode() : 0;
    result = 31 * result + (serverId != null ? serverId.hashCode() : 0);
    result = 31 * result + (maxValue != null ? maxValue.hashCode() : 0);
    result = 31 * result + (int) (timeoutSeconds ^ (timeoutSeconds >>> 32));
    result = 31 * result + (publicEcKey != null ? Arrays.hashCode(publicEcKey) : 0);
    result = 31 * result + (stroemId != null ? stroemId.hashCode() : 0);
    result = 31 * result + (minerFee != null ? minerFee.hashCode() : 0);
    result = 31 * result + (fiatMaxValue != null ? fiatMaxValue.hashCode() : 0);
    result = 31 * result + (fiatCurrency != null ? fiatCurrency.hashCode() : 0);
    result = 31 * result + (note != null ? note.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "StroemPaymentChannelConfiguration{" +
        "stroemId=" + stroemId +
        ", issuerUri='" + issuerUri + '\'' +
        ", maxValue=" + maxValue +
        ", timeoutSeconds=" + timeoutSeconds +
        ", publicEcKey=" + Arrays.toString(publicEcKey) +
        ", serverId='" + serverId + '\'' +
        ", minerFee=" + minerFee +
        ", fiatMaxValue=" + fiatMaxValue +
        ", fiatCurrency='" + fiatCurrency + '\'' +
        ", note='" + note + '\'' +
        '}';
  }
}
