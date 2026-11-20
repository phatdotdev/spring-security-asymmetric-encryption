package com.doph.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    USER_NOT_FOUND("USER_NOT_FOUND", "User not found with id %s", HttpStatus.NOT_FOUND),
    CHANGE_PASSWORD_MISMATCH("CHANGE_PASSWORD_MISMATCH", "New password and confirm password are not the same", HttpStatus.BAD_REQUEST),
    INVALID_CURRENT_PASSWORD("INVALID_CURRENT_PASSWORD", "Current password is invalid", HttpStatus.BAD_REQUEST),
    THE_ACCOUNT_ALREADY_DEACTIVATED("THE_ACCOUNT_ALREADY_DEACTIVATED", "The account is already deactivated", HttpStatus.BAD_REQUEST ),
    THE_ACCOUNT_ALREADY_ACTIVATED("THE_ACCOUNT_ALREADY_ACTIVATED", "The account is already activated" , HttpStatus.BAD_REQUEST );

    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(
            final String code,
            final String defaultMessage,
            final HttpStatus status
    ){
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }

}
