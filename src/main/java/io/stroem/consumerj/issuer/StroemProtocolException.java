package io.stroem.consumerj.issuer;

/**
 * An exception for Stroem protocol errors, in connection to the issuer
 */
public class StroemProtocolException extends Exception {
  public static enum Code {
    TIMEOUT(1),                   // Protocol timeout occurred (one party hung).
    SYNTAX_ERROR(2),              // Generic error indicating some message was not properly formatted or was out of order.
    NO_ACCEPTABLE_VERSION(3),     // We don't speak the version the other side asked for.
    DURATION_UNACCEPTABLE(4),     // Request duration to long or short
    WRONG_ISSUER_PUBLICKEY(5),    // The issuer can to use requested issuer public key

    OTHER(8);

    int id;
    Code(int id) {
      this.id = id;
    }

    static Code fromId(int id) {
      for (Code code : Code.values())
        if (code.id == id)
          return code;
      return OTHER;
    }
  }

  private Code code;

  public StroemProtocolException(String msg) {
    super(msg);
    code = Code.OTHER;
  }

  public StroemProtocolException(Code code, String msg) {
    super(msg);
    this.code = code;
  }

  public Code getCode() {
    return code;
  }
}
