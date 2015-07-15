package io.stroem.clientj.domain;

import java.net.URI;

/**
 * Is used to identify a payment channel, based on the host name of the issuer.
 *
 * We recommend you to use this class if you are building a normal wallet.
 */
public class StroemIdSimple extends StroemId {
  private URI uri;

  /**
   * @param issuerUri - The simple way is to only use the Issuer's URI host as ID, recommended for normal wallets
   */
  public StroemIdSimple(URI issuerUri) {
    this.uri = issuerUri;
    this.serverId = issuerUri.getHost();
  }

  public String getIssuerUriHost() {
    return serverId;
  }

  public URI getIssuerURI() {
    return uri;
  }


}
