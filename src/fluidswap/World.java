package fluidswap;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the player, terrain, and water, and advances the simulation. Kept free
 * of any rendering/windowing code so it can be driven headlessly by tests.
 *
 * Movement speed depends on the medium the character is in:
 *   - slowest when standing on land (grounded),
 *   - medium while airborne,
 *   - fastest while in water.
 *
 * NOTE: "glowing areas" that grant the player effects are still intentionally
 * NOT implemented. They would be another list of trigger regions checked like
 * water is below.
 */
public final class World {

    public static final double GRAVITY = 1800.0;      // px / s^2
    public static final double JUMP_SPEED = 720.0;    // px / s

    // Horizontal movement speeds by medium (px / s).
    public static final double LAND_SPEED = 140.0;    // slowest
    public static final double AIR_SPEED = 240.0;     // medium
    public static final double WATER_SPEED = 420.0;   // fastest

    // In water the character sinks slowly and can swim; gravity is reduced and
    // damped so it stays interactive rather than dropping like a stone.
    public static final double WATER_GRAVITY_SCALE = 0.25;
    public static final double WATER_DRAG = 3.0;      // per-second velocity damping

    public final Player player;
    public final List<Block> blocks = new ArrayList<>();
    public final List<Water> waters = new ArrayList<>();

    // Input intent, set by the controller each frame.
    public double moveDir = 0;       // -1 left, +1 right, 0 none
    public boolean jumpRequested = false;

    public World(Player player) {
        this.player = player;
    }

    public void addBlock(Block b) { blocks.add(b); }
    public void addWater(Water w) { waters.add(w); }

    /** The active horizontal speed given the character's current medium. */
    public double activeMoveSpeed() {
        if (player.inWater) {
            return WATER_SPEED;
        }
        return player.onGround ? LAND_SPEED : AIR_SPEED;
    }

    /** Advance the simulation by {@code dt} seconds. */
    public void step(double dt) {
        // Determine medium from current position (water + previous grounded state).
        player.inWater = overlapsAnyWater();

        // Horizontal intent at the speed for the current medium.
        player.vel.x = moveDir * activeMoveSpeed();

        // Jump / swim-up when grounded or in water.
        if (jumpRequested && (player.onGround || player.inWater)) {
            player.vel.y = -JUMP_SPEED * (player.inWater ? 0.7 : 1.0);
            player.onGround = false;
        }
        jumpRequested = false;

        // Gravity (reduced in water) and water drag.
        double gravity = GRAVITY * (player.inWater ? WATER_GRAVITY_SCALE : 1.0);
        player.vel.y += gravity * dt;
        if (player.inWater) {
            double damp = Math.max(0, 1.0 - WATER_DRAG * dt);
            player.vel.y *= damp;
        }

        // Integrate.
        player.pos.add(player.vel.x * dt, player.vel.y * dt);

        // Resolve collisions against all terrain.
        player.onGround = false;
        for (Block b : blocks) {
            Collision.Hit hit = Collision.resolve(player, b);
            if (!hit.collided) {
                continue;
            }
            player.pos.add(hit.pushX, hit.pushY);

            // Cancel velocity into the surface.
            if (hit.normalX != 0 && Math.signum(player.vel.x) == -Math.signum(hit.normalX)) {
                player.vel.x = 0;
            }
            if (hit.normalY != 0 && Math.signum(player.vel.y) == -Math.signum(hit.normalY)) {
                player.vel.y = 0;
            }
            // Landing on top of a block (normal points up).
            if (hit.normalY < 0) {
                player.onGround = true;
            }
        }

        // Re-evaluate water after moving so state is accurate for rendering.
        player.inWater = overlapsAnyWater();
    }

    private boolean overlapsAnyWater() {
        for (Water w : waters) {
            if (Collision.intersects(player, w)) {
                return true;
            }
        }
        return false;
    }
}
