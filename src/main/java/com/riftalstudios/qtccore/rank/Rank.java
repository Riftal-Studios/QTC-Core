package com.riftalstudios.qtccore.rank;

public enum Rank {
    VETERAN("Veteran", 1_000_000),
    LORD("Lord", 750_000),
    HERO("Hero", 500_000),
    KNIGHT("Knight", 350_000),
    CHAMPION("Champion", 250_000),
    GUARDIAN("Guardian", 175_000),
    SEEKER("Seeker", 125_000),
    MYSTIC("Mystic", 100_000),
    ADEPT("Adept", 75_000),
    HISTORIAN("Historian", 50_000),
    SCHOLAR("Scholar", 35_000),
    EXPLORER("Explorer", 20_000),
    JOURNEYMAN("Journeyman", 10_000),
    APPRENTICE("Apprentice", 5_000),
    NOVICE("Novice", 2_500),
    NEWCOMER("Newcomer", 1_500),
    REGULAR("Regular", 1_001),
    CIVILIAN("Civilian", 501),
    WANDERER("Wanderer", 101),
    OUTCAST("Outcast", 1);

    private final String displayName;
    private final long threshold;

    Rank(String displayName, long threshold) {
        this.displayName = displayName;
        this.threshold = threshold;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getThreshold() {
        return threshold;
    }

    /**
     * Determines the rank for a given lifetime reputation total.
     * Iterates from the highest rank down and returns the first match.
     */
    public static Rank fromRepTotal(long repTotal) {
        for (Rank rank : values()) {
            if (repTotal >= rank.threshold) {
                return rank;
            }
        }
        return OUTCAST;
    }
}
