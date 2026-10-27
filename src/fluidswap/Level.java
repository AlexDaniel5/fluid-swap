package fluidswap;

/**
 * A short, water-forward course. You spawn inside a floating square of water
 * that rests on a <b>one-way platform</b> (a thin line you can swim up through
 * but not sink down through). Burst up and leap across the gap to a second
 * square, ride the single <b>waterfall</b> up to a third square with a grass
 * block, then make one last jump onto the dry <b>finish</b> platform (off to the
 * right) with its flag pole. Everything below the squares is a bottomless pit —
 * fall in and you die and respawn back in the spawn square.
 */
public final class Level {

    public static final int WIDTH = 2800;
    public static final int HEIGHT = 1700;

    private Level() {}

    public static World build() {
        // Spawn standing on the one-way platform that runs through the middle
        // of the spawn square (square center is (540, 1080); the platform top is
        // at 1100 so the radius-20 player rests exactly on it).
        Player player = new Player(540, 1080, 20);
        World world = new World(player);

        // Side walls + bottomless pit below everything.
        world.addBlock(new Block(-40, 0, 40, HEIGHT, Block.Type.SOLID));
        world.addBlock(new Block(WIDTH, 0, 40, HEIGHT, Block.Type.SOLID));
        world.addHazard(new Hazard(0, 1420, WIDTH, HEIGHT - 1420));

        // Square 1 (spawn): the one-way platform runs through its MIDDLE, so you
        // spawn standing on the bar in the centre of the water (full width, so
        // you can't sink past it — you progress by swimming up and out).
        world.addWater(new Water(300, 900, 480, 360));    // x[300..780], y[900..1260]
        world.addPlatform(new Platform(465, 1100, 150));  // mid-square line

        // Square 2 — the leap target (~160px gap); its one-way line is the floor.
        floatingSquare(world, 940, 900, 480, 360);        // x[940..1420]

        // The single waterfall: climb it from square 2 up into square 3. Its
        // column spans exactly from square 3's left edge (1360) to square 2's
        // right edge (1420), so it lines up flush against both squares.
        world.addWaterfall(new Waterfall(1360, 480, 60, 780)); // x[1360..1420], y[480..1260]

        // Square 3: a water section with one grass block for footing.
        world.addWater(new Water(1360, 440, 640, 390));        // x[1360..2000], y[440..830]
        world.addBlock(new Block(1540, 760, 280, 48, Block.Type.GRASS));

        // The finish: a dry grass platform off to the right, with a flag pole.
        world.addBlock(new Block(2180, 520, 280, 40, Block.Type.GRASS)); // x[2180..2460]
        world.addFinish(new Finish(2250, 420, 180, 110));                // sits on the platform

        return world;
    }

    /** A floating rectangle of water sitting on a one-way platform line (its floor). */
    private static void floatingSquare(World w, double x, double y, double width, double height) {
        w.addWater(new Water(x, y, width, height));
        w.addPlatform(new Platform(x, y + height, width)); // one-way floor at the bottom
    }
}
