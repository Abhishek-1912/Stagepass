package com.stagepass.common.error;

/** A business error that maps directly to a Problem Details response. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException conflict(String detail) {
        return new ApiException(ErrorCode.CONFLICT, detail);
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(ErrorCode.FORBIDDEN, detail);
    }
}
