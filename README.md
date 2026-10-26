# Fluid Swap

A tiny 2D game in plain Java (Swing/Java2D — no build tools or dependencies).

The main character is drawn as a **circle** but has a **box (AABB) hitbox**
matching the circle's bounds. It collides with **box-shaped terrain**.
Terrain blocks come in four visual flavors (collision is identical AABB for all):
- `SOLID` — plain box
- `CREST` — ridged top edge (hill cap / platform lip)
- `BLOTCHY` — speckled rough ground
- `GRASS` — dirt body with a grassy top and blades (the main platform terrain)

The map also has **water** regions (non-solid). Movement speed depends on the
medium the character is in:
- **Land** (grounded) — slowest (`LAND_SPEED = 140`)
- **Air** — medium (`AIR_SPEED = 240`)
- **Water** — fastest (`WATER_SPEED = 420`); gravity is reduced and buoyant.
  Holding up is a *sustained* ascent (`WATER_SWIM_UP_SPEED = 380`), holding down
  a dive; with no input you sink gently. The vertical velocity eases toward its
  target at a fixed `WATER_SWIM_ACCEL = 2000`, so reversing (ascend ⇄ dive) is a
  gradual turn that takes longer the faster you were going — no snap. Breaking
  the surface while holding jump launches you out with a strong burst
  (`WATER_EXIT_JUMP_SPEED = 780`).
- **Waterfall** — a vertical column of falling water you can **cling to and
  climb slowly** (`CLIMB_SPEED = 90`, up *or* down). Gravity is ignored while
  clinging; with no vertical input you slip down gently (`WATERFALL_SLIP = 45`).

The demo level is a short, water-forward course (2800×1700) — bigger than the
window, so a **camera** follows the character (clamped at the world edges). You
spawn inside a **floating square of water** that rests on a **one-way platform**
(a thin line you can swim up through but not sink down through). Burst up and
leap across the gap to a second square, ride the single **waterfall** up to a
third square with a **grass block**, then make one last jump out to the dry
**finish** platform (set off to the right) with its flag pole. Everything below
the squares is a bottomless **pit** — fall in and you die and respawn in the
spawn square.

## Run

```bash
./run.sh          # build + launch the game window
./run.sh test     # build + run the headless collision tests
```

Or manually:

```bash
javac -d out src/fluidswap/*.java
java -cp out fluidswap.Main            # game
java -cp out fluidswap.CollisionTest   # tests
```

Controls: **A/D** or **←/→** to move, **Space / W / ↑** to jump / swim up /
climb a waterfall up, **S / ↓** to climb a waterfall down.

## Layout

| File | Role |
|------|------|
| `Player.java` | the character: circle sprite + box hitbox + physics state |
| `Block.java` | a box of terrain (with a visual `Type`) |
| `Water.java` | a non-solid water region |
| `Waterfall.java` | a non-solid vertical column you cling to and climb slowly |
| `Platform.java` | a one-way platform (swim up through, land on top) |
| `Finish.java` | a non-solid goal trigger region (the flag) |
| `Hazard.java` | a non-solid lethal trigger region (the pit) |
| `Collision.java` | AABB-vs-AABB detection and resolution |
| `World.java` | physics step, medium-based speed, collision pipeline, completion + death/respawn state (no rendering — testable) |
| `Level.java` | the demo level layout |
| `GamePanel.java` / `Main.java` | Swing window, game loop, rendering |
| `CollisionTest.java` | headless assertions that the circle collides with terrain |

## Finish stage

On the dry finish platform stands a simple **flag pole** (`Finish` region).
**Landing** on it (the trigger requires `onGround`, so the message waits until
you actually hit the platform rather than grazing the flag mid-jump) enters the
goal: `World.finished` latches on (it can only trigger once), the player is
locked in place with all input ignored, the flag turns green, and `GamePanel`
dims the screen with a brief **"LEVEL COMPLETE!"** title.
`Finish` follows the same non-solid trigger-region pattern as `Water` and
`Waterfall`, checked after movement — exactly the slot the engine reserved for
"glowing areas."

## One-way platforms

Floating water squares rest on a `Platform` — a thin line you can swim/jump
**up through** but not pass **down through**. `World` resolves it specially
using the player's previous-frame foot position: a descending player that was
above the line lands on top (sets `onGround`), while a rising player passes
straight up. It never blocks horizontal movement.

## Atmosphere, the pit & respawn

`GamePanel` draws an ambient aquatic backdrop behind the world — a depth
gradient (lit teal up top fading to a dark deep), slow drifting god-rays, and
floating bubbles.

Below the squares is a bottomless **pit** — a full-width lethal `Hazard` band.
Falling into it kills the player: `World.dead` latches, a death burst (ring +
scattering bubbles) plays while the screen darkens, and after `DEATH_DURATION`
the player **respawns** in the spawn square with motion and medium state reset.
Input is ignored during the sequence, and a short fade-in plays on respawn.
Death uses the same trigger-region pattern as the finish and never fires once
the level is won (finish takes priority).
