package fluidswap;

/** Builds a small demo level of box terrain with mixed crest/blotchy flavors. */
public final class Level {

    public static final int WIDTH = 900;
    public static final int HEIGHT = 600;

    private Level() {}

    public static World build() {
        Player player = new Player(150, 100, 20);
        World world = new World(player);

        // Ground floor (crest-topped).
        world.addBlock(new Block(0, 540, WIDTH, 60, Block.Type.CREST));

        // Some floating platforms and rough blocks.
        world.addBlock(new Block(260, 440, 160, 30, Block.Type.CREST));
        world.addBlock(new Block(520, 360, 120, 120, Block.Type.BLOTCHY));
        world.addBlock(new Block(700, 470, 90, 70, Block.Type.BLOTCHY));
        world.addBlock(new Block(120, 300, 90, 40, Block.Type.SOLID));
        world.addBlock(new Block(400, 250, 70, 70, Block.Type.SOLID));

        // Left and right walls so the ball stays on screen.
        world.addBlock(new Block(-40, 0, 40, HEIGHT, Block.Type.SOLID));
        world.addBlock(new Block(WIDTH, 0, 40, HEIGHT, Block.Type.SOLID));

        return world;
    }
}
