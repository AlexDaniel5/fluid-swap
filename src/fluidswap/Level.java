package fluidswap;

/** Builds a larger demo level: box terrain (mixed flavors) plus a water pool. */
public final class Level {

    public static final int WIDTH = 1400;
    public static final int HEIGHT = 900;

    private Level() {}

    public static World build() {
        Player player = new Player(180, 120, 20);
        World world = new World(player);

        // Ground floor (crest-topped), with a gap for the water pool.
        world.addBlock(new Block(0, 820, 700, 80, Block.Type.CREST));
        world.addBlock(new Block(1000, 820, 400, 80, Block.Type.CREST));

        // Walls of the water basin (between x=700 and x=1000).
        world.addBlock(new Block(640, 640, 60, 260, Block.Type.BLOTCHY));
        world.addBlock(new Block(1000, 640, 60, 260, Block.Type.BLOTCHY));
        world.addBlock(new Block(640, 860, 420, 40, Block.Type.BLOTCHY)); // basin bottom

        // Water pool sitting in the basin (non-solid region).
        world.addWater(new Water(700, 660, 300, 200));

        // Floating platforms and rough blocks across the wider map.
        world.addBlock(new Block(300, 680, 200, 30, Block.Type.CREST));
        world.addBlock(new Block(150, 500, 120, 40, Block.Type.SOLID));
        world.addBlock(new Block(480, 420, 90, 90, Block.Type.SOLID));
        world.addBlock(new Block(820, 400, 140, 140, Block.Type.BLOTCHY));
        world.addBlock(new Block(1120, 560, 180, 30, Block.Type.CREST));
        world.addBlock(new Block(1180, 360, 100, 50, Block.Type.SOLID));

        // Left and right walls so the ball stays on screen.
        world.addBlock(new Block(-40, 0, 40, HEIGHT, Block.Type.SOLID));
        world.addBlock(new Block(WIDTH, 0, 40, HEIGHT, Block.Type.SOLID));

        return world;
    }
}
