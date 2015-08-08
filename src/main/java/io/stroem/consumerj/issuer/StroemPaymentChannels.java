package io.stroem.consumerj.issuer;

import io.stroem.consumerj.persistence.proto.StroemPCWrapperProtos.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>StroemPaymentChannels to provide the following :</p>
 * <ul>
 * <li>Holds a collection of all Stroem payment channels. </li>
 * </ul>
 *
 * @since 0.0.1
 */
public class StroemPaymentChannels {

  // All the channels in the wallet
  private final Map<String, StroemPaymentChannel> allStroemPaymentChannelMap;

  // Note that this info might get in a few hours, as channels time out
  private Map<StroemId, StroemPaymentChannel> openStroemPaymentChannelMap;

  private StroemPaymentChannel preferredStroemPaymentChannel;

  public StroemPaymentChannels() {
    allStroemPaymentChannelMap = new HashMap<String, StroemPaymentChannel>();
    preferredStroemPaymentChannel = null;
  }

  public void addStroemPaymentChannel(StroemPaymentChannel stroemPaymentChannel) {
    allStroemPaymentChannelMap.put(stroemPaymentChannel.getHash(), stroemPaymentChannel);
  }

  public void setPreferred(StroemPaymentChannel stroemPaymentChannel) {
    preferredStroemPaymentChannel = stroemPaymentChannel;
  }

  public StroemPaymentChannels(StroemPaymentChannelProtos stroemPaymentChannelProtos) {
    Map<String, StroemPaymentChannel> resultMap = new HashMap<String, StroemPaymentChannel>();


    PreferredStroemPaymentChannelProto preferredStroemPaymentChannelProto = stroemPaymentChannelProtos.getPreferredChannel();
    String preferredHash =  null;
    if (preferredStroemPaymentChannelProto != null) {
      preferredHash = preferredStroemPaymentChannelProto.getHash();
    }

    for (StroemPaymentChannelProto stroemPaymentChannelProto : stroemPaymentChannelProtos.getStroemPaymentChannelList()) {
      StroemPaymentChannel stroemPaymentChannel = new StroemPaymentChannel(stroemPaymentChannelProto);
      resultMap.put(stroemPaymentChannelProto.getHash(), stroemPaymentChannel);
      if (preferredHash != null && preferredHash.equalsIgnoreCase(stroemPaymentChannelProto.getHash())) {
        preferredStroemPaymentChannel = stroemPaymentChannel;
      }
    }

    this.allStroemPaymentChannelMap = resultMap;

  }

  public StroemPaymentChannel getPreferredStroemPaymentChannel() {
    return preferredStroemPaymentChannel;
  }

  public StroemPaymentChannel getStroemPaymentChannel(String hash) {
    return allStroemPaymentChannelMap.get(hash);
  }

  public Collection<StroemPaymentChannel> getAllStroemPaymentChannels() {
    return allStroemPaymentChannelMap.values();
  }
}
