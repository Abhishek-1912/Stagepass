package com.stagepass.common.security;

import com.stagepass.common.error.ErrorCode;
import org.springframework.security.core.AuthenticationException;

/** Authentication failure that carries the Problem Details code the entry point should answer with. */
public class StagepassAuthenticationException extends AuthenticationException {

    private final ErrorCode code;

    public StagepassAuthenticationException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
