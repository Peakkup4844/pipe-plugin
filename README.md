# PipePlugin

A *Create*-style item pipe system for Minecraft servers.

Move items between storage blocks by building a pipe out of stained glass and pulsing redstone — no GUI, no commands, just build and power it.

---

## Compatibility

| | |
|---|---|
| Minecraft | **1.20.1 → latest (26.2)** |
| Platforms | **Bukkit / Spigot, Paper, Folia** |
| Java (runtime) | 17+ (the jar is compiled to Java 17 bytecode) |

Built against the pure Bukkit API (no NMS), so a single jar runs across the whole version range. Folia support is provided through [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib) (shaded & relocated).

The same jar is run end-to-end on **Spigot 1.20.1, Paper 1.20.1, Paper 26.2 and Folia 26.1.2** — building a pipe, pulsing it, and checking that the item totals on both ends match exactly.

---

## How it works

A pipe has three connected parts:

```
[ Source chest ] ← (Sticky Piston)   ==glass tube==   (Piston) → [ Dest chest ]
                     input, facing        one colour      output, facing
                     the source                           the destination
```

1. **Input** — a **sticky piston** whose head faces a storage block (the source).
2. **Process** — a tube of **same-coloured glass** running from the sticky piston to the output.
3. **Output** — a normal **piston** whose head faces another storage block (the destination).

All three parts must touch with **no gap**. The glass colour defines the network — **each colour is a separate, independent pipe**, so different-coloured pipes can run side by side without connecting.

### Activating

Send a redstone **pulse** to the sticky piston. **Each pulse = 1 cycle**, which moves **one item type** from the source: up to `items-per-cycle` (default 32) of it, or just 1 for non-stackable items (tools, armour, etc.). The type moved is the first one in the source that has a valid route to an output.

> The pistons act purely as markers/triggers — they never physically push the chests.

---

## Supported storage

Any container block: chest, trapped chest, double chest, barrel, shulker box, hopper, dropper, dispenser, etc. (anything that is a Bukkit `Container`).

> **Hoppers:** a hopper moves items on its own every ~8 ticks (vanilla behaviour), independent of redstone pulses. Using one as a source/destination never duplicates items — the pipe conserves everything it touches — but the hopper's own suction/push can make items appear to flow "without a pulse" or loop back into the source. If you want the pipe to be the only mover, exclude `HOPPER` from `allowed-containers`.

> **Storage plugins (WildChests etc.):** some plugins place a normal-looking `CHEST` but keep the real contents in their own storage instead of the block's inventory. Writing into such a chest would make items invisible — and lost when the chest is broken; reading a "display" item back out of one would create items from nothing. Pipes therefore **refuse to use them** as a source or destination (`skip-plugin-managed-chests: true`). Three independent checks: WildChests is asked through its API; **any** container whose inventory is not one of the server's own is skipped; and finally — always on, even with the option disabled — every cycle is checked against what the container actually did. A container that fails that last check is **never used again** until the server restarts, and the warning names its inventory class — please report it so a direct check can be added.

> **WildChests storage units work with pipes** (as source *and* destination), even on the safe default. A storage unit draws a single "display" stack that stays the same no matter how much is added or taken, so counting its inventory can never tell you what really happened — the pipe asks WildChests for the unit's actual stock instead, and verifies every cycle against that. A storage unit already holding a different item is skipped rather than filled, since it only takes one type. WildChests' normal and linked chests are still refused, because their items live in the plugin's own pages.

> **Ender chests are never used**, even if you list `ENDER_CHEST` in `allowed-containers`: the inventory behind an ender chest belongs to the player who opens it, not to the block.

> **A shulker box is never put inside a shulker box.** Vanilla forbids it, but the Bukkit call a plugin uses to insert items does not enforce that rule — so this is checked explicitly. Such an item is simply skipped for that destination, and the pipe moves on to the next item type in the source, so one shulker box left lying in a source chest cannot jam a pipe.

Supported glass: clear `GLASS`, `TINTED_GLASS`, and all 16 `*_STAINED_GLASS` blocks (full blocks, not panes).

---

## Installation

1. Drop `PipePlugin-1.0.0.jar` into your server's `plugins/` folder.
2. Start the server. `plugins/PipePlugin/config.yml` and `lang.yml` are generated.

---

## Configuration

`config.yml`:

```yaml
# Number of items moved per pulse for the selected item type (counted as items, not stacks).
# Non-stackable items (tools, armour, etc.) always move 1 per pulse.
items-per-cycle: 32

# Max tube length the search will follow (caps the pathfinding cost).
max-pipe-length: 64

# Minimum ticks between two transfers on the SAME pipe (per-pipe rate limit).
# 20 ticks = 1 second. Stops a fast redstone clock from firing hundreds of cycles.
# 0 disables the limit.
min-pulse-interval-ticks: 2

# Play a sound + particles at the source and destination when items move.
effects: true

# Skip containers whose real contents live outside the vanilla block inventory.
# Controls two checks: WildChests is asked through its API, and any container whose inventory
# is not one of the server's own classes is skipped. WildChests storage units are exempt --
# their real stock can be read and verified, so pipes use them either way. A third check --
# verifying that containers really gain/lose what they report -- is always on and cannot
# be disabled. true = safe (default); false = use them anyway, NOT recommended.
skip-plugin-managed-chests: true

# Fire InventoryMoveItemEvent for every insert, the same event vanilla hoppers use, so that
# land-protection plugins (WorldGuard, GriefPrevention, Towny, LWC) and logging plugins
# (CoreProtect) can see a pipe transfer and block or record it. true = pipes obey the same
# protections hoppers do (default). See "Protection plugins" below before turning it off.
call-inventory-move-event: true

# ITEM-FRAME FILTER matching mode -- this affects which items a frame lets through, nothing else:
#   SIMILAR = exact NBT (type + custom name + enchants + meta)  [default]
#   TYPE    = material only (all diamonds count as the same)
filter-match: SIMILAR

# Allowed source/destination storage:
#   all  = any Container
#   else = only the materials you name, on one line or as a YAML list
allowed-containers: all
```

> **Restricting `allowed-containers`:** write `allowed-containers: CHEST, BARREL` on one line, or replace that line with a YAML list. Do **not** leave a second `allowed-containers:` key in the file — YAML keeps only the last one and the restriction would vanish with no error. The plugin logs the setting it actually used at every start (`Allowed containers: ...`); if that line does not match what you wrote, the file has a duplicate key or a misspelled material (each bad name is warned about individually).

`lang.yml` holds every console/log message the plugin prints (the plugin has no in-game chat). Edit it to translate or reword the console output; `{placeholder}` tokens are filled in at runtime.

---

## Protection plugins

A pipe pulls items out of whatever container its sticky piston faces. Without a hook, that means anyone could point a sticky piston at a chest inside someone else's claim and drain it, because nothing else on the server would ever learn the items had moved.

So every insert fires an **`InventoryMoveItemEvent`** — the same event a vanilla hopper fires. Land-protection plugins (WorldGuard, GriefPrevention, Towny, LWC) and logging plugins (CoreProtect) already listen for it, so they see and can block pipe transfers with no extra configuration. **A cancelled transfer moves nothing and loses nothing** — the items go back to the exact slots they came from.

Turn `call-inventory-move-event` off only if your server has no claims at all, or if some plugin cancels every hopper move (a few anti-lag plugins do) and stops pipes from working.

> **On Folia** the event is fired when the source container belongs to the region the insert runs on — which covers any normal pipe. A pipe that genuinely spans two regions skips the event, because handing another region's inventory to a listener would break Folia's threading rules.

---

## Quick start example

1. Place a chest and fill it with items.
2. Place a **sticky piston** against the chest, head pointing into it.
3. From the sticky piston, run a line of **one colour** of glass to where you want.
4. At the end, place a **piston** against a second chest, head pointing into it, touching the glass.
5. Pulse redstone (button/lever) into the sticky piston → one item type moves per pulse.

---

## Building from source

Requires a JDK 17+ on `PATH` (the included Gradle wrapper handles the rest).

```bash
./gradlew shadowJar        # Linux/macOS
.\gradlew.bat shadowJar    # Windows
```

Output: `build/libs/PipePlugin-1.0.0.jar`

---

## Item-frame filters

Place an **item frame on any pipe block** — a glass block, the input piston, or an output piston — and put an item in it. That block becomes a **gate**: an item may pass through / into that block only if it matches the framed item. A block with no frame lets everything through.

| Frame is on… | Effect |
|---|---|
| Input piston | Only matching items are **pulled** from the source |
| A glass block | Items must match to **pass through** that block (use it to block a branch) |
| Output piston | That output **only accepts** matching items |

- Multiple frames on the same block → matching **any** of them lets the item pass (union).
- Matching is **NBT-exact by default** (`SIMILAR`): a renamed item only matches the exact renamed item. Switch to `TYPE` in config for material-only matching.
- `filter-match` changes **only** what a frame lets through. Items are always grouped, moved and put back by exact match, so `TYPE` can never make a pipe merge two NBT-different stacks (tipped arrows, fireworks) into one kind.
- Frames are read live every pulse, so changing/removing a frame takes effect immediately.

## Multiple outputs & routing

One pipe (one input) can feed **many output pistons** — just attach more pistons (each facing a container) anywhere along the tube.

Each pulse moves a single item type; that type is routed as follows:

1. **Priority = nearest first, then left before right.** At a junction the left branch is preferred; if the left branch is blocked by a filter for that item, it goes right instead.
2. **Priority fill:** the highest-priority output is filled until full, then overflow goes to the next, and so on.
3. Anything that fits nowhere is returned to the source (never lost).

> "Front" of the pipe = the direction leading **away from the source**; left/right are from that facing.

## Behaviour & limits

- Source empty → nothing moves. Destination full → only what fits is moved; the rest goes back to the **slots it came from**, so a full destination leaves the source chest looking untouched (items are never lost or shuffled).
- After taking items out, the pipe **re-counts the source** and only sends them on if the source really lost exactly that many. The count comes from the plugin that owns the container when it can report one (WildChests storage units), and from the block inventory otherwise. If the numbers do not add up, everything taken is put straight back, the pulse is abandoned, and that container is ignored from then on — so an odd container can cost at most one cycle, once, instead of every pulse. Destinations are checked the same way (they are only blacklisted, never "un-inserted", since taking items back that the container did in fact keep would duplicate them).
- The pipe layout is re-scanned on **every pulse**, so edits show up immediately — including ones that fire no block event, such as WorldEdit or `/fill`. Add a piston, then its chest, and the next pulse already uses it.
- Triggering relies on `BlockRedstoneEvent` rising-edge detection at the sticky piston. Standard redstone inputs (lever, button, repeater adjacent to the piston) work as expected.
- A per-pipe rate limit (`min-pulse-interval-ticks`) keeps a fast redstone clock from firing hundreds of cycles a second. A cycle also never overlaps itself — a new pulse is ignored while the previous transfer is still running.
- A successful move plays a sound + particles at both ends (toggle with `effects`), so a working-but-empty pipe is distinguishable from a broken one.
- A whole cycle — frame scan, take, insert, return — runs **within a single tick** whenever every container involved belongs to the region the pulse is running on, which is every pipe on Bukkit/Paper and any pipe inside one region on Folia. Nothing outside the pipe can slip into a slot mid-cycle, and a pipe answers a pulse immediately instead of several ticks later.
- On Folia, a pipe that genuinely crosses region boundaries falls back to scheduling each step on the owning region's thread, and the **item-frame scan runs per region** (one task per chunk it touches), so it still reads filters and moves items correctly.

---

## License

You may use and modify this freely for your server.
