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
    public static final double JUMP_SPEED = 720.0;    // px / s (off land, one impulse)
    // In water there is no jump impulse: holding up is a sustained ascent (swim
    // up) and holding down a sustained dive. Steady speeds, not a sudden burst.
    public static final double WATER_SWIM_UP_SPEED = 380.0;
    public static final double WATER_SWIM_DOWN_SPEED = 240.0;
    // How fast the vertical swim velocity eases toward its target (px / s^2).
    // Because this is a fixed acceleration, reversing direction (ascend->dive)
    // takes longer the faster you were already moving, rather than snapping.
    public static final double WATER_SWIM_ACCEL = 2000.0;
    // Breaking the surface while holding jump launches the character out of the
    // water with a burst — a solid pop, a bit stronger than a standing land
    // jump, but not a rocket (so holding up doesn't fling you across the map).
    public static final double WATER_EXIT_JUMP_SPEED = 780.0;

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
    // Turning around is a roughly constant-TIME maneuver, not constant force:
    // the brake rate scales with how fast you're going, so reversing at water
    // speed is just as snappy as reversing at a slow walk. The per-medium TURN
    // above acts as a floor so slow turns stay crisp. Smaller = quicker.
    public static final double TURN_TIME = 0.07;      // seconds to bleed off speed

    // In water the character sinks slowly and can swim; gravity is reduced and
    // damped so it stays interactive rather than dropping like a stone.
    public static final double WATER_GRAVITY_SCALE = 0.25;
    public static final double WATER_DRAG = 3.0;      // per-second velocity damping

    // Waterfall climbing. Gravity is ignored while clinging; the character
    // climbs up/down at a deliberately slow pace, and slips down gently when no
    // vertical input is held.
    public static final double CLIMB_SPEED = 90.0;         // up/down climb (slow)
    public static final double WATERFALL_SLIP = 45.0;      // gentle downward drift

    // Terminal velocity. Without a cap a fall from the top of the level reaches
    // ~2,470 px/s (~41 px per 60 Hz frame), enough to tunnel through the 40 px
    // walls and grass blocks. Movement is also substepped (MAX_SUBSTEP_FRACTION
    // of the player's radius per substep) so even a long frame can't skip
    // through thin terrain.
    public static final double MAX_FALL_SPEED = 1200.0;    // px / s
    public static final double MAX_SUBSTEP_FRACTION = 0.5; // of player.radius

    // Dash (F): a short, fixed-length horizontal burst in the facing direction.
    // Gravity and input are ignored for its duration, so it is a dead-straight
    // line — no fall, no steering. Walls stop it. One dash per jump in mid-air
    // (refilled by touching ground, water or a waterfall), plus a short cooldown
    // between dashes. It grants no invulnerability.
    public static final double DASH_SPEED = 950.0;     // px / s
    public static final double DASH_DURATION = 0.16;   // seconds (~150 px)
    public static final double DASH_COOLDOWN = 0.35;   // seconds after a dash ends

    // A descending player lands on a one-way platform if their feet were at
    // most this far below its line on the previous substep (absorbs float error).
    public static final double PLATFORM_LANDING_TOLERANCE = 1.0; // px

    public final Player player;
    public final List<Block> blocks = new ArrayList<>();
    public final List<Water> waters = new ArrayList<>();
    public final List<Waterfall> waterfalls = new ArrayList<>();
    public final List<Finish> finishes = new ArrayList<>();
    public final List<Hazard> hazards = new ArrayList<>();
    public final List<Platform> platforms = new ArrayList<>(); // one-way (land from above)
    public final List<Urchin> urchins = new ArrayList<>();     // enemies (touch = death)

    // Input intent, set by the controller each frame.
    public double moveDir = 0;       // -1 left, +1 right, 0 none
    public boolean jumpRequested = false;
    public boolean climbUp = false;  // up/jump key held (climb a waterfall)
    public boolean climbDown = false; // down key held (descend a waterfall)
    public boolean dashRequested = false; // dash key pressed (one-shot, like a jump)

    // Count of dashes started, so the view can spawn a dash burst when it changes.
    public int dashes = 0;

    // Level completion. `finished` latches true the moment the player reaches a
    // finish region and only clears via restart(), so the goal can't be re-triggered. While
    // finished the player is locked into a gentle victory bob and ignores input.
    // `finishElapsed` is seconds since completion, driving the end sequence.
    public boolean finished = false;
    public double finishElapsed = 0;
    private double finishAnchorX, finishAnchorY;

    // Death + respawn. `dead` latches when the player enters a hazard and plays
    // a short death sequence (DEATH_DURATION) before respawning at the spawn
    // point. Input is ignored while dead. `deathX/deathY` anchor the effect.
    public static final double DEATH_DURATION = 1.0; // seconds of death effect
    public boolean dead = false;
    public double deathElapsed = 0;
    public double deathX, deathY;
    // True when the death came from an enemy: instead of auto-respawning, the
    // world stays on the "YOU DIED" screen until retry() is called.
    public boolean killedByEnemy = false;
    private final double spawnX, spawnY;

    // Water-exit event for view-layer effects (splash, drips). `waterExits`
    // counts every time the player leaves a pool into open air; the view watches
    // for it to change and spawns a splash at (waterExitX, waterExitY) on the
    // edge they crossed. (waterExitNX, waterExitNY) is that edge's outward
    // normal — (0,-1) out the top, (±1,0) out a side, (0,1) out the bottom —
    // and waterExitVX/VY is the player's velocity at that moment.
    public int waterExits = 0;
    public double waterExitX, waterExitY;
    public double waterExitNX, waterExitNY;
    public double waterExitVX, waterExitVY;

    public World(Player player) {
        this.player = player;
        this.spawnX = player.pos.x;
        this.spawnY = player.pos.y;
    }

    public double spawnX() { return spawnX; }
    public double spawnY() { return spawnY; }

    public void addBlock(Block b) { blocks.add(b); }
    public void addWater(Water w) { waters.add(w); }
    public void addWaterfall(Waterfall f) { waterfalls.add(f); }
    public void addFinish(Finish g) { finishes.add(g); }
    public void addHazard(Hazard h) { hazards.add(h); }
    public void addPlatform(Platform pf) { platforms.add(pf); }
    public void addUrchin(Urchin u) { urchins.add(u); }

    /**
     * The active horizontal speed given the character's current medium. A
     * waterfall only governs vertical climbing — horizontal speed is unaffected
     * by it, so it uses the normal water/land/air value.
     */
    public double activeMoveSpeed() {
        if (player.inWater) {
            return WATER_SPEED;
        }
        return player.onGround ? LAND_SPEED : AIR_SPEED;
    }

    /** Advance the simulation by {@code dt} seconds. */
    public void step(double dt) {
        if (finished) {
            // Victory: input is ignored and the player holds still at the flag,
            // so the level can't be disturbed or the goal re-triggered.
            finishElapsed += dt;
            moveDir = 0;
            jumpRequested = climbUp = climbDown = dashRequested = false;
            player.dashTime = 0;
            player.vel.x = player.vel.y = 0;
            player.pos.x = finishAnchorX;
            player.pos.y = finishAnchorY;
            player.inWater = overlapsAny(waters);
            player.onWaterfall = false;
            return;
        }

        if (dead) {
            // Death sequence: frozen at the death spot, input ignored, then
            // respawn at the start once the effect has played out.
            deathElapsed += dt;
            moveDir = 0;
            jumpRequested = climbUp = climbDown = dashRequested = false;
            player.dashTime = 0;
            player.vel.x = player.vel.y = 0;
            player.pos.x = deathX;
            player.pos.y = deathY;
            if (!killedByEnemy && deathElapsed >= DEATH_DURATION) {
                respawn();
            }
            return;
        }

        // Determine medium from current position (water + previous grounded state).
        player.inWater = overlapsAny(waters);
        player.onWaterfall = overlapsAny(waterfalls);
        Water startWater = deepestWater();
        boolean enteredStepInWater = startWater != null;

        updateDash(dt);
        if (!player.dashing()) {
            // Ease horizontal velocity toward the intended speed for this medium.
            player.vel.x = steerHorizontal(player.vel.x, moveDir * activeMoveSpeed(), dt);
        }

        Player.Medium medium = player.medium();
        if (player.dashing()) {
            // Mid-dash: a straight horizontal line. Gravity, swimming and
            // climbing are all suspended, and jump input is ignored.
            player.vel.x = player.facing * DASH_SPEED;
            player.vel.y = 0;
            player.onGround = false;
            jumpRequested = false;
        } else if (medium == Player.Medium.WATERFALL) {
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
        } else if (medium == Player.Medium.WATER) {
            // Swimming. Holding up/down eases the vertical velocity toward a
            // target ascent/dive speed at a fixed acceleration, so reversing
            // (ascend -> dive) is a gradual turn that takes longer the faster
            // you were going — no sudden snap. With no input the body sinks
            // gently under reduced, damped gravity.
            if (climbUp && !climbDown) {
                player.vel.y = approach(player.vel.y, -WATER_SWIM_UP_SPEED, WATER_SWIM_ACCEL * dt);
            } else if (climbDown && !climbUp) {
                player.vel.y = approach(player.vel.y, WATER_SWIM_DOWN_SPEED, WATER_SWIM_ACCEL * dt);
            } else {
                player.vel.y += GRAVITY * WATER_GRAVITY_SCALE * dt;
                player.vel.y *= Math.max(0, 1.0 - WATER_DRAG * dt);
            }
            jumpRequested = false;
        } else {
            // On land/air: a jump is a single impulse off the ground.
            if (jumpRequested && player.onGround) {
                player.vel.y = -JUMP_SPEED;
                player.onGround = false;
            }
            jumpRequested = false;
            player.vel.y += GRAVITY * dt;
        }
        player.vel.y = Math.min(player.vel.y, MAX_FALL_SPEED);

        // Integrate and collide in small substeps so fast motion can't tunnel.
        player.onGround = false;
        double dx = player.vel.x * dt, dy = player.vel.y * dt;
        double maxStep = Math.max(1e-6, player.radius * MAX_SUBSTEP_FRACTION);
        int substeps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / maxStep));
        double subDt = dt / substeps;
        for (int i = 0; i < substeps; i++) {
            // Re-read velocity each substep: a wall or floor hit zeroes it, and
            // the remaining substeps must not keep driving into the surface.
            moveAndCollide(player.vel.x * subDt, player.vel.y * subDt);
        }

        // Re-evaluate region state after moving so it is accurate for rendering.
        player.inWater = overlapsAny(waters);
        player.onWaterfall = overlapsAny(waterfalls);

        // Tick the dash down after moving (so its first step moves too); it also
        // ends early if it ran into a wall (its velocity was zeroed).
        if (player.dashing()) {
            player.dashTime -= dt;
            if (player.dashTime <= 0 || player.vel.x == 0) {
                endDash();
            }
        }

        // If this step carried the character up and out through the surface
        // while jump was held, launch them with a strong exit burst. (Not when
        // dashing out sideways — the dash stays a straight line.)
        if (enteredStepInWater && !player.inWater && !player.onWaterfall && climbUp
                && !player.dashing()) {
            player.vel.y = -WATER_EXIT_JUMP_SPEED;
            player.onGround = false;
        }

        // Record a water exit (into air, not into a waterfall) for the view.
        if (enteredStepInWater && !player.inWater && !player.onWaterfall) {
            recordWaterExit(startWater);
        }

        // Reaching the goal completes the level (latched; triggers once) — but
        // only once the player has actually landed on the finish platform, so
        // "LEVEL COMPLETE!" waits until they hit the ground, not mid-jump.
        // Falling into a hazard kills the player (checked only if not winning).
        if (player.onGround && overlapsAny(finishes)) {
            finished = true;
            finishElapsed = 0;
            finishAnchorX = player.pos.x;
            finishAnchorY = player.pos.y;
        } else if (touchesUrchin()) {
            die(true);
        } else if (overlapsAny(hazards)) {
            die(false);
        }
    }

    /**
     * Track facing, refill/cool down the dash, and start one if requested. The
     * active dash is ticked down after movement in {@link #step}.
     */
    private void updateDash(double dt) {
        if (!player.dashing() && moveDir != 0) {
            player.facing = moveDir > 0 ? 1 : -1;
        }
        player.dashCooldown = Math.max(0, player.dashCooldown - dt);
        if (player.onGround || player.inWater || player.onWaterfall) {
            player.dashReady = true;
        }

        if (dashRequested && !player.dashing() && player.dashReady && player.dashCooldown == 0) {
            player.dashTime = DASH_DURATION;
            player.dashReady = false;
            dashes++;
        }
        dashRequested = false;
    }

    /** Stop dashing: drop to normal top speed for the medium and start the cooldown. */
    private void endDash() {
        player.dashTime = 0;
        player.dashCooldown = DASH_COOLDOWN;
        double cap = activeMoveSpeed();
        player.vel.x = Math.max(-cap, Math.min(cap, player.vel.x));
    }

    private void die(boolean byEnemy) {
        player.dashTime = 0;
        dead = true;
        killedByEnemy = byEnemy;
        deathElapsed = 0;
        deathX = player.pos.x;
        deathY = player.pos.y;
    }

    private boolean touchesUrchin() {
        for (Urchin u : urchins) {
            if (u.touches(player)) {
                return true;
            }
        }
        return false;
    }

    /** Retry after an enemy death: back to the spawn with input cleared. */
    public void retry() {
        restart();
    }

    /**
     * Work out which edge of {@code w} the player just left through and record
     * the splash point on that edge. The player no longer overlaps {@code w},
     * so whichever side they're now fully past is the one they crossed (the
     * surface wins a corner exit, since that's the usual way out).
     */
    private void recordWaterExit(Water w) {
        waterExits++;
        waterExitVX = player.vel.x;
        waterExitVY = player.vel.y;
        double cx = Math.max(w.left(), Math.min(w.right(), player.pos.x));
        double cy = Math.max(w.top(), Math.min(w.bottom(), player.pos.y));
        if (player.bottom() <= w.top()) {
            waterExitX = cx; waterExitY = w.top();    waterExitNX = 0;  waterExitNY = -1;
        } else if (player.top() >= w.bottom()) {
            waterExitX = cx; waterExitY = w.bottom(); waterExitNX = 0;  waterExitNY = 1;
        } else if (player.right() <= w.left()) {
            waterExitX = w.left();  waterExitY = cy;  waterExitNX = -1; waterExitNY = 0;
        } else {
            waterExitX = w.right(); waterExitY = cy;  waterExitNX = 1;  waterExitNY = 0;
        }
    }

    /** Restart the level after completion: clear the win latch and respawn. */
    public void restart() {
        finished = false;
        finishElapsed = 0;
        moveDir = 0;
        jumpRequested = climbUp = climbDown = dashRequested = false;
        respawn();
    }

    /** Return the player to the spawn point with cleared motion/medium state. */
    private void respawn() {
        dead = false;
        killedByEnemy = false;
        deathElapsed = 0;
        player.pos.x = spawnX;
        player.pos.y = spawnY;
        player.vel.x = player.vel.y = 0;
        player.onGround = false;
        player.inWater = false;
        player.onWaterfall = false;
        player.dashTime = 0;
        player.dashCooldown = 0;
        player.dashReady = true;
        player.facing = 1;
    }

    /**
     * Move the player by (dx, dy) and resolve it against solid blocks and
     * one-way platforms. Sets {@code onGround} if anything is landed on; the
     * caller clears it once per step, before the first substep.
     */
    private void moveAndCollide(double dx, double dy) {
        double prevBottom = player.bottom(); // feet before this substep (one-way platforms)
        player.pos.add(dx, dy);

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

        // One-way platforms: a descending player that was above the line last
        // substep lands on top; a rising player passes straight up through it.
        for (Platform pf : platforms) {
            if (player.vel.y >= 0
                    && player.right() > pf.left() && player.left() < pf.right()
                    && prevBottom <= pf.top() + PLATFORM_LANDING_TOLERANCE
                    && player.bottom() >= pf.top()) {
                player.pos.y = pf.top() - player.radius;
                player.vel.y = 0;
                player.onGround = true;
            }
        }
    }

    /** @return true if the player's hitbox overlaps any rectangle in the list. */
    private boolean overlapsAny(List<? extends Rect> regions) {
        for (Rect r : regions) {
            if (Collision.intersects(player, r)) {
                return true;
            }
        }
        return false;
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
            // Braking against current motion (turning around): scale with the
            // reverse top speed so the maneuver takes ~TURN_TIME regardless of
            // how fast we're going. The medium's turn rate acts as a floor.
            rate = Math.max(turn, Math.abs(target) / TURN_TIME);
        } else {
            rate = accel;                    // speeding up in the held direction
        }

        return approach(vx, target, rate * dt);
    }

    /** Move {@code value} toward {@code target} by at most {@code maxDelta}. */
    private static double approach(double value, double target, double maxDelta) {
        double diff = target - value;
        if (Math.abs(diff) <= maxDelta) {
            return target;
        }
        return value + Math.signum(diff) * maxDelta;
    }

    /**
     * The water region the player overlaps most, or null. With overlapping or
     * touching pools this is the one they are actually "in", so a water exit is
     * attributed to the right pool's edge.
     */
    private Water deepestWater() {
        Water best = null;
        double bestArea = 0;
        for (Water w : waters) {
            double a = w.overlapArea(player);
            if (a > bestArea) {
                bestArea = a;
                best = w;
            }
        }
        return best;
    }
}
