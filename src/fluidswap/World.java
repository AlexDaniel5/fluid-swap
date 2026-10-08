package fluidswap;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the player and terrain and advances the simulation. This is kept free
 * of any rendering/windowing code so it can be driven headlessly by tests.
 *
 * NOTE: "glowing areas" that grant the player effects are intentionally NOT
 * implemented yet. They would live here as a separate list of trigger regions
 * checked after movement; the collision pipeline below is structured to make
 * adding them straightforward.
 */
public final class World {

    public static final double GRAVITY = 1800.0;      // px / s^2
    public static final double MOVE_SPEED = 320.0;    // px / s
    public static final double JUMP_SPEED = 720.0;    // px / s

    public final Player player;
    public final List<Block> blocks = new ArrayList<>();

    // Input intent, set by the controller each frame.
    public double moveDir = 0;       // -1 left, +1 right, 0 none
    public boolean jumpRequested = false;

    public World(Player player) {
        this.player = player;
    }

    public void addBlock(Block b) {
        blocks.add(b);
    }

    /** Advance the simulation by {@code dt} seconds. */
    public void step(double dt) {
        // Horizontal intent.
        player.vel.x = moveDir * MOVE_SPEED;

        // Jump only when grounded.
        if (jumpRequested && player.onGround) {
            player.vel.y = -JUMP_SPEED;
            player.onGround = false;
        }
        jumpRequested = false;

        // Gravity.
        player.vel.y += GRAVITY * dt;

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
    }
}
