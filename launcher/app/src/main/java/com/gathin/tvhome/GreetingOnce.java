package com.gathin.tvhome;

/** greeting_once: the greeting shows once per part of the day (HOME-DESIGN.md 4.7). */
final class GreetingOnce {
    private GreetingOnce() {}

    /** The key remembered once the greeting has shown: "<yyyy-MM-dd>/<part of the day>". */
    static String key(String date, String partName) {
        return date + "/" + partName;
    }

    /** True when the greeting has not been shown for {@code nowKey} yet; {@code seenKey} is what was remembered, or null. */
    static boolean due(String seenKey, String nowKey) {
        return nowKey != null && !nowKey.isEmpty() && !nowKey.equals(seenKey);
    }
}
