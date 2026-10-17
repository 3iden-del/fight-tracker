package dev.fighttracker.util;

public final class TimeFormat {
    private TimeFormat() {}

    public static String ago(long timestampMs, long nowMs) {
        if (timestampMs <= 0) return "never";
        long s = Math.max(0, (nowMs - timestampMs) / 1000);
        if (s < 10) return "just now";
        if (s < 60) return s + "s ago";
        if (s < 3600) return (s / 60) + "m ago";
        if (s < 86400) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }
}
