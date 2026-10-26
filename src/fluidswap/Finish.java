package fluidswap;

/**
 * The end-of-level goal: a non-solid trigger region — an ancient glowing water
 * shrine sunk into the summit pool. Like {@link Water} and {@link Waterfall} it
 * never collides; {@link World} checks it as a trigger after movement, and the
 * first time the player's hitbox enters it the level is completed.
 *
 * (This is the "glowing area" the engine's comments always reserved a slot for.)
 */
public final class Finish {

    public final double x;
    public final double y;
    public final double width;
    public final double height;

    public Finish(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double left()   { return x; }
    public double right()  { return x + width; }
    public double top()    { return y; }
    public double bottom() { return y + height; }

    /** Center of the shrine — used to anchor the victory pose and effects. */
    public double centerX() { return x + width / 2.0; }
    public double centerY() { return y + height / 2.0; }
}
