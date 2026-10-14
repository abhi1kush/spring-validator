package com.abhi1kush.validator.core;

import java.util.Comparator;

/**
 * Deterministic ordering for property paths such as {@code address.city} or {@code items[10].qty}:
 * lexicographic, except that runs of digits compare numerically ({@code items[2]} before {@code items[10]}).
 */
final class PropertyPathComparator implements Comparator<String> {

    static final PropertyPathComparator INSTANCE = new PropertyPathComparator();

    private PropertyPathComparator() {
    }

    @Override
    public int compare(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = stripZeros(a.substring(si, i));
                String nb = stripZeros(b.substring(sj, j));
                int cmp = na.length() != nb.length() ? Integer.compare(na.length(), nb.length()) : na.compareTo(nb);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return Integer.compare(a.length() - i, b.length() - j);
    }

    private static String stripZeros(String digits) {
        int k = 0;
        while (k < digits.length() - 1 && digits.charAt(k) == '0') {
            k++;
        }
        return digits.substring(k);
    }
}
