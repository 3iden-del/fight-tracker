# Fight Tracker - Stage 1 (tracking + data storage + Lobby tab)

Client-side Fabric mod for Minecraft 1.21.11. Press **K** (rebindable in Controls > Fight Tracker) to open the menu.

## Build (GitHub, no setup needed)
1. Put this folder's contents in a new GitHub repo (include the hidden `.github` folder).
2. Open the repo's **Actions** tab, wait for the "build" run to go green, and download the `fighttracker-jar` artifact.
3. Unzip it and put the `.jar` in `.minecraft/mods/` together with Fabric API for 1.21.11.
The workflow installs Gradle and Java 21 itself, so no wrapper files are required.

## Files written at runtime (config/fighttracker/)
- `config.json`: death-message regexes, fallback toggle, duplicate window, save interval.
- `data.json`: all fights and observed names. Back this up if you care about your history.

## Testing without a real fight
1. Run a local server with `online-mode=false` and join with two accounts (or a friend). Both names must be in the tab list.
2. From the server console or as an op, run: `/tellraw @a "Friend was slain by YourName"`, then
   `/tellraw @a "YourName was slain by Friend using [Diamond Sword]"`.
3. Press K. The Friend row should show 1 kill, 1 death, K/D 1.00. Sort by clicking the column headers.
4. Check `config/fighttracker/data.json` after ~30 seconds or after quitting.

## Known limitations (stage 1)
- Death messages must arrive as system messages. Servers that send them as player-style chat, or with custom
  formats, need regex edits in `config.json`. Test with a real kill on your server and adjust.
- The hit-based fallback only credits kills (it can't see your deaths) and can false-positive when a target leaves render range.
- K/D is combined across all servers. Per-server views arrive with the Overview tab.
- Saving serializes JSON on the client thread (the file write itself is async). Fine up to tens of thousands of fights.
- Not yet built: Search, Graphs, Name History and Overview tabs (shown greyed out), menu scale, Mod Menu/Cloth Config screens.
- Name tracking only covers players you actually see in the tab list; it can't recover older names.
