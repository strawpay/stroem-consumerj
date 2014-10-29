package io.stroem.clientj.domain;

import com.google.protobuf.ByteString;
import io.stroem.proto.Stroem;


/**
 * Describes a participant in the Stroem network. Has a name and a public key.
 */
public class StroemEntity {

  private final String name; // Could be a company name.
  private final byte[] publicKey; // The public key is just an array of bytes

  public StroemEntity(String name, byte[] publicKey) {
    this.name = name;
    this.publicKey = publicKey;
  }

  public StroemEntity(Stroem.Entity entity) {
    this(entity.getName(), entity.getPublicKey().toByteArray());
  }

  public String getName() {
    return name;
  }

  public byte[] getPublicKey() {
    return publicKey;
  }

  public Stroem.Entity buildProtoBufObject() {
    ByteString entityPubKey = ByteString.copyFrom(publicKey);
    return Stroem.Entity.newBuilder()
        .setName(name)
        .setPublicKey(entityPubKey)
        .build();
  }

}
