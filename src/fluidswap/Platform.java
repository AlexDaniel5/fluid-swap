package fluidswap;

/**
 * A one-way platform drawn as a thin horizontal line. You can swim/jump <b>up
 * through</b> it from below, but you cannot pass <b>down</b> through it — a
 * descending player lands on top. Used as the floor of a floating water square
 * so the character floats instead of sinking out the bottom into the pit.
 *
 * Unlike {@link Block}, collision is resolved specially in {@link World} (it
 * needs the player's previous position), so this is just the geometry.
 */
public final class Platform {

    public final double x;
    public final double y;      // the line's y (its top surface)
    public final double width;

    public Platform(double x, double y, double width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public double left()  { return x; }
    public double right() { return x + width; }
    public double top()   { return y; }
}
