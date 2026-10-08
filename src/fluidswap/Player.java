package fluidswap;

/** The main character. For now it is just a circle with simple physics. */
public final class Player {

    public final Vec2 pos;          // center of the circle
    public final Vec2 vel = new Vec2(0, 0);
    public final double radius;

    /** True when the circle is resting on something below it (lets us jump). */
    public boolean onGround = false;

    public Player(double x, double y, double radius) {
        this.pos = new Vec2(x, y);
        this.radius = radius;
    }
}
