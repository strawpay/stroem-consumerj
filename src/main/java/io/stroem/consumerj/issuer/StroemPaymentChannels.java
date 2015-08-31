package io.stroem.consumerj.issuer;

import io.stroem.consumerj.persistence.proto.StroemPCWrapperProtos.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>StroemPaymentChannels to provide the following :</p>
 * <ul>
 * <li>Holds a collection of all (existing) Stroem payment channels. </li>
 * <li>Keeps track of the channels that are (probably) open</li>
 * <li>Keeps track of the preferred channel</li>
 * </ul>
 *
 */
public class StroemPaymentChannels {

    // All the channels in the wallet
    private final Map<String, StroemPaymentChannelComplete> allStroemPaymentChannelMap;

    // Note that this info might get in a few hours, as channels time out
    private Map<StroemId, StroemPaymentChannelComplete> openStroemPaymentChannelMap;

    // This is the channel that the consumer probably would like to use.
    private StroemPaymentChannelComplete preferredStroemPaymentChannel;

    public StroemPaymentChannels() {
        allStroemPaymentChannelMap = new HashMap<String, StroemPaymentChannelComplete>();
        preferredStroemPaymentChannel = null;
    }

    public void addStroemPaymentChannel(StroemPaymentChannelComplete stroemPaymentChannel) {
        allStroemPaymentChannelMap.put(stroemPaymentChannel.getHash(), stroemPaymentChannel);
    }

    public void setPreferred(StroemPaymentChannelComplete stroemPaymentChannel) {
        preferredStroemPaymentChannel = stroemPaymentChannel;
    }

    public StroemPaymentChannels(StroemPaymentChannelProtos stroemPaymentChannelProtos) {
        Map<String, StroemPaymentChannelComplete> resultMap = new HashMap<String, StroemPaymentChannelComplete>();


        PreferredStroemPaymentChannelProto preferredStroemPaymentChannelProto = stroemPaymentChannelProtos.getPreferredChannel();
        String preferredHash =  null;
        if (preferredStroemPaymentChannelProto != null) {
            preferredHash = preferredStroemPaymentChannelProto.getHash();
        }

        for (StroemPaymentChannelProto stroemPaymentChannelProto : stroemPaymentChannelProtos.getStroemPaymentChannelList()) {
            StroemPaymentChannelComplete stroemPaymentChannel = StroemPaymentChannelComplete.buildFrom(stroemPaymentChannelProto);
            resultMap.put(stroemPaymentChannelProto.getHash(), stroemPaymentChannel);
            if (preferredHash != null && preferredHash.equalsIgnoreCase(stroemPaymentChannelProto.getHash())) {
                preferredStroemPaymentChannel = stroemPaymentChannel;
            }
        }

        this.allStroemPaymentChannelMap = resultMap;

    }

    public StroemPaymentChannelComplete getPreferredStroemPaymentChannel() {
        return preferredStroemPaymentChannel;
    }

    public StroemPaymentChannelComplete getStroemPaymentChannel(String hash) {
        return allStroemPaymentChannelMap.get(hash);
    }

    public Collection<StroemPaymentChannelComplete> getAllStroemPaymentChannels() {
        return allStroemPaymentChannelMap.values();
    }
}
