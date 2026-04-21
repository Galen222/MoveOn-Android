package com.proyecto.moveon.core.api;

/**
 * Enumeración con los valores posibles de api error type.
 */
public enum ApiErrorType {
    NETWORK,
    TIMEOUT,
    CANCELED,

    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    RATE_LIMIT,
    VALIDATION,
    PAYLOAD_TOO_LARGE,
    SERVER,

    PARSE,
    UNKNOWN
}