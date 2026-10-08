package fluidswap;

/** Minimal mutable-ish 2D vector. Kept small and value-like for clarity. */
public final class Vec2 {
    public double x;
    public double y;

    public Vec2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void add(double dx, double dy) {
        this.x += dx;
        this.y += dy;
    }
}
