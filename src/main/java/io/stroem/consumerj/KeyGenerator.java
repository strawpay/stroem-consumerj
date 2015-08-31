package io.stroem.consumerj;

import io.stroem.javaapi.JavaToScalaBridge;
import io.stroem.promissorynote.DigitalSignatureScheme;
import org.bitcoinj.core.ECKey;
import org.spongycastle.math.ec.ECPoint;

import java.math.BigInteger;

/**
 * Can help you get a key with a private part (say if you run a HD wallet).
 */
public class KeyGenerator {

    /**
     * @return an ECKey with a private part. This can be usseful since a deterministic key does not have private part.
     */
    public static ECKey generateKeyWithPrivatePart() {
        DigitalSignatureScheme.KeyPair keyPair = JavaToScalaBridge.generateKeyPair();
        BigInteger priv = (BigInteger) keyPair.privateKey();
        ECPoint pub = (ECPoint) keyPair.publicKey();
        return ECKey.fromPrivateAndPrecalculatedPublic(priv, pub);
    }
}
