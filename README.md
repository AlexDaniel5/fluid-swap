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

Controls: **A/D** or **←/→** to move, **Space / W / ↑** to jump.

## Layout

| File | Role |
|------|------|
| `Player.java` | the character: circle sprite + box hitbox + physics state |
| `Block.java` | a box of terrain (with a visual `Type`) |
| `Water.java` | a non-solid water region |
| `Collision.java` | AABB-vs-AABB detection and resolution |
| `World.java` | physics step, medium-based speed, collision pipeline (no rendering — testable) |
| `Level.java` | the demo level layout |
| `GamePanel.java` / `Main.java` | Swing window, game loop, rendering |
| `CollisionTest.java` | headless assertions that the circle collides with terrain |

## Not yet implemented (by design)

**Glowing areas** that grant the player effects are intentionally left out. When
added, they'd be a separate list of trigger regions in `World`, checked after
movement — the collision pipeline is structured to slot them in.
