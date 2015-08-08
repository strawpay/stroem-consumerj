package io.stroem.consumerj.issuer;

import com.google.protobuf.ByteString;

/**
 *  There are many different kinds of public keys
 *  TODO: This will be a class hierarchy
 */
public class StroemPublicKey {
  private ByteString key;
  private String type;

  public StroemPublicKey(ByteString key, String type) {
    this.key = key;
    this.type = type;
  }

  public ByteString getKey() {
    return key;
  }

  public String getType() {
    return type;
  }
}
