package fluidswap;

/**
 * A lethal region — the jagged "crushing depths" at the bottom of an abyss
 * trench. Like {@link Water}, {@link Waterfall} and {@link Finish} it is a
 * non-solid trigger checked by {@link World} after movement; entering it kills
 * the player, who then respawns at the level's start.
 */
public final class Hazard {

    public final double x;
    public final double y;
    public final double width;
    public final double height;

    public Hazard(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double left()   { return x; }
    public double right()  { return x + width; }
    public double top()    { return y; }
    public double bottom() { return y + height; }
}
