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

    /** Last horizontal direction moved (+1 right, -1 left); the dash goes this way. */
    public int facing = 1;
    /** Seconds left in the current dash (0 when not dashing). */
    public double dashTime = 0;
    /** Seconds until another dash may start. */
    public double dashCooldown = 0;
    /**
     * Whether a dash is available. Spent by dashing; refilled by touching the
     * ground, water or a waterfall — so only one dash per jump in mid-air.
     */
    public boolean dashReady = true;

    /** @return true while a dash is in progress. */
    public boolean dashing() { return dashTime > 0; }

    /**
     * What the character is moving through. When regions overlap (a waterfall
     * poured into a pool, say) the priority is explicit and in declaration
     * order: WATERFALL beats WATER beats LAND beats AIR.
     */
    public enum Medium { WATERFALL, WATER, LAND, AIR }

    /** The current medium, derived from the region flags by the priority above. */
    public Medium medium() {
        if (onWaterfall) return Medium.WATERFALL;
        if (inWater)     return Medium.WATER;
        return onGround ? Medium.LAND : Medium.AIR;
    }

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
