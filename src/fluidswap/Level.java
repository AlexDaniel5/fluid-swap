package fluidswap;

/**
 * A big open testing area (much larger than the viewport, so the camera scrolls
 * to follow the character). It is a chain of chunky floating <b>islands</b>
 * climbing up and to the right out of a wide <b>sea</b>, linked by
 * <b>waterfalls</b> you cling to and climb. Every island has a <b>pond</b>
 * carved into its top to swim in, and a few loose platforms are scattered about
 * for jump testing.
 *
 * Intended route: start island → drop into the sea → climb waterfall A, then B,
 * C, D up the chain to the summit island. Waterfall E is an alternate sea climb
 * up to the far-right island.
 */
public final class Level {

    public static final int WIDTH = 4200;
    public static final int HEIGHT = 1800;

    private Level() {}

    public static World build() {
        Player player = new Player(200, 1300, 20);
        World world = new World(player);

        // --- Boundaries ------------------------------------------------------
        world.addBlock(new Block(-40, 0, 40, HEIGHT, Block.Type.SOLID));   // left wall
        world.addBlock(new Block(WIDTH, 0, 40, HEIGHT, Block.Type.SOLID)); // right wall
        world.addBlock(new Block(0, 1760, WIDTH, 40, Block.Type.BLOTCHY)); // ocean floor

        // The sea: a wide, deep non-solid pool resting on the ocean floor.
        world.addWater(new Water(0, 1500, WIDTH, 300));

        // --- Islands (ascending chain, each a basin holding a pond) ---------
        //      x     top  leftW pondW rightW thick pondDepth
        island(world, 120, 1360, 180, 220, 180, 240, 90);  // start island  -> right 700
        island(world, 820, 1120, 160, 200, 180, 220, 80);  // island 2      -> right 1360
        island(world, 1380, 880, 160, 200, 180, 220, 80);  // island 3      -> right 1920
        island(world, 1940, 640, 160, 220, 180, 220, 80);  // island 4      -> right 2500
        island(world, 2520, 400, 180, 240, 200, 220, 90);  // summit island -> right 3140
        island(world, 3400, 1150, 180, 240, 200, 250, 90); // far-right isle -> right 4020

        // --- Waterfalls (vertical climb routes) -----------------------------
        waterfall(world, 760, 1100, 1540);  // A: sea      -> island 2
        waterfall(world, 1320, 860, 1120);  // B: island 2 -> island 3
        waterfall(world, 1880, 620, 880);   // C: island 3 -> island 4
        waterfall(world, 2460, 380, 640);   // D: island 4 -> summit island
        waterfall(world, 3340, 1130, 1540); // E: sea      -> far-right island

        // --- Loose platforms for jump testing -------------------------------
        world.addBlock(new Block(500, 1150, 160, 30, Block.Type.SOLID));
        world.addBlock(new Block(1000, 1480, 180, 30, Block.Type.SOLID));
        world.addBlock(new Block(2700, 1300, 220, 30, Block.Type.CREST));
        world.addBlock(new Block(3600, 800, 200, 30, Block.Type.BLOTCHY));

        return world;
    }

    /**
     * Builds a basin island: a left land block and a right land block with a
     * base between them that forms both the island underside and the pond
     * floor; water fills the notch on top. Walk the rims, drop in, swim, climb
     * back out. Returns the island's right edge x.
     */
    private static double island(World w, double x, double top, double leftW,
                                 double pondW, double rightW, double thickness,
                                 double pondDepth) {
        double pondX = x + leftW;
        double floorTop = top + pondDepth;
        w.addBlock(new Block(x, top, leftW, thickness, Block.Type.CREST));        // left land
        w.addBlock(new Block(pondX + pondW, top, rightW, thickness, Block.Type.CREST)); // right land
        w.addBlock(new Block(pondX, floorTop, pondW, thickness - pondDepth, Block.Type.BLOTCHY)); // base / pond floor
        w.addWater(new Water(pondX, top, pondW, pondDepth));                      // pond
        return x + leftW + pondW + rightW;
    }

    /** A 60px-wide waterfall column spanning {@code top..bottom} at {@code x}. */
    private static void waterfall(World w, double x, double top, double bottom) {
        w.addWaterfall(new Waterfall(x, top, 60, bottom - top));
    }
}
