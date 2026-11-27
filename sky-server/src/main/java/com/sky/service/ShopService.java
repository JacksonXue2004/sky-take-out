package com.sky.service;

/**
 * Whether the shop is open. The shop table holds the status; Redis keeps a copy for the customer
 * pages. Taking an order checks the table, so a stale copy can only show the wrong badge.
 */
public interface ShopService {

    Integer OPEN = 1;
    Integer CLOSED = 0;

    /** From the database. A missing row counts as open. */
    Integer getStatus();

    /** From the database, for accepting orders. */
    boolean isOpen();

    /**
     * For the customer pages: the Redis copy, or the database on a miss. Never throws; when neither
     * can be read, the shop is reported open, as before the status was stored.
     */
    Integer getCachedStatus();

    /** Saves the status in the database, then updates the Redis copy. */
    void setStatus(Integer status);
}
