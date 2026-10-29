package fluidswap;

/**
 * Headless tests proving the circle character collides with box terrain.
 * No external test framework needed — run with:
 *   java -cp out fluidswap.CollisionTest
 * Exits non-zero if any assertion fails.
 */
public final class CollisionTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testIntersectsOverlapping();
        testNoCollisionWhenApart();
        testBallRestsOnGround();
        testBallStoppedByWall();
        testBallFallsThroughEmptySpace();
        testCornerPushOut();
        testGradualTurnAround();
        testSpeedByMedium();
        testWaterDetection();
        testTurnAroundTimeIsSpeedIndependent();
        testWaterSwimUpIsSustainedAscent();
        testWaterVerticalReversalIsGradual();
        testWaterExitJumpBurst();
        testWaterExitEventAtSurface();
        testWaterExitEventOutOfSide();
        testReachingFinishCompletesLevel();
        testFinishNeedsGround();
        testFinishNotTriggeredWhenAway();
        testHazardKillsAndRespawns();
        testNoDeathOnSafeGround();
        testSpawnPlatformIsSafe();
        testOneWayPlatformLandsFromAbove();
        testOneWayPlatformPassesFromBelow();
        testWaterfallDetection();
        testWaterfallClimbUp();
        testWaterfallSlipIsSlow();

        System.out.printf("%n%d passed, %d failed%n", passed, failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    // --- individual cases ------------------------------------------------

    private static void testIntersectsOverlapping() {
        Player p = new Player(100, 100, 20);
        Block b = new Block(110, 90, 50, 50, Block.Type.SOLID); // overlaps the circle
        check("intersects detects overlap", Collision.intersects(p, b));
    }

    private static void testNoCollisionWhenApart() {
        Player p = new Player(100, 100, 20);
        Block b = new Block(300, 300, 50, 50, Block.Type.SOLID);
        check("no collision when far apart", !Collision.intersects(p, b));
        check("resolve reports no hit when apart", !Collision.resolve(p, b).collided);
    }

    private static void testBallRestsOnGround() {
        Player p = new Player(100, 0, 20);
        World w = new World(p);
        w.addBlock(new Block(0, 500, 400, 100, Block.Type.CREST)); // ground at y=500

        simulate(w, 3.0); // let it fall and settle

        // Bottom of the circle should rest on the ground surface.
        double bottom = p.pos.y + p.radius;
        check("ball lands and rests on ground (bottom≈500, got " + round(bottom) + ")",
                Math.abs(bottom - 500) < 1.0);
        check("ball is marked onGround", p.onGround);
        check("vertical velocity settled to ~0 (got " + round(p.vel.y) + ")",
                Math.abs(p.vel.y) < 1.0);
    }

    private static void testBallStoppedByWall() {
        Player p = new Player(100, 100, 20);
        World w = new World(p);
        w.addBlock(new Block(0, 460, 400, 140, Block.Type.CREST));  // floor to stand on
        w.addBlock(new Block(200, 0, 40, 460, Block.Type.SOLID));   // wall; left face at x=200
        w.moveDir = 1; // drive right into the wall

        simulate(w, 2.0);

        check("ball stopped by wall (right≈200, got " + round(p.pos.x + p.radius) + ")",
                p.pos.x + p.radius <= 200.5);
        check("ball did not tunnel through wall", p.pos.x < 200);
    }

    private static void testBallFallsThroughEmptySpace() {
        Player p = new Player(100, 100, 20);
        World w = new World(p); // no terrain at all
        double startY = p.pos.y;

        simulate(w, 0.5);

        check("ball falls under gravity when nothing to hit", p.pos.y > startY + 10);
        check("ball is not grounded in empty space", !p.onGround);
    }

    private static void testCornerPushOut() {
        // Circle center diagonally off a block corner, overlapping it.
        Player p = new Player(100, 100, 20);
        Block b = new Block(105, 105, 60, 60, Block.Type.BLOTCHY);
        Collision.Hit hit = Collision.resolve(p, b);
        check("corner overlap is detected", hit.collided);

        // Applying the push should remove the overlap.
        p.pos.add(hit.pushX, hit.pushY);
        check("push resolves the corner overlap", !Collision.intersects(p, b));
    }

    private static void testSpeedByMedium() {
        Player p = new Player(100, 100, 20);
        World w = new World(p);

        p.inWater = false; p.onGround = true;
        check("land speed is slowest", w.activeMoveSpeed() == World.LAND_SPEED);

        p.onGround = false;
        check("air speed is medium", w.activeMoveSpeed() == World.AIR_SPEED);

        p.inWater = true;
        check("water speed is fastest", w.activeMoveSpeed() == World.WATER_SPEED);

        check("ordering land < air < water",
                World.LAND_SPEED < World.AIR_SPEED && World.AIR_SPEED < World.WATER_SPEED);
    }

    private static void testWaterDetection() {
        Player p = new Player(100, 300, 20);
        World w = new World(p);
        w.addWater(new Water(50, 200, 200, 200)); // covers the player
        w.step(1.0 / 240.0);
        check("player detected in water", p.inWater);

        // Moving in water uses the fast speed and covers more ground than on land.
        Player land = new Player(100, 100, 20);
        World lw = new World(land);
        lw.addBlock(new Block(0, 140, 400, 60, Block.Type.CREST)); // floor to stand on
        land.onGround = true;
        lw.moveDir = 1;
        for (int i = 0; i < 60; i++) lw.step(1.0 / 60.0);

        Player swim = new Player(100, 300, 20);
        World sw = new World(swim);
        sw.addWater(new Water(-1000, 0, 4000, 2000)); // big pool, always in water
        sw.moveDir = 1;
        for (int i = 0; i < 60; i++) sw.step(1.0 / 60.0);

        double landDist = land.pos.x - 100;
        double swimDist = swim.pos.x - 100;
        check("travels farther in water than on land (water=" + round(swimDist)
                + ", land=" + round(landDist) + ")", swimDist > landDist);
    }

    private static void testGradualTurnAround() {
        Player p = new Player(100, 100, 20);
        World w = new World(p);
        w.addBlock(new Block(-2000, 120, 6000, 60, Block.Type.CREST)); // long floor
        w.moveDir = 1;
        simulate(w, 1.0);
        check("reaches land top speed moving right (got " + round(p.vel.x) + ")",
                Math.abs(p.vel.x - World.LAND_SPEED) < 0.01);

        w.moveDir = -1;
        w.step(1.0 / 60.0);
        check("one frame after reversing, still moving right (got " + round(p.vel.x) + ")",
                p.vel.x > 0);

        simulate(w, 1.0);
        check("eventually reaches top speed moving left (got " + round(p.vel.x) + ")",
                Math.abs(p.vel.x + World.LAND_SPEED) < 0.01);

        w.moveDir = 0;
        w.step(1.0 / 60.0);
        check("releasing input slows rather than stops instantly", p.vel.x < 0);
        simulate(w, 1.0);
        check("coasts to a stop with no input", p.vel.x == 0);
    }

    private static void testTurnAroundTimeIsSpeedIndependent() {
        // Reversing direction should take about the same (short) time whether
        // you're crawling on land or racing through water — not proportional to
        // speed. We count frames for vx to cross back through zero.
        int landFrames = framesToReverse(false);
        int waterFrames = framesToReverse(true);

        check("fast (water) turnaround isn't much slower than slow (land) one"
                + " (land=" + landFrames + "f, water=" + waterFrames + "f)",
                waterFrames <= landFrames + 2);
        check("turnaround is quick (land reverses within ~0.1s: " + landFrames + "f)",
                landFrames <= 25); // 25 frames @240Hz ≈ 0.1s
    }

    /** Frames for vx to cross back through zero after reversing input at top speed. */
    private static int framesToReverse(boolean inWater) {
        double dt = 1.0 / 240.0;
        Player p = new Player(100, 100, 20);
        World w = new World(p);
        if (inWater) {
            w.addWater(new Water(-5000, 0, 10000, 5000));
        } else {
            w.addBlock(new Block(-5000, 120, 10000, 60, Block.Type.CREST));
            p.onGround = true;
        }
        w.moveDir = 1;
        for (int i = 0; i < 240; i++) w.step(dt); // reach top speed moving right
        w.moveDir = -1;                            // flip input
        int frames = 0;
        while (p.vel.x > 0 && frames < 1000) { w.step(dt); frames++; }
        return frames;
    }

    private static void testWaterSwimUpIsSustainedAscent() {
        // Holding up in water eases into a steady ascent (not an impulse that
        // decays): once ramped up, the upward velocity holds at the swim-up
        // speed for as long as it is held.
        double dt = 1.0 / 240.0;
        Player swim = new Player(100, 300, 20);
        World sw = new World(swim);
        sw.addWater(new Water(-1000, 0, 4000, 4000)); // deep, fully submerged
        sw.climbUp = true;                             // hold "up"

        double startY = swim.pos.y;
        for (int i = 0; i < 60; i++) sw.step(dt);      // let the ascent ramp in
        double v1 = swim.vel.y;      // upward is negative
        for (int i = 0; i < 60; i++) sw.step(dt);
        double vLater = swim.vel.y;

        check("swimming up ascends (upward velocity, got " + round(v1) + ")", v1 < 0);
        check("ascent settles at the steady swim-up speed (got " + round(-v1) + ")",
                Math.abs(-v1 - World.WATER_SWIM_UP_SPEED) < 0.01);
        check("ascent is sustained, not a decaying burst (earlier=" + round(v1)
                + ", later=" + round(vLater) + ")", Math.abs(vLater - v1) < 0.01);
        check("ascent actually carries the swimmer up", swim.pos.y < startY - 50);

        // A land jump is still a single impulse at ~JUMP_SPEED.
        Player land = new Player(100, 100, 20);
        World lw = new World(land);
        land.onGround = true;
        lw.jumpRequested = true;
        lw.step(dt);
        double landJumpSpeed = -land.vel.y;
        check("land jump unchanged (~JUMP_SPEED, got " + round(landJumpSpeed) + ")",
                Math.abs(landJumpSpeed - World.JUMP_SPEED) < 10);
    }

    private static void testWaterVerticalReversalIsGradual() {
        // Switching from ascending to diving in water is a gradual turn, not an
        // instant snap, and reversing from a higher speed takes longer.
        int fast = framesToReverseVertical(120); // ramp to full ascent first
        int slow = framesToReverseVertical(2);    // reverse from a low speed

        check("vertical reversal in water is not instant (took " + fast + "f)", fast > 1);
        check("reversing a faster ascent takes longer (fast=" + fast + "f, slow=" + slow + "f)",
                fast > slow);
    }

    /** Ascend for {@code upFrames}, then dive; count frames for vy to cross zero. */
    private static int framesToReverseVertical(int upFrames) {
        double dt = 1.0 / 240.0;
        Player p = new Player(100, 4000, 20);
        World w = new World(p);
        w.addWater(new Water(-5000, 0, 10000, 8000)); // very deep, always submerged
        w.climbUp = true;
        for (int i = 0; i < upFrames; i++) w.step(dt);
        w.climbUp = false;
        w.climbDown = true; // now dive
        int frames = 0;
        while (p.vel.y < 0 && frames < 2000) { w.step(dt); frames++; }
        return frames;
    }

    private static void testWaterExitJumpBurst() {
        // Holding jump while breaking the surface launches out with a strong
        // burst — much faster than the steady swim-up or a standing land jump.
        double dt = 1.0 / 240.0;
        Player p = new Player(100, 230, 20);
        World w = new World(p);
        w.addWater(new Water(-1000, 200, 4000, 2000)); // surface at y=200, player just below
        double peakUp = 0;        // most-negative (fastest upward) vel.y seen
        boolean leftWater = false;
        for (int i = 0; i < 240; i++) {
            w.climbUp = true;       // swimming up
            w.jumpRequested = true; // jump held
            w.step(dt);
            peakUp = Math.min(peakUp, p.vel.y);
            if (!p.inWater) leftWater = true;
        }
        check("player breaks the surface", leftWater);
        check("exit jump bursts faster than a land jump (peak up=" + round(-peakUp) + ")",
                -peakUp > World.JUMP_SPEED);
        check("exit burst is the strong WATER_EXIT_JUMP_SPEED (got " + round(-peakUp) + ")",
                Math.abs(-peakUp - World.WATER_EXIT_JUMP_SPEED) < 15);
    }

    private static void testWaterExitEventAtSurface() {
        // Leaving water records one exit event, placed on the surface line,
        // so the view can play a splash there.
        double dt = 1.0 / 240.0;
        Player p = new Player(100, 230, 20);
        World w = new World(p);
        w.addWater(new Water(-1000, 200, 4000, 2000)); // surface at y=200
        check("no water exit before leaving", w.waterExits == 0);
        for (int i = 0; i < 240 && w.waterExits == 0; i++) {
            w.climbUp = true;
            w.step(dt);
        }
        check("leaving the water records one exit (got " + w.waterExits + ")", w.waterExits == 1);
        check("exit splash sits on the surface (y=" + round(w.waterExitY) + ")",
                Math.abs(w.waterExitY - 200) < 0.5);
        check("exit records upward velocity (vy=" + round(w.waterExitVY) + ")", w.waterExitVY < 0);
        check("surface exit normal points up", w.waterExitNX == 0 && w.waterExitNY == -1);
    }

    private static void testWaterExitEventOutOfSide() {
        // Swimming out the right wall (even while also rising) splashes on that
        // wall, not up on the surface.
        double dt = 1.0 / 240.0;
        Player p = new Player(360, 300, 20);
        World w = new World(p);
        w.addWater(new Water(0, 200, 400, 400)); // right wall at x=400, surface y=200
        for (int i = 0; i < 240 && w.waterExits == 0; i++) {
            w.moveDir = 1;
            w.climbUp = true;
            w.step(dt);
        }
        check("swimming out the side records an exit", w.waterExits == 1);
        check("side exit normal points right (n=" + w.waterExitNX + "," + w.waterExitNY + ")",
                w.waterExitNX == 1 && w.waterExitNY == 0);
        check("side splash sits on the wall (x=" + round(w.waterExitX) + ")",
                Math.abs(w.waterExitX - 400) < 0.5);
        check("side splash is below the surface (y=" + round(w.waterExitY) + ")",
                w.waterExitY > 200 && w.waterExitY < 600);
    }

    private static void testReachingFinishCompletesLevel() {
        double dt = 1.0 / 240.0;
        Player p = new Player(100, 100, 20);
        World w = new World(p);
        w.addBlock(new Block(0, 300, 400, 60, Block.Type.GRASS)); // finish platform
        w.addFinish(new Finish(0, 240, 400, 120));                // over its standing band
        check("level not finished before landing", !w.finished);

        simulate(w, 2.0); // fall and land on the finish platform
        check("landing on the finish completes the level", w.finished);

        // Latches and locks the player: driving input does nothing afterward.
        double lockedX = p.pos.x;
        w.moveDir = 1;
        w.jumpRequested = true;
        for (int i = 0; i < 240; i++) w.step(dt);
        check("completion latches (stays finished)", w.finished);
        check("player is locked after finishing (no horizontal travel, x="
                + round(p.pos.x) + ")", Math.abs(p.pos.x - lockedX) < 0.5);
        check("finish timer advances for the end sequence (" + round(w.finishElapsed) + "s)",
                w.finishElapsed > 0.9);
    }

    private static void testFinishNeedsGround() {
        // Overlapping the finish mid-air (not grounded) must NOT complete it.
        double dt = 1.0 / 240.0;
        Player p = new Player(100, 100, 20);
        World w = new World(p);
        w.addFinish(new Finish(0, 0, 400, 400)); // player is inside it but falling
        for (int i = 0; i < 10; i++) w.step(dt);
        check("finish does not trigger while airborne in the region",
                !w.finished && !p.onGround);
    }

    private static void testFinishNotTriggeredWhenAway() {
        Player p = new Player(100, 100, 20);
        World w = new World(p);
        w.addBlock(new Block(0, 140, 400, 60, Block.Type.CREST)); // floor
        w.addFinish(new Finish(3000, 3000, 100, 100));            // far away
        p.onGround = true;
        for (int i = 0; i < 120; i++) w.step(1.0 / 240.0);
        check("goal not triggered when the player is nowhere near it", !w.finished);
    }

    private static void testHazardKillsAndRespawns() {
        double dt = 1.0 / 240.0;
        Player p = new Player(500, 100, 20);   // spawn point captured here
        World w = new World(p);
        w.addBlock(new Block(400, 180, 200, 60, Block.Type.CREST)); // ground under spawn
        w.addHazard(new Hazard(0, 1000, 2000, 200));                // deadly depths, far below

        for (int i = 0; i < 180; i++) w.step(dt); // settle on the spawn ground
        double restY = p.pos.y;
        check("not dead during normal play", !w.dead);

        // Fall into the hazard.
        p.pos.x = 800;
        p.pos.y = 1100;
        w.step(dt);
        check("entering a hazard kills the player", w.dead);

        // Input is ignored while dead (can't crawl out).
        w.moveDir = 1;
        w.jumpRequested = true;
        for (int i = 0; i < (int) (World.DEATH_DURATION * 240) - 5; i++) w.step(dt);
        check("still dead during the death sequence", w.dead);

        // After the sequence, respawn at the spawn point.
        for (int i = 0; i < 20; i++) w.step(dt);
        check("respawns (death cleared)", !w.dead);
        check("respawns at the spawn x (got " + round(p.pos.x) + ")", Math.abs(p.pos.x - 500) < 1.0);

        // And lands safely back on the spawn ground.
        for (int i = 0; i < 240; i++) w.step(dt);
        check("respawn settles on safe ground (y≈" + round(restY) + ", got " + round(p.pos.y) + ")",
                p.onGround && Math.abs(p.pos.y - restY) < 2.0 && !w.dead);
    }

    private static void testNoDeathOnSafeGround() {
        Player p = new Player(100, 100, 20);
        World w = new World(p);
        w.addBlock(new Block(0, 140, 400, 60, Block.Type.CREST));
        w.addHazard(new Hazard(2000, 2000, 100, 100)); // far away
        p.onGround = true;
        for (int i = 0; i < 240; i++) w.step(1.0 / 240.0);
        check("no death when standing safely away from hazards", !w.dead);
    }

    private static void testSpawnPlatformIsSafe() {
        // In the real level the player should settle on the spawn square's
        // one-way floor and not fall into the pit; goal/pit/platforms exist.
        World w = Level.build();
        Player p = w.player;
        for (int i = 0; i < 240 * 3; i++) w.step(1.0 / 240.0);
        check("spawn settles on the one-way floor (grounded)", p.onGround);
        check("player does not die on the spawn platform", !w.dead);
        check("spawn floats in water", p.inWater);
        check("level has a finish, a pit hazard and a one-way platform",
                !w.finishes.isEmpty() && !w.hazards.isEmpty() && !w.platforms.isEmpty());
    }

    private static void testOneWayPlatformLandsFromAbove() {
        double dt = 1.0 / 240.0;
        Player p = new Player(100, 100, 20); // starts above the line
        World w = new World(p);
        w.addPlatform(new Platform(50, 300, 200)); // line at y=300, x[50..250]
        simulate(w, 2.0); // fall onto it
        check("a descending player lands on the one-way line (bottom≈300, got "
                + round(p.pos.y + p.radius) + ")", Math.abs((p.pos.y + p.radius) - 300) < 1.0);
        check("resting on the one-way line marks onGround", p.onGround);
        check("did not tunnel through the one-way line", p.pos.y + p.radius <= 300.5);
    }

    private static void testOneWayPlatformPassesFromBelow() {
        double dt = 1.0 / 240.0;
        Player p = new Player(100, 360, 20); // starts below the line
        World w = new World(p);
        w.addPlatform(new Platform(50, 300, 200)); // line at y=300
        p.vel.y = -600; // launched upward
        boolean roseAbove = false;
        for (int i = 0; i < 60; i++) {
            w.step(dt);
            if (p.pos.y + p.radius < 300) { roseAbove = true; break; }
        }
        check("a rising player passes up through the one-way line", roseAbove);
    }

    private static void testWaterfallDetection() {
        Player p = new Player(100, 300, 20);
        World w = new World(p);
        w.addWaterfall(new Waterfall(60, 100, 80, 400)); // covers the player
        w.step(1.0 / 240.0);
        check("player detected on waterfall", p.onWaterfall);
        // A waterfall only governs vertical climbing; horizontal speed is the
        // normal medium (airborne here, since not grounded or in water).
        check("waterfall leaves horizontal speed unaffected (got "
                + round(w.activeMoveSpeed()) + ")",
                w.activeMoveSpeed() == World.AIR_SPEED);
    }

    private static void testWaterfallClimbUp() {
        Player p = new Player(100, 400, 20);
        World w = new World(p);
        w.addWaterfall(new Waterfall(60, 0, 80, 800)); // tall column around the player
        w.climbUp = true;                               // hold "up" to climb

        double startY = p.pos.y;
        simulate(w, 1.0);

        // Climbing beats gravity: the player ends up clearly higher than it began.
        check("holding up climbs the waterfall (rose from " + round(startY)
                + " to " + round(p.pos.y) + ")", p.pos.y < startY - 50);
        check("still on the waterfall while climbing", p.onWaterfall);
    }

    private static void testWaterfallSlipIsSlow() {
        // On a waterfall with no input, the player slips down far slower than a
        // real gravity free-fall would carry them over the same time.
        Player p = new Player(100, 100, 20);
        World w = new World(p);
        w.addWaterfall(new Waterfall(60, 0, 80, 4000));
        double startY = p.pos.y;
        simulate(w, 1.0);
        double slipDist = p.pos.y - startY;

        Player fall = new Player(100, 100, 20);
        World fw = new World(fall); // empty: pure gravity free-fall
        double fallStart = fall.pos.y;
        simulate(fw, 1.0);
        double fallDist = fall.pos.y - fallStart;

        check("clinging slip is downward but gentle (slip=" + round(slipDist) + ")",
                slipDist > 0 && slipDist < 120);
        check("clinging is far slower than free-fall (slip=" + round(slipDist)
                + ", freefall=" + round(fallDist) + ")", slipDist < fallDist / 2);
    }

    // --- helpers ---------------------------------------------------------

    /** Step the world in small fixed increments for {@code seconds}. */
    private static void simulate(World w, double seconds) {
        double dt = 1.0 / 240.0;
        for (double t = 0; t < seconds; t += dt) {
            w.step(dt);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
