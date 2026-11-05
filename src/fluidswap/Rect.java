package fluidswap;

/**
 * Axis-aligned rectangle shared by every piece of terrain — solid
 * {@link Block}s, one-way {@link Platform}s, and the non-solid trigger regions
 * ({@link Water}, {@link Waterfall}, {@link Hazard}, {@link Finish}). Subclasses
 * add only what makes them behave differently; geometry and overlap tests live
 * here once.
 */
public abstract class Rect {

    public final double x;
    public final double y;
    public final double width;
    public final double height;

    protected Rect(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public final double left()    { return x; }
    public final double right()   { return x + width; }
    public final double top()     { return y; }
    public final double bottom()  { return y + height; }
    public final double centerX() { return x + width / 2.0; }
    public final double centerY() { return y + height / 2.0; }

    /** @return true if the two rectangles overlap with positive area. */
    public final boolean overlaps(Rect o) {
        return right() > o.left() && left() < o.right()
            && bottom() > o.top() && top() < o.bottom();
    }

    /** @return true if the rectangle overlaps the given box (edges exclusive). */
    public final boolean overlaps(double l, double t, double r, double b) {
        return right() > l && left() < r && bottom() > t && top() < b;
    }

    /** @return the overlap area with the player's hitbox (0 if apart). */
    public final double overlapArea(Player p) {
        double ox = Math.min(p.right(), right()) - Math.max(p.left(), left());
        double oy = Math.min(p.bottom(), bottom()) - Math.max(p.top(), top());
        return (ox > 0 && oy > 0) ? ox * oy : 0;
    }
}
