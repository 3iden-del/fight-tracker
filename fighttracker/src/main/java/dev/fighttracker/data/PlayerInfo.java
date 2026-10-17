package dev.fighttracker.data;

import java.util.ArrayList;
import java.util.List;

/** Names we have personally observed for one UUID. Used by the Name History tab (stage 4). */
public class PlayerInfo {
    public String uuid;
    public String name;
    public long firstSeen;
    public long lastSeen;
    public List<NameChange> nameChanges = new ArrayList<>();

    public static class NameChange {
        public String oldName;
        public String newName;
        public long observedAt;

        public NameChange() {}

        public NameChange(String oldName, String newName, long observedAt) {
            this.oldName = oldName;
            this.newName = newName;
            this.observedAt = observedAt;
        }
    }

    public PlayerInfo() {}

    public PlayerInfo(String uuid, String name, long now) {
        this.uuid = uuid;
        this.name = name;
        this.firstSeen = now;
        this.lastSeen = now;
    }
}
