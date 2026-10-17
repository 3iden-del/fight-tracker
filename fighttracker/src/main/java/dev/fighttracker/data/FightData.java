package dev.fighttracker.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Root object saved to fighttracker/data.json. */
public class FightData {
    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    public List<FightRecord> fights = new ArrayList<>();
    public Map<String, PlayerInfo> players = new HashMap<>(); // key = UUID string
}
