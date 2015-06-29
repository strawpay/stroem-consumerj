package io.stroem.clientj.domain;

import io.stroem.clientj.StroemClientUtil;
import org.bitcoinj.core.Sha256Hash;

import java.net.URI;

/**
 * Is used to identify a payment channel, based on the host name of the issuer.
 *
 * We recommend you to use this class if you are building a normal wallet.
 */
public class StroemIdSimple extends StroemId {
  private String issuerUriHost;

  /**
   * @param issuerUri - The simple way is to only use the Issuer's URI host as ID, recommended for normal wallets
   */
  StroemIdSimple(URI issuerUri) {
    this.issuerUriHost = issuerUri.getHost();
  }

  public String getIssuerUriHost() {
    return issuerUriHost;
  }


}
