package io.stroem.consumerj.issuer;

import io.stroem.consumerj.persistence.StroemPaymentChannelRepository;
import io.stroem.consumerj.persistence.proto.StroemPCWrapperProtos;
import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;

/**
 * This class holds some additional fields that must be present for existing
 * Stroem payment channels.
 *
 * When you read a Stroem payment channel configuration from a repository,
 * this is what you get - the complete object.
 */
public class StroemPaymentChannelComplete extends StroemPaymentChannelConfiguration {

    /**
     * The "hard" id of the channel. The hash of the founding/contract transaction.
     *
     * This field must be present for any existing payment channel,
     * but (obviously) can't be set before channel creation since the funding transaction doesn't exist.
     */
    private String hash;

    /**
     * This name is given to us (by the issuer) when a payment channel has been opened.
     */
    private String issuerName;


    /**
     * Use one of the other constructors  (still, no need to make this private)
     */
    public StroemPaymentChannelComplete(String hash, String issuerName, String issuerUri, String serverId, Coin maxValue,
                                         long timeoutSeconds, byte[] publicEcKey, @Nullable Coin minerFee,
                                         @Nullable Double fiatMaxValue, @Nullable String fiatCurrency, @Nullable String note) {
        super(issuerUri, serverId, maxValue,
                timeoutSeconds, publicEcKey, minerFee,
                fiatMaxValue, fiatCurrency, note);
        this.hash = hash;
        this.issuerName = issuerName;
    }

    /**
     * Construct from a previous (non existing) channel configuration
     *
     * @param hash
     * @param issuerName
     * @param channel
     * @return
     */
    public static StroemPaymentChannelComplete buildFrom(String hash, String issuerName, StroemPaymentChannelConfiguration channel) {
        return new StroemPaymentChannelComplete(hash, issuerName, channel.getIssuerUri(), channel.getServerId(),
                channel.getMaxValue(), channel.getTimeoutSeconds(), channel.getPublicEcKey(), channel.getMinerFee(),
                channel.getFiatMaxValue(), channel.getFiatCurrency(), channel.getNote());
    }

   /**
    * Construct from proto (from disk)
    *
    * @param proto - This is the generated object we get after parsing a binary stream containing Stroem payment channels.
    */
    public static StroemPaymentChannelComplete buildFrom(StroemPCWrapperProtos.StroemPaymentChannelProto proto) {
        String hash = proto.getHash();
        String issuerName = proto.getIssuerName();
        String issuerUri = proto.getIssuerUri();
        Coin maxValue = Coin.valueOf(proto.getMaxValue());
        long timeoutSeconds = proto.getTimeoutSeconds();
        byte[] publicEcKey = proto.getPublicEcKey().toByteArray();

        @Nullable String serverId = null;
        @Nullable Coin minerFee = null;
        @Nullable Double fiatMaxValue = null;
        @Nullable String fiatCurrency = null;
        @Nullable String note = null;

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

        return new StroemPaymentChannelComplete(hash, issuerName, issuerUri, serverId, maxValue,
                timeoutSeconds, publicEcKey, minerFee, fiatMaxValue, fiatCurrency, note);
    }



    public String getHash() {
        return hash;
    }


    public String getIssuerName() {
        return issuerName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        StroemPaymentChannelComplete that = (StroemPaymentChannelComplete) o;

        if (!hash.equals(that.hash)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }
}
