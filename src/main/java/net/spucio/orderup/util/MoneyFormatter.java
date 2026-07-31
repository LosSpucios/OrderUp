package net.spucio.orderup.util;

/** Utility methods for restaurant money stored in half-dollar units. */
public final class MoneyFormatter {
    private MoneyFormatter() {}

    public static long dollarsToHalfUnits(long dollars) {
        if (dollars <= 0L) return 0L;
        return dollars > Long.MAX_VALUE / 2L ? Long.MAX_VALUE : dollars * 2L;
    }

    /** Converts a decimal dollar amount to the nearest half-dollar unit. */
    public static long dollarsToHalfUnits(double dollars) {
        if (!Double.isFinite(dollars) || dollars <= 0.0D) return 0L;
        if (dollars >= Long.MAX_VALUE / 2.0D) return Long.MAX_VALUE;
        return Math.round(dollars * 2.0D);
    }

    public static String formatHalfUnits(long halfUnits) {
        long safe = Math.max(0L, halfUnits);
        long whole = safe / 2L;
        return (safe & 1L) == 0L ? Long.toString(whole) : whole + ".5";
    }

    public static String withDollarPrefix(long halfUnits) {
        return "$" + formatHalfUnits(halfUnits);
    }

    public static String withDollarSuffix(long halfUnits) {
        return formatHalfUnits(halfUnits) + "$";
    }
}
