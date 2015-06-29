package io.stroem.clientj.domain;

import io.stroem.clientj.StroemClientUtil;
import org.bitcoinj.core.Sha256Hash;

import java.net.URI;

/**
 * Is used to identify a payment channel.
 *
 * Note: it is somewhat confusing that the Id of the payment channel is based on the server
 * since there can be many old/closed channels directed to this server (but only one active).
 */
public class StroemId {

  protected String serverId;

  public String getServerId() {
    return serverId;
  }

  /**
   * @return The real ID of the payment channel, used internally by bitcoinj.
   */
  public Sha256Hash getRealPaymentChannelServerId() {
    return StroemClientUtil.makeServerIdFromString(this.serverId);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StroemId stroemId = (StroemId) o;

    if (serverId != null ? !serverId.equals(stroemId.serverId) : stroemId.serverId != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return serverId != null ? serverId.hashCode() : 0;
  }

  @Override
  public String toString() {
    return "StroemId{" +
        "serverId='" + serverId + '\'' +
        '}';
  }
}
