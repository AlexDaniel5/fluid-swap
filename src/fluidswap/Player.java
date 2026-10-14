package fluidswap;

/**
 * The main character. It is drawn as a circle, but its collision hitbox is an
 * axis-aligned box. {@code radius} is used both for drawing and as the box
 * half-extent, so the square hitbox matches the circle sprite's bounds.
 */
public final class Player {

    public final Vec2 pos;          // center of the hitbox (and the circle)
    public final Vec2 vel = new Vec2(0, 0);
    public final double radius;     // circle draw radius == box half-extent

    /** True when the box is resting on something below it (lets us jump). */
    public boolean onGround = false;
    /** True when the hitbox overlaps a water region. */
    public boolean inWater = false;
    /** True when the hitbox overlaps a waterfall column (can cling and climb). */
    public boolean onWaterfall = false;

    public Player(double x, double y, double radius) {
        this.pos = new Vec2(x, y);
        this.radius = radius;
    }

    // Axis-aligned hitbox edges.
    public double halfW()  { return radius; }
    public double halfH()  { return radius; }
    public double left()   { return pos.x - radius; }
    public double right()  { return pos.x + radius; }
    public double top()    { return pos.y - radius; }
    public double bottom() { return pos.y + radius; }
}
