package io.stroem.consumerj.issuer;

public class WrongStroemServerVersionException extends Exception {

  public WrongStroemServerVersionException(String message) {
    super(message);
  }
}
