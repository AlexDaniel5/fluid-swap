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
