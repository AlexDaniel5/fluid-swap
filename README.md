# Fluid Swap

A tiny 2D game in plain Java (Swing/Java2D — no build tools or dependencies).

The main character is drawn as a **circle** but has a **box (AABB) hitbox**
matching the circle's bounds. It collides with **box-shaped terrain**.
Terrain blocks come in two visual flavors (collision is identical AABB for all):
- `SOLID` — plain marble brickwork (the boundary walls)
- `GRASS` — ornate marble ledge: gilded capstone, finials and hanging pendants
  (the main platform terrain; the enum name predates the art style)

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
climb a waterfall up, **S / ↓** to climb a waterfall down, **F** to dash.

## Dash

Pressing **F** fires a dash, inspired by Hollow Knight's: a short, fixed-length
burst in the direction you last moved (`DASH_SPEED = 950` for
`DASH_DURATION = 0.16` s, about 150 px). While dashing, gravity, swimming and
climbing are suspended and input is ignored, so the dash is a dead-straight
line, even off a ledge or across the surface of a pool. It ends early if it
hits a wall. Afterwards speed drops back to the current medium's top speed.

- **One dash in mid-air.** The dash is spent when used and refilled by touching
  the ground, water or a waterfall.
- **Cooldown.** `DASH_COOLDOWN = 0.35` s between dashes.
- **No invulnerability.** Dashing into an urchin or the pit still kills you.
- **One dash per key press.** Holding F doesn't repeat it.

The view draws the orb stretched along the dash, leaves fading void-dark
afterimages behind it, and throws a puff of void smoke where the dash starts.
The HUD shows whether the dash is `ready`, `spent` or `DASHING`.

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

**Art style.** Everything is pale, white-palace pixel art. `GamePanel` draws
the world into a low-res canvas where one canvas pixel is one art pixel
(`PX = 4` screen pixels), with antialiasing off, then scales it up with
nearest-neighbour filtering. The camera snaps to that grid so nothing shimmers.
Text is drawn the same way (rendered small, then scaled up). The palette is
ivory marble, lavender-grey shadow, a touch of pale gold, and the black void as
the only deep tone; the player is a dark orb so it stands out against the white.

Behind the world: a dithered sky gradient, two parallax layers of distant white
spires, soft light shafts and drifting motes of light.

Below the squares is a bottomless **pit** — a full-width lethal `Hazard` band.
Falling into it kills the player: `World.dead` latches, a death burst (dotted ring +
scattering shards) plays while the screen darkens, and after `DEATH_DURATION`
the player **respawns** in the spawn square with motion and medium state reset.
Input is ignored during the sequence, and a short fade-in plays on respawn.
Death uses the same trigger-region pattern as the finish and never fires once
the level is won (finish takes priority).
