package fluidswap;

/**
 * A larger demo level: a chain of big floating <b>islands</b> at increasing
 * heights, a sea along the bottom, and four <b>waterfalls</b> that spill off the
 * island edges. Each waterfall is a vertical column the character can cling to
 * and climb slowly — that is how you ascend from one island to the next.
 *
 * Every island is a chunky landmass with a <b>pond</b> carved into its top: two
 * raised land sides and a floor between them hold a small body of water you can
 * drop into and swim across.
 *
 * Intended route: start island → drop into the sea → climb waterfall A up to the
 * mid island (swim its pond to cross) → climb B up to the high island → climb D
 * up to the top rock. (Waterfall C is an alternate route from the sea up to the
 * right island.)
 */
public final class Level {

    public static final int WIDTH = 1800;
    public static final int HEIGHT = 1000;

    private Level() {}

    public static World build() {
        Player player = new Player(130, 680, 20);
        World world = new World(player);

        // --- Boundaries ------------------------------------------------------
        world.addBlock(new Block(-40, 0, 40, HEIGHT, Block.Type.SOLID));   // left wall
        world.addBlock(new Block(WIDTH, 0, 40, HEIGHT, Block.Type.SOLID)); // right wall
        world.addBlock(new Block(0, 960, WIDTH, 40, Block.Type.BLOTCHY));  // ocean floor

        // The sea: a wide non-solid pool resting on the ocean floor.
        world.addWater(new Water(0, 860, WIDTH, 100));

        // --- Islands (low to high), each a basin holding a pond -------------
        // Each island = left land + right land + a base block between them that
        // forms both the island underside and the pond floor; water fills the
        // notch on top. Walk the rims, drop in, swim, and climb back out.

        // Start island (home; big pond to splash in).
        world.addBlock(new Block(60, 740, 150, 200, Block.Type.CREST));    // left land
        world.addBlock(new Block(370, 740, 170, 200, Block.Type.CREST));   // right land
        world.addBlock(new Block(210, 810, 160, 130, Block.Type.BLOTCHY)); // base / pond floor
        world.addWater(new Water(210, 740, 160, 70));                      // pond

        // Mid island (reached by waterfall A; its pond must be swum to cross).
        world.addBlock(new Block(620, 560, 110, 180, Block.Type.CREST));
        world.addBlock(new Block(830, 560, 110, 180, Block.Type.CREST));
        world.addBlock(new Block(730, 620, 100, 120, Block.Type.BLOTCHY));
        world.addWater(new Water(730, 560, 100, 60));

        // High island (reached by waterfall B).
        world.addBlock(new Block(980, 360, 110, 180, Block.Type.CREST));
        world.addBlock(new Block(1190, 360, 90, 180, Block.Type.CREST));
        world.addBlock(new Block(1090, 420, 100, 120, Block.Type.BLOTCHY));
        world.addWater(new Water(1090, 360, 100, 60));

        // Right island (alternate route via waterfall C).
        world.addBlock(new Block(1420, 680, 140, 200, Block.Type.CREST));
        world.addBlock(new Block(1680, 680, 80, 200, Block.Type.CREST));
        world.addBlock(new Block(1560, 740, 120, 140, Block.Type.BLOTCHY));
        world.addWater(new Water(1560, 680, 120, 60));

        // Summit rock (reached by waterfall D) and a floating rough ledge.
        world.addBlock(new Block(1300, 160, 220, 60, Block.Type.SOLID));
        world.addBlock(new Block(1500, 460, 220, 40, Block.Type.BLOTCHY));

        // --- Waterfalls (vertical climb routes) -----------------------------
        // A: sea -> mid island (spills off the mid island's left edge).
        world.addWaterfall(new Waterfall(560, 540, 60, 400));
        // B: mid island -> high island.
        world.addWaterfall(new Waterfall(920, 340, 60, 220));
        // C: sea -> right island (alternate route).
        world.addWaterfall(new Waterfall(1360, 660, 60, 300));
        // D: high island -> summit rock.
        world.addWaterfall(new Waterfall(1240, 160, 60, 200));

        return world;
    }
}
