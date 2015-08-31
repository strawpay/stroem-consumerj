package io.stroem.consumerj.issuer;

import io.stroem.proto.StroemProtos;

/**
 * An exception for Stroem protocol errors, in connection to the issuer
 */
public class StroemProtocolException extends Exception {

    private StroemProtos.Error.ErrorCode code;

    public StroemProtocolException(String msg) {
        super(msg);
        code = StroemProtos.Error.ErrorCode.OTHER;
    }

    public StroemProtocolException(StroemProtos.Error.ErrorCode code, String msg) {
        super(msg);
        this.code = code;
    }

    public StroemProtos.Error.ErrorCode getCode() {
        return code;
    }
}
