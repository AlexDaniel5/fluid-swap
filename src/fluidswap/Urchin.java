package fluidswap;

/**
 * A spiky sea urchin enemy. It sits still on the terrain; touching it kills the
 * player outright. Unlike falling into a {@link Hazard} (which auto-respawns),
 * an urchin death shows a "YOU DIED" screen and waits for the player to retry.
 *
 * Its lethal zone is a circle: the solid body plus part of the spine length, so
 * brushing just the spine tips is forgiving rather than an unfair box corner.
 */
public final class Urchin {

    /** Fraction of the spine length (beyond the body) that is lethal. */
    public static final double SPINE_REACH = 0.5;

    public final double x, y;        // centre of the body
    public final double bodyRadius;  // round shell
    public final double spineLength; // how far spines stick out past the shell

    public Urchin(double x, double y, double bodyRadius, double spineLength) {
        this.x = x;
        this.y = y;
        this.bodyRadius = bodyRadius;
        this.spineLength = spineLength;
    }

    /** An urchin resting on top of a block, centred at {@code cx}. */
    public static Urchin restingOn(Block b, double cx, double bodyRadius, double spineLength) {
        return new Urchin(cx, b.top() - bodyRadius, bodyRadius, spineLength);
    }

    /** Radius of the lethal circle. */
    public double hitRadius() {
        return bodyRadius + spineLength * SPINE_REACH;
    }

    /** @return true if the player's circle touches the urchin's lethal circle. */
    public boolean touches(Player p) {
        double dx = p.pos.x - x, dy = p.pos.y - y;
        double reach = p.radius + hitRadius();
        return dx * dx + dy * dy < reach * reach;
    }

    /** Outer extent including full spines (for drawing / culling). */
    public double outerRadius() {
        return bodyRadius + spineLength;
    }
}
