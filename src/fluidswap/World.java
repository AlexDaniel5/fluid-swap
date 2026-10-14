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
 * Waterfalls are a fourth kind of region: while clinging to one the character
 * ignores gravity and can climb up or down slowly, which is how the islands at
 * different heights are reached.
 *
 * NOTE: "glowing areas" that grant the player effects are still intentionally
 * NOT implemented. They would be another list of trigger regions checked like
 * water / waterfalls are below.
 */
public final class World {

    public static final double GRAVITY = 1800.0;      // px / s^2
    public static final double JUMP_SPEED = 720.0;    // px / s

    // Horizontal movement speeds by medium (px / s).
    public static final double LAND_SPEED = 140.0;    // slowest
    public static final double AIR_SPEED = 240.0;     // medium
    public static final double WATER_SPEED = 420.0;   // fastest

    // Horizontal momentum by medium (px / s^2). Velocity eases toward the input
    // target instead of snapping to it, so reversing direction is a gradual
    // slow-down-then-speed-up rather than an instant flip.
    //   ACCEL    - speeding up in the held direction
    //   TURN     - braking while holding the opposite direction
    //   FRICTION - slowing down with no input (or when over the medium's top speed)
    public static final double LAND_ACCEL = 1200.0;
    public static final double LAND_TURN = 1600.0;
    public static final double LAND_FRICTION = 1400.0;
    public static final double AIR_ACCEL = 1800.0;
    public static final double AIR_TURN = 2000.0;
    public static final double AIR_FRICTION = 800.0;
    public static final double WATER_ACCEL = 2800.0;
    public static final double WATER_TURN = 3000.0;
    public static final double WATER_FRICTION = 1500.0;

    // In water the character sinks slowly and can swim; gravity is reduced and
    // damped so it stays interactive rather than dropping like a stone.
    public static final double WATER_GRAVITY_SCALE = 0.25;
    public static final double WATER_DRAG = 3.0;      // per-second velocity damping

    // Waterfall climbing. Gravity is ignored while clinging; the character
    // climbs up/down at a deliberately slow pace, and slips down gently when no
    // vertical input is held.
    public static final double CLIMB_SPEED = 90.0;         // up/down climb (slow)
    public static final double WATERFALL_SLIP = 45.0;      // gentle downward drift
    public static final double WATERFALL_MOVE_SPEED = 100.0; // sideways while clinging

    public final Player player;
    public final List<Block> blocks = new ArrayList<>();
    public final List<Water> waters = new ArrayList<>();
    public final List<Waterfall> waterfalls = new ArrayList<>();

    // Input intent, set by the controller each frame.
    public double moveDir = 0;       // -1 left, +1 right, 0 none
    public boolean jumpRequested = false;
    public boolean climbUp = false;  // up/jump key held (climb a waterfall)
    public boolean climbDown = false; // down key held (descend a waterfall)

    public World(Player player) {
        this.player = player;
    }

    public void addBlock(Block b) { blocks.add(b); }
    public void addWater(Water w) { waters.add(w); }
    public void addWaterfall(Waterfall f) { waterfalls.add(f); }

    /** The active horizontal speed given the character's current medium. */
    public double activeMoveSpeed() {
        if (player.onWaterfall) {
            return WATERFALL_MOVE_SPEED;
        }
        if (player.inWater) {
            return WATER_SPEED;
        }
        return player.onGround ? LAND_SPEED : AIR_SPEED;
    }

    /** Advance the simulation by {@code dt} seconds. */
    public void step(double dt) {
        // Determine medium from current position (water + previous grounded state).
        player.inWater = overlapsAnyWater();
        player.onWaterfall = overlapsAnyWaterfall();

        // Ease horizontal velocity toward the intended speed for this medium.
        player.vel.x = steerHorizontal(player.vel.x, moveDir * activeMoveSpeed(), dt);

        if (player.onWaterfall) {
            // Cling to the rushing water: climb slowly up/down, or slip gently.
            // Gravity is ignored while clinging.
            if (climbUp && !climbDown) {
                player.vel.y = -CLIMB_SPEED;
            } else if (climbDown && !climbUp) {
                player.vel.y = CLIMB_SPEED;
            } else {
                player.vel.y = WATERFALL_SLIP;
            }
            player.onGround = false;
            jumpRequested = false;
        } else {
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

        // Re-evaluate region state after moving so it is accurate for rendering.
        player.inWater = overlapsAnyWater();
        player.onWaterfall = overlapsAnyWaterfall();
    }

    /**
     * Move {@code vx} toward {@code target} by at most rate*dt, where the rate
     * depends on whether we're accelerating, turning around, or coasting.
     */
    private double steerHorizontal(double vx, double target, double dt) {
        double accel, turn, friction;
        if (player.inWater) {
            accel = WATER_ACCEL; turn = WATER_TURN; friction = WATER_FRICTION;
        } else if (player.onGround) {
            accel = LAND_ACCEL; turn = LAND_TURN; friction = LAND_FRICTION;
        } else {
            accel = AIR_ACCEL; turn = AIR_TURN; friction = AIR_FRICTION;
        }

        double rate;
        if (target == 0 || Math.abs(vx) > Math.abs(target) && Math.signum(vx) == Math.signum(target)) {
            rate = friction;                 // coasting, or above this medium's top speed
        } else if (vx != 0 && Math.signum(vx) != Math.signum(target)) {
            rate = turn;                     // braking against current motion
        } else {
            rate = accel;                    // speeding up in the held direction
        }

        double maxChange = rate * dt;
        double diff = target - vx;
        if (Math.abs(diff) <= maxChange) {
            return target;
        }
        return vx + Math.signum(diff) * maxChange;
    }

    private boolean overlapsAnyWater() {
        for (Water w : waters) {
            if (Collision.intersects(player, w)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlapsAnyWaterfall() {
        for (Waterfall f : waterfalls) {
            if (Collision.intersects(player, f)) {
                return true;
            }
        }
        return false;
    }
}
