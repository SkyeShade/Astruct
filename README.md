# Async Structure Generator (Astruct)

**Astruct** lets you generate *massive* jigsaw-based structures without freezing the server.
It plans structures **off-thread**, queues placements, and only builds blocks once the required chunks are loaded.

> In practice, very large structures may “stream in” over a few seconds—especially after teleporting—since planning can take time. In survival, this is usually unnoticeable when `plan_horizon_chunks` is set high enough.

---

## Why use Astruct?

* **Way beyond vanilla limits** – plan thousands of jigsaw pieces safely.
* **Off-thread planning** – heavy jigsaw expansion runs outside the main thread, avoiding TPS spikes.
* **Datapack-driven** – ship content via JSON; no code required.

---

## How it works (short version)

1. **Centers** – The world is partitioned into “cells” (`spacing`). Each cell gets a deterministic, seed-based center *per structure ID*.
2. **Proximity scan** – As players roam, Astruct **pre-plans** cells well ahead of them.
3. **Async expansion** – Jigsaw expansion runs off-thread.
4. **Placement queue** – Finished plans enqueue pieces, keyed by the chunks they need.
5. **Chunk-safe build** – On chunk load (and periodic sweeps), Astruct places only pieces whose chunks are currently loaded.

---

## JSON definition (datapack)

Place JSON files at:

```
data/<namespace>/worldgen/async_structure/<id>.json
```

### Example

```json
{
  "id": "example:castle",
  "dimension": "minecraft:the_nether",
  "start_pool": "castle:start",
  "fallback_pool": "castle:caps",

  "soft_radius_chunks": 16,
  "plan_horizon_chunks": 160,

  "budgets": {
    "max_steps": 30,
    "max_open_connectors": 128
  },

  "gen_y": { "mode": "surface", "value": 0 },

  "spacing": 2048
}
```

---

## Field reference

* `id`  
  Unique structure ID (should match the file path key).

* `dimension`  
  Target dimension key (e.g. `minecraft:the_nether`).

* `start_pool`  
  Template pool to start expansion from.

* `fallback_pool`  
  Pool used to cap dead-ends (end pieces).

* `soft_radius_chunks`  
  Max distance budget (in chunks) from the center during expansion.

* `plan_horizon_chunks`  
  How far ahead (in chunks) the planner prepares around players.

* `budgets.max_steps`  
  Upper bound on jigsaw pieces per plan.

* `budgets.max_open_connectors`  
  Safety limit for outstanding connectors.

* `gen_y`  
  Controls how the final Y coordinate is chosen.  
  **Y is resolved late, at the final structure position.**

  Supported modes:

    * `"fixed"`  
      Always use `value` as the Y level.

    * `"min_plus"`  
      `min_build_height + value`.

    * `"world_y"`  
      World sea level.

    * `"surface"`  
      Terrain surface at the structure’s X/Z position  
      (plus optional offset via `value`).

  Example:
  ```json
  "gen_y": { "mode": "surface", "value": 0 }
  ```

* `spacing`  
  Cell size in *blocks* (effective density control).

---


### Field reference

* `id` – Unique structure ID (should match the file path key).
* `dimension` – Target dimension key (e.g. `minecraft:the_nether`).
* `start_pool` – Template pool to start expansion from.
* `fallback_pool` – Pool used to cap dead-ends (end pieces).
* `soft_radius_chunks` – Max distance budget (in chunks) from the center during expansion.
* `plan_horizon_chunks` – How far ahead (in chunks) the planner prepares around players.
* `budgets.max_steps` – Upper bound on jigsaw pieces per plan.
* `budgets.max_open_connectors` – Safety limit for outstanding connectors.
* `gen_y.mode` – `"fixed" | "world_y" | "min_plus"`.
* `gen_y.value` – Y or offset (depending on mode).
* `spacing` – Cell size in *blocks* (effective density control).
* `piece_rules.deny_overlap_tile_entities` – Optional safety flag to skip pieces that would overlap TEs.

---

## Commands

Astruct plugs into the vanilla **`/locate`** root:

* `/locate astruct` – Nearest center for any Astruct structure in this dimension.
* `/locate astruct <id>` – Nearest center for a specific structure ID.

---

## Config (server)

`astruct-common.toml`:

* `debug_logs` *(bool, default `false`)* – Verbose diagnostics.
* `max_placements_per_tick` *(int, default `10`)* – Upper bound on pieces placed each tick.
  Tune for your hardware/pack.

---

## Performance notes

* **Planning cost** scales with `max_steps` and pool complexity. Thousands of pieces may take seconds off-thread—that’s expected. Players won’t lag; placement is paced.
* **Placement is chunk-paced.** Pieces place only when their chunks are loaded, producing a smooth “streaming” effect as you approach.

---

## Compatibility

* Works alongside vanilla and modded structures.
  Astruct doesn’t replace vanilla generation; it adds an async planner/placer on top.

---

## Troubleshooting

* **“Unknown structure id” in commands**
  Check your datapack path and that `id` inside JSON matches the file’s namespaced key.
* **“My structure never generates” / “It takes ages”**
  Enable `debug_logs` to see planning times. Very high `max_steps` or complex pools can take long. Reduce `max_steps`, increase `plan_horizon_chunks`, or increase `spacing`.
* **Spammy logs**
  Set `debug_logs = false` (default).

---

## Roadmap

* Optional biome predicates for expansion.
* Per-structure placement throttles.
* In-game visualization for planned cells.
* Integration under `/locate structure`.

---
