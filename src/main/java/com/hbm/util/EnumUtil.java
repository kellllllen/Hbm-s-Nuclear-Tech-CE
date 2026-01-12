package com.hbm.util;

public final class EnumUtil {

    private EnumUtil() {
    }

    /**
     * @deprecated this creates a new E[] object on every call, use {@link #grabEnumSafely(Enum[], int)} instead.
     */
    @Deprecated
    public static <E extends Enum<E>> E grabEnumSafely(Class<E> theEnum, int index) {
        E[] values = theEnum.getEnumConstants();
        return values[Math.floorMod(index, values.length)];
    }

    public static <E extends Enum<E>> E grabEnumSafely(E[] values, int index) {
        return values[Math.floorMod(index, values.length)];
    }
}
