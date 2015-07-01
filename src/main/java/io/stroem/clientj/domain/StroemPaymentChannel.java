package io.stroem.clientj.domain;

import io.stroem.clientj.persistence.StroemPaymentChannelRepository;
import io.stroem.clientj.persistence.proto.StroemPCWrapperProtos;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

/**
 * <p>StroemPaymentChannel to provide the following :</p>
 * <ul>
 * <li>Additional information related to a Stroem payment channel that is not stored in (or hard to get from)
 *     the bitcoinj payment channel</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemPaymentChannel {




  // ----------------- Requered fields ------------------------

  /**
   * The "soft" id of the channel, Many channels can have the same id, but only one of them can open channel at the same time.
   */
  private StroemId stroemId;

  /**
   * The "hard" id of the channel. The hash of the founding/contract transaction.
   */
  private String hash;

  /**
   * We will use the issuerUri as the key to find the Stroem issuer instance, if present
   */
  private String issuerUri;

  /**
   * This name is given to us (by the issuer) when a payment channel has been opened.
   */
  private String issuerName;


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
   * The payment channels serverId. If not present,
   * serverId will be set to the host name of the issuer uri.
   */
  @Nullable
  private String serverId;

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
   * Construct from proto (from disk)
   *
   * @param proto
   */
  public StroemPaymentChannel(StroemPCWrapperProtos.StroemPaymentChannelProto proto) {

    hash = proto.getHash();
    issuerUri = proto.getIssuerUri();
    issuerName = proto.getIssuerName();
    maxValue = Coin.valueOf(proto.getMaxValue());
    timeoutSeconds = proto.getTimeoutSeconds();
    publicEcKey = proto.getPublicEcKey().toByteArray();

    // Server id
    if (proto.hasServerId()) {
      if (!proto.getServerId().equals(StroemPaymentChannelRepository.PROTO_ABSENT_STRING)) {
        serverId = proto.getServerId();
      }
    }

    // Miner fee
    if (proto.hasMinerFee()) {
      long minerFeeLong = proto.getMinerFee();
      if (minerFeeLong != StroemPaymentChannelRepository.PROTO_ABSENT_VALUE) {
        minerFee = Coin.valueOf(minerFeeLong);
      }
    }

    // Fiat value
    if (proto.hasFiatMaxValue()) {
      double value = proto.getFiatMaxValue();
      if (value > 0) {
        fiatMaxValue = new Double(value);
      }
    }

    // Fiat currency
    if (proto.hasFiatCurrency()) {
      if (!proto.getFiatCurrency().equals(StroemPaymentChannelRepository.PROTO_ABSENT_STRING)) {
        fiatCurrency = proto.getFiatCurrency();
      }
    }

    // Note
    if (proto.hasNote()) {
      if (!proto.getNote().equals(StroemPaymentChannelRepository.PROTO_ABSENT_STRING)) {
        note = proto.getNote();
      }
    }
  }

  /**
   * Constructor used mainly when constructing a completely new Stroem payment channel.
   */
  public StroemPaymentChannel(StroemId stroemId, String hash, String issuerUri, String issuerName, Coin maxValue,
                              long timeoutSeconds, byte[] publicEcKey, @Nullable String serverId, @Nullable Coin minerFee,
                              @Nullable Double fiatMaxValue, @Nullable String fiatCurrency, @Nullable String note) {
    this.stroemId = stroemId;
    this.hash = hash;
    this.issuerUri = issuerUri;
    this.issuerName = issuerName;
    this.maxValue = maxValue;
    this.timeoutSeconds = timeoutSeconds;
    this.publicEcKey = publicEcKey;
    this.serverId = serverId;
    this.minerFee = minerFee;
    this.fiatMaxValue = fiatMaxValue;
    this.fiatCurrency = fiatCurrency;
    this.note = note;
  }

  public StroemId getStroemId() {
    return stroemId;
  }

  public String getHash() {
    return hash;
  }

  public String getIssuerUri() {
    return issuerUri;
  }

  public String getIssuerHost() {
    try {
      URI uri = new URI(issuerUri);
      return uri.getHost();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("URI should be valid by now: " + issuerUri);
    }
  }

  public String getIssuerName() {
    return issuerName;
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
  public String getServerId() {
    return serverId;
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

    StroemPaymentChannel that = (StroemPaymentChannel) o;

    if (!hash.equals(that.hash)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return hash.hashCode();
  }

  @Override
  public String toString() {
    return "StroemPaymentChannel{" +
        "stroemId=" + stroemId +
        ", hash='" + hash + '\'' +
        ", issuerUri='" + issuerUri + '\'' +
        ", issuerName='" + issuerName + '\'' +
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
