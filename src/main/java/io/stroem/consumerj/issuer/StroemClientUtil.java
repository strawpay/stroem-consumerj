package io.stroem.consumerj.issuer;

import org.bitcoinj.core.Sha256Hash;

/**
 *
 * Some useful methods specific to Stroem clients
 *
 */
public class StroemClientUtil {

    /**
     * Use this method when you want to create a payment channel id from the server's id
     *
     * @param serverIdString A string unique for the server (could be Server's host name for example)
     */
    public static Sha256Hash makeServerIdFromString(String serverIdString) {
        return Sha256Hash.create(serverIdString.getBytes());
    }
}
