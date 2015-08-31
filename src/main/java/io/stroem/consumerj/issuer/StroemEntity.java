package io.stroem.consumerj.issuer;

import com.google.protobuf.ByteString;
import io.stroem.proto.StroemProtos;


/**
 * Describes a participant (a participant is a client, merchant or hub?) in the Stroem network. Has a name and a public key.
 */
public class StroemEntity {

    private final String name; // Could be a company name.
    private final byte[] publicKey; // The public key is just an array of bytes

    public StroemEntity(String name, byte[] publicKey) {
        this.name = name;
        this.publicKey = publicKey;
    }

    public StroemEntity(StroemProtos.Entity entity) {
        this(entity.getName(), entity.getPublicKey().toByteArray());
    }

    public String getName() {
        return name;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }

    public StroemProtos.Entity buildProtoBufObject() {
        ByteString entityPubKey = ByteString.copyFrom(publicKey);
        return StroemProtos.Entity.newBuilder()
                .setName(name)
                .setPublicKey(entityPubKey)
                .build();
    }

}
