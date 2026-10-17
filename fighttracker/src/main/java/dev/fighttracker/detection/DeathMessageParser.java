package dev.fighttracker.detection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Turns a chat/system message into (victim, killer, weapon) using configurable regexes.
 * Deliberately free of Minecraft classes so it can be unit-tested on its own.
 *
 * Each regex must contain the named groups "victim" and "killer"; "weapon" is optional.
 * Patterns are searched (not fully matched), so server prefixes like "[VIP] " are fine.
 */
public final class DeathMessageParser {

    public record ParsedDeath(String victim, String killer, String weapon) {}

    /** Vanilla death messages that involve a second party. Editable in config.json. */
    public static final List<String> DEFAULT_PATTERNS = List.of(
            // "X was slain by Y", "X was shot by Y using [Bow]", "X was smashed by Y", ...
            "(?<!\\w)(?<victim>\\w{1,16}) was (?:slain|shot|killed|pummeled|fireballed|blown up|impaled|squashed|smashed|speared|skewered|stung|doomed to fall) by (?<killer>\\w{1,16})(?: using (?<weapon>.+?))?\\s*$",
            // "X fell from a high place whilst fighting Y", "X was killed by magic whilst trying to escape Y", ...
            "(?<!\\w)(?<victim>\\w{1,16}) .+ (?:whilst|while) (?:fighting|trying to escape) (?<killer>\\w{1,16})\\s*$"
    );

    private final List<Pattern> patterns = new ArrayList<>();

    public DeathMessageParser(List<String> regexes) {
        for (String regex : regexes) {
            try {
                patterns.add(Pattern.compile(regex));
            } catch (PatternSyntaxException e) {
                // A bad user-supplied pattern must never break the mod; skip it.
                System.err.println("[FightTracker] Ignoring invalid death pattern: " + regex + " (" + e.getDescription() + ")");
            }
        }
    }

    public ParsedDeath parse(String message) {
        if (message == null) return null;
        for (Pattern p : patterns) {
            Matcher m = p.matcher(message);
            if (!m.find()) continue;
            String victim = group(m, "victim");
            String killer = group(m, "killer");
            if (victim == null || killer == null) continue;
            return new ParsedDeath(victim, killer, group(m, "weapon"));
        }
        return null;
    }

    private static String group(Matcher m, String name) {
        try {
            return m.group(name);
        } catch (IllegalArgumentException noSuchGroup) {
            return null; // custom pattern without this group
        }
    }
}
