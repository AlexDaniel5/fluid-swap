package fluidswap;

/**
 * A rectangular region of water. Unlike {@link Block} it is NOT solid — the
 * character passes through it — but while overlapping it the character moves
 * faster and feels buoyant.
 */
public final class Water {

    public final double x;
    public final double y;
    public final double width;
    public final double height;

    public Water(double x, double y, double width, double height) {
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
