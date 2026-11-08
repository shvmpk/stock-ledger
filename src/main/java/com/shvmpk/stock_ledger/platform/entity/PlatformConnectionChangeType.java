package com.shvmpk.stock_ledger.platform.entity;

/**
 * The kind of save that produced a history entry. A save that changes several
 * fields at once is one {@code UPDATED} entry whose {@code fields} list names
 * them; pausing, resuming, and testing are their own kinds because they are
 * their own actions.
 */
public enum PlatformConnectionChangeType {
    CREATED,
    UPDATED,
    PAUSED,
    RESUMED,
    TESTED
}
