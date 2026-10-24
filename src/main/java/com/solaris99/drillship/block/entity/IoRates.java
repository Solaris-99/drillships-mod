package com.solaris99.drillship.block.entity;

final class IoRates {
    /** A module's own limit and the server's cap, either 0 for none: the lower of those that are set. */
    static int combine(int limit, int cap) {
        if (limit == 0) {
            return cap == 0 ? Integer.MAX_VALUE : cap;
        }
        return cap == 0 ? limit : Math.min(limit, cap);
    }

    private IoRates() {
    }
}
