package io.stroem.clientj.persistence;

import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import io.stroem.clientj.domain.StroemPaymentChannel;
import io.stroem.clientj.domain.StroemPaymentChannels;
import io.stroem.clientj.persistence.proto.StroemPCWrapperProtos;
import org.bitcoinj.core.Coin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * <p>StroemPaymentChannelRepository to provide the following :</p>
 * <ul>
 * <li>Read Stroem payment channels from stream</li>
 * <li>Write Stroem payment channels to stream</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemPaymentChannelRepository {

  public static final long PROTO_ABSENT_VALUE = -1;
  public static final String PROTO_ABSENT_STRING = "absent";


  /**
   * Parses a StroemPaymentChannels from the given stream.
   *
   * A StroemPaymentChannels db can be unreadable for various reasons, such as inability to open the file, corrupt data,
   * internally inconsistent data.
   *
   * @throws StroemPaymentChannelsLoadException thrown in various error conditions (see description).
   */
  public StroemPaymentChannels readPaymentChannels(InputStream input) throws StroemPaymentChannelsLoadException {
    try {
      StroemPCWrapperProtos.StroemPaymentChannelProtos protos = parseToProto(input);
      return new StroemPaymentChannels(protos);
    } catch (IOException e) {
      throw new StroemPaymentChannelsLoadException("Could not parse input stream to protobuf", e);
    }
  }

  /**
   * Returns the loaded protocol buffer from the given byte stream. This method is designed for low level work involving the
   * wallet file format itself.
   */
  public static StroemPCWrapperProtos.StroemPaymentChannelProtos parseToProto(InputStream input) throws IOException {
    return StroemPCWrapperProtos.StroemPaymentChannelProtos.parseFrom(input);
  }

  /**
   * Formats the given PaymentChannels to the given output stream in protocol buffer format.
   */
  public void writePaymentChannels(StroemPaymentChannels stroemPaymentChannels, OutputStream output) throws IOException {
    StroemPCWrapperProtos.StroemPaymentChannelProtos paymentChannelsProtos = paymentChannelsToProto(stroemPaymentChannels);
    paymentChannelsProtos.writeTo(output);
  }

  /**
   * Converts the given paymentChannelInfos to the object representation of the protocol buffers. This can be modified, or
   * additional data fields set, before serialization takes place.
   */
  public StroemPCWrapperProtos.StroemPaymentChannelProtos paymentChannelsToProto(StroemPaymentChannels stroemPaymentChannels) {
    StroemPCWrapperProtos.StroemPaymentChannelProtos.Builder builder = StroemPCWrapperProtos.StroemPaymentChannelProtos.newBuilder();

    Preconditions.checkNotNull(stroemPaymentChannels, "StroemPaymentChannels must be specified");

    for (StroemPaymentChannel channel : stroemPaymentChannels.getAllStroemPaymentChannels()) {
      StroemPCWrapperProtos.StroemPaymentChannelProto paymentChannelInfoProto = makeStroemPaymentChannelProto(channel);
      builder.addStroemPaymentChannel(paymentChannelInfoProto);
    }

    StroemPaymentChannel preferred = stroemPaymentChannels.getPreferredStroemPaymentChannel();
    builder.setPreferredChannel(makePreferredStroemPaymentChannelProto(preferred.getHash(), preferred.getIssuerName()));

    return builder.build();
  }

  private StroemPCWrapperProtos.PreferredStroemPaymentChannelProto makePreferredStroemPaymentChannelProto(String hash, String issuerName) {
    StroemPCWrapperProtos.PreferredStroemPaymentChannelProto.Builder builder = StroemPCWrapperProtos.PreferredStroemPaymentChannelProto.newBuilder();
    builder.setHash(hash);
    builder.setIssuerName(issuerName);
    return builder.build();
  }

  private StroemPCWrapperProtos.StroemPaymentChannelProto makeStroemPaymentChannelProto(StroemPaymentChannel channel) {
    StroemPCWrapperProtos.StroemPaymentChannelProto.Builder builder = StroemPCWrapperProtos.StroemPaymentChannelProto.newBuilder();

    // ------- Required fields -----
    builder.setHash(channel.getHash());
    builder.setIssuerUri(channel.getIssuerUri());
    builder.setIssuerName(channel.getIssuerName());
    builder.setMaxValue(channel.getMaxValue().getValue());
    builder.setTimeoutSeconds(channel.getTimeoutSeconds());
    ByteString byteString = ByteString.copyFrom(channel.getPublicEcKey());
    builder.setPublicEcKey(byteString);

    // -- Optional fielts ---------
    // Server Id
    if (channel.getServerId() != null) {
      builder.setServerId(channel.getServerId());
    } else {
      builder.setServerId(PROTO_ABSENT_STRING);
    }

    // Miner fee
    Coin minerFee = channel.getMinerFee();
    if (minerFee != null) {
      builder.setMinerFee(minerFee.longValue());
    } else {
      builder.setMinerFee(PROTO_ABSENT_VALUE);
    }

    // Fiat value
    if (channel.getFiatMaxValue() != null) {
      builder.setFiatMaxValue(channel.getFiatMaxValue());
    } else {
      builder.setFiatMaxValue(PROTO_ABSENT_VALUE);
    }

    // Fiat currency
    if (channel.getFiatCurrency() != null) {
      builder.setFiatCurrency(channel.getFiatCurrency());
    } else {
      builder.setFiatCurrency(PROTO_ABSENT_STRING);
    }

    // Note
    if (channel.getNote() != null) {
      builder.setNote(channel.getNote());
    } else {
      builder.setFiatCurrency(PROTO_ABSENT_STRING);
    }

    return builder.build();
  }
}
