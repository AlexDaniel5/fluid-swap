package fluidswap;

/**
 * A larger demo level: a chain of floating <b>islands</b> at increasing heights,
 * a sea along the bottom, and four <b>waterfalls</b> that spill off the island
 * edges. Each waterfall is a vertical column the character can cling to and
 * climb slowly — that is how you ascend from one island to the next.
 *
 * Intended route: start island → drop into the sea → climb waterfall A up to the
 * mid island → climb B up to the high island → climb D up to the top rock.
 * (Waterfall C is an alternate route from the sea up to the right island.)
 */
public final class Level {

    public static final int WIDTH = 1800;
    public static final int HEIGHT = 1000;

    private Level() {}

    public static World build() {
        Player player = new Player(200, 700, 20);
        World world = new World(player);

        // --- Boundaries ------------------------------------------------------
        world.addBlock(new Block(-40, 0, 40, HEIGHT, Block.Type.SOLID)); // left wall
        world.addBlock(new Block(WIDTH, 0, 40, HEIGHT, Block.Type.SOLID)); // right wall
        world.addBlock(new Block(0, 960, WIDTH, 40, Block.Type.BLOTCHY));  // ocean floor

        // The sea: a wide non-solid pool resting on the ocean floor.
        world.addWater(new Water(0, 860, WIDTH, 100));

        // --- Islands (low to high) ------------------------------------------
        world.addBlock(new Block(80, 780, 380, 50, Block.Type.CREST));    // start island
        world.addBlock(new Block(620, 560, 320, 50, Block.Type.CREST));   // mid island
        world.addBlock(new Block(980, 360, 300, 50, Block.Type.CREST));   // high island
        world.addBlock(new Block(1420, 680, 340, 50, Block.Type.CREST));  // right island
        world.addBlock(new Block(1500, 480, 220, 40, Block.Type.BLOTCHY));// rough ledge
        world.addBlock(new Block(1300, 180, 180, 40, Block.Type.SOLID));  // top rock

        // --- Waterfalls (vertical climb routes) -----------------------------
        // A: sea -> mid island (spills off the mid island's left edge).
        world.addWaterfall(new Waterfall(560, 540, 60, 360));
        // B: mid island -> high island.
        world.addWaterfall(new Waterfall(920, 340, 60, 220));
        // C: sea -> right island (alternate route).
        world.addWaterfall(new Waterfall(1360, 660, 60, 300));
        // D: high island -> top rock.
        world.addWaterfall(new Waterfall(1240, 160, 60, 220));

        return world;
    }
}
