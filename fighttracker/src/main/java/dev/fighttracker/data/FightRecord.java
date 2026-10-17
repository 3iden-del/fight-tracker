package dev.fighttracker.data;

/** One kill or death involving the local player. Plain fields so Gson can (de)serialize it. */
public class FightRecord {
    public long time;          // epoch millis
    public String server;      // server address, or "singleplayer"
    public String opponent;    // opponent UUID as string
    public boolean iKilled;    // true = I killed them, false = they killed me
    public String weapon;      // may be null

    public FightRecord() {}

    public FightRecord(long time, String server, String opponent, boolean iKilled, String weapon) {
        this.time = time;
        this.server = server;
        this.opponent = opponent;
        this.iKilled = iKilled;
        this.weapon = weapon;
    }
}
