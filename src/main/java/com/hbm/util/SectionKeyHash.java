package com.hbm.util;

import it.unimi.dsi.fastutil.longs.LongHash;

/**
 * Designed for x22 | z22 | y20 sectionKey encoding
 */
public final class SectionKeyHash {
    private SectionKeyHash() {
    }

    public static int hash(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        z ^= z >>> 31;
        return Long.hashCode(z);
    }

    public static final LongHash.Strategy STRATEGY = new LongHash.Strategy() {
        @Override
        public int hashCode(long k) {
            return hash(k);
        }

        @Override
        public boolean equals(long a, long b) {
            return a == b;
        }
    };
}
