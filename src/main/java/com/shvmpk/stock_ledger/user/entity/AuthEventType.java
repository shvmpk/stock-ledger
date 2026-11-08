package com.shvmpk.stock_ledger.user.entity;

public enum AuthEventType {
    LOGIN_SUCCEEDED,
    LOGIN_FAILED,
    LOGOUT,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    USER_CREATED,
    USER_DEACTIVATED,
    USER_REACTIVATED,
    ROLE_CHANGED
}
