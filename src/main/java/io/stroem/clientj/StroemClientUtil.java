package io.stroem.clientj;

import org.bitcoinj.core.Sha256Hash;

/**
 * 
 * Some useful methods specific to Stroem clients
 *
 */
public class StroemClientUtil {

  /**
   * Use this method when you want to create a payment channel id from the server's host name
   */ 
  public static Sha256Hash makeServerIdFromString(String serverHost) {
    return Sha256Hash.create(serverHost.getBytes());
  }
}
