# Fluid Swap

A tiny 2D game in plain Java (Swing/Java2D — no build tools or dependencies).

The main character is drawn as a **circle** but has a **box (AABB) hitbox**
matching the circle's bounds. It collides with **box-shaped terrain**.
Terrain blocks come in three visual flavors (collision is identical AABB for all):
- `SOLID` — plain box
- `CREST` — ridged top edge (hill cap / platform lip)
- `BLOTCHY` — speckled rough ground

The map also has **water** regions (non-solid). Movement speed depends on the
medium the character is in:
- **Land** (grounded) — slowest (`LAND_SPEED = 140`)
- **Air** — medium (`AIR_SPEED = 240`)
- **Water** — fastest (`WATER_SPEED = 420`); gravity is reduced and Space lets
  you swim up.
- **Waterfall** — a vertical column of falling water you can **cling to and
  climb slowly** (`CLIMB_SPEED = 90`, up *or* down). Gravity is ignored while
  clinging; with no vertical input you slip down gently (`WATERFALL_SLIP = 45`).

The demo level is a chain of floating **islands** at increasing heights above a
sea, connected by four **waterfalls**. The intended route climbs from the sea up
to the mid island, then the high island, then the top rock — each hop is a slow
waterfall climb.

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
| `Collision.java` | AABB-vs-AABB detection and resolution |
| `World.java` | physics step, medium-based speed, collision pipeline (no rendering — testable) |
| `Level.java` | the demo level layout |
| `GamePanel.java` / `Main.java` | Swing window, game loop, rendering |
| `CollisionTest.java` | headless assertions that the circle collides with terrain |

## Not yet implemented (by design)

**Glowing areas** that grant the player effects are intentionally left out. When
added, they'd be a separate list of trigger regions in `World`, checked after
movement — the collision pipeline is structured to slot them in.
