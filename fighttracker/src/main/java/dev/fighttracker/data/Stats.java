package dev.fighttracker.data;

/** Aggregated record against one opponent. */
public class Stats {
    public int kills;
    public int deaths;
    public long lastFought;

    /** K/D where zero deaths just shows the kill count, like most PvP trackers. */
    public double ratio() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }
}
