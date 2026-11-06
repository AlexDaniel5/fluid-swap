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

    public static final double PLAYER_RADIUS = 20;

    // A waterfall pours in this far below the upper pool's surface, so the
    // climber surfaces inside that pool rather than at its very lip.
    public static final double WATERFALL_INSET = 40;
    // How far a finish region sinks into the platform it sits on, so a player
    // standing on the platform's top overlaps it.
    public static final double FINISH_SINK = 10;

    public static World build() {
        // Square 1 (spawn): the one-way platform runs through its MIDDLE, so you
        // spawn standing on the bar in the centre of the water (you can't sink
        // past it — you progress by swimming up and out).
        Water square1 = new Water(300, 900, 480, 360);          // x[300..780], y[900..1260]
        Platform spawnBar = new Platform(465, 1100, 150);       // mid-square line
        World world = new World(standingOn(spawnBar));
        world.addWater(square1);
        world.addPlatform(spawnBar);

        // Side walls + bottomless pit below everything.
        world.addBlock(new Block(-40, 0, 40, HEIGHT, Block.Type.SOLID));
        world.addBlock(new Block(WIDTH, 0, 40, HEIGHT, Block.Type.SOLID));
        world.addHazard(new Hazard(0, 1420, WIDTH, HEIGHT - 1420));

        // Square 2 — the leap target (~160px gap); its one-way line is the floor.
        Water square2 = floatingSquare(world, 940, 900, 480, 360); // x[940..1420]

        // Square 3: a water section with one grass block for footing.
        Water square3 = new Water(1360, 440, 640, 390);         // x[1360..2000], y[440..830]
        world.addWater(square3);
        Block square3Grass = new Block(1540, 760, 280, 48, Block.Type.GRASS);
        world.addBlock(square3Grass);
        // A sea urchin guards the middle of that grass block — touch it and you die.
        world.addUrchin(Urchin.restingOn(square3Grass, square3Grass.centerX(), 18, 14));

        // The single waterfall: climb it from square 2 up into square 3. It is
        // derived from the two pools so it always lines up flush with both.
        world.addWaterfall(waterfallBetween(square2, square3));

        // The finish: a dry grass platform off to the right, with a flag pole.
        Block finishPlatform = new Block(2180, 520, 280, 40, Block.Type.GRASS); // x[2180..2460]
        world.addBlock(finishPlatform);
        world.addFinish(finishOn(finishPlatform, 70, 180, 110));

        return world;
    }

    /** A player standing on the centre of a platform's line. */
    private static Player standingOn(Platform pf) {
        return new Player(pf.centerX(), pf.top() - PLAYER_RADIUS, PLAYER_RADIUS);
    }

    /** A floating rectangle of water sitting on a one-way platform line (its floor). */
    private static Water floatingSquare(World w, double x, double y, double width, double height) {
        Water water = new Water(x, y, width, height);
        w.addWater(water);
        w.addPlatform(new Platform(x, water.bottom(), width)); // one-way floor at the bottom
        return water;
    }

    /**
     * A waterfall connecting a lower pool to an upper pool that overlaps it
     * horizontally. The column spans their shared x-range (upper's left edge to
     * lower's right edge) and runs from just inside the upper pool down to the
     * lower pool's floor.
     */
    private static Waterfall waterfallBetween(Water lower, Water upper) {
        double left = Math.max(lower.left(), upper.left());
        double right = Math.min(lower.right(), upper.right());
        if (right <= left) {
            throw new IllegalArgumentException("pools don't overlap horizontally");
        }
        double top = upper.top() + WATERFALL_INSET;
        return new Waterfall(left, top, right - left, lower.bottom() - top);
    }

    /** A finish region sitting on (and sunk slightly into) a platform block. */
    private static Finish finishOn(Block platform, double offsetX, double width, double height) {
        return new Finish(platform.left() + offsetX, platform.top() - height + FINISH_SINK,
                width, height);
    }
}
