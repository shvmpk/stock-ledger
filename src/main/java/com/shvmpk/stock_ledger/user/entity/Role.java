package com.shvmpk.stock_ledger.user.entity;

public enum Role {
    OWNER,
    STAFF;

    public String toAuthority() {
        return "ROLE_" + name();
    }
}
