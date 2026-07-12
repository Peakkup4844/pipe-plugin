# PipePlugin

A *Create*-style item pipe system for Minecraft servers.

Move items between storage blocks by building a pipe out of stained glass and pulsing redstone — no GUI, no commands, just build and power it.

---

## Compatibility

| | |
|---|---|
| Minecraft | **1.20.1 → latest (26.1)** |
| Platforms | **Bukkit / Spigot, Paper, Folia** |
| Java (runtime) | 17+ (the jar is compiled to Java 17 bytecode) |

Built against the pure Bukkit API (no NMS), so a single jar runs across the whole version range. Folia support is provided through [FoliaLib](https://github.com/TechnicallyCoded/FoliaLib) (shaded & relocated).

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

# Item-frame filter matching mode:
#   SIMILAR = exact NBT (type + custom name + enchants + meta)  [default]
#   TYPE    = material only (all diamonds count as the same)
filter-match: SIMILAR

# Allowed source/destination storage:
#   all  = any Container
#   list = only the listed materials
allowed-containers: all
```

`lang.yml` holds every console/log message the plugin prints (the plugin has no in-game chat). Edit it to translate or reword the console output; `{placeholder}` tokens are filled in at runtime.

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

Place an **item frame** on the boundary between any two pipe parts and put an item in it. That frame becomes a **gate**: an item may cross that boundary only if it matches the framed item. No frame = everything passes.

| Frame location | Effect |
|---|---|
| Input piston ↔ tube | Only matching items are **pulled** from the source |
| Tube ↔ tube | Items that don't match **can't pass beyond** that point |
| Tube ↔ output piston | That output **won't accept** non-matching items |

- Multiple frames on the same boundary → matching **any** of them lets the item pass (union).
- Matching is **NBT-exact by default** (`SIMILAR`): a renamed item only matches the exact renamed item. Switch to `TYPE` in config for material-only matching.
- Frames are read live every pulse, so changing/removing a frame takes effect immediately.

## Multiple outputs & routing

One pipe (one input) can feed **many output pistons** — just attach more pistons (each facing a container) anywhere along the tube.

Each pulse moves a single item type; that type is routed as follows:

1. **Priority = nearest first, then left before right.** At a junction the left branch is preferred; if the left branch is blocked by a filter for that item, it goes right instead.
2. **Priority fill:** the highest-priority output is filled until full, then overflow goes to the next, and so on.
3. Anything that fits nowhere is returned to the source (never lost).

> "Front" of the pipe = the direction leading **away from the source**; left/right are from that facing.

## Behaviour & limits

- Source empty → nothing moves. Destination full → only what fits is moved; the rest stays in the source (items are never lost).
- Pipes are auto-discovered on activation and cached; breaking/changing any block in the pipe re-validates it on the next pulse.
- Triggering relies on `BlockRedstoneEvent` rising-edge detection at the sticky piston. Standard redstone inputs (lever, button, repeater adjacent to the piston) work as expected.
- On Folia, item insertion happens on each output region's own thread, so outputs in different regions are handled safely. The pipe **topology/filter scan** runs on the input's region, so for guaranteed correctness keep a single pipe network within one region (the normal case for a connected build).

---

## License

You may use and modify this freely for your server.
