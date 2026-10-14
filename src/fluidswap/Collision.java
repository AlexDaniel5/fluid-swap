package fluidswap;

/** AABB-vs-AABB collision detection and resolution (player box vs terrain box). */
public final class Collision {

    private Collision() {}

    /** Result of resolving the player box against one block. */
    public static final class Hit {
        public final boolean collided;
        /** How far the box was pushed out, along the collision normal. */
        public final double pushX;
        public final double pushY;
        /** Unit normal pointing from the block toward the player. */
        public final double normalX;
        public final double normalY;

        private Hit(boolean collided, double pushX, double pushY, double nx, double ny) {
            this.collided = collided;
            this.pushX = pushX;
            this.pushY = pushY;
            this.normalX = nx;
            this.normalY = ny;
        }

        static final Hit NONE = new Hit(false, 0, 0, 0, 0);
    }

    /** @return true if the player's box overlaps the block's box at all. */
    public static boolean intersects(Player p, Block b) {
        return p.right() > b.left() && p.left() < b.right()
            && p.bottom() > b.top() && p.top() < b.bottom();
    }

    /** @return true if the player's box overlaps a water region. */
    public static boolean intersects(Player p, Water w) {
        return p.right() > w.left() && p.left() < w.right()
            && p.bottom() > w.top() && p.top() < w.bottom();
    }

    /** @return true if the player's box overlaps a waterfall column. */
    public static boolean intersects(Player p, Waterfall f) {
        return p.right() > f.left() && p.left() < f.right()
            && p.bottom() > f.top() && p.top() < f.bottom();
    }

    /**
     * Separate the player box from the block using the minimum translation
     * vector: push out along whichever axis has the smaller penetration.
     */
    public static Hit resolve(Player p, Block b) {
        if (!intersects(p, b)) {
            return Hit.NONE;
        }

        // Overlap depth on each axis.
        double overlapX = Math.min(p.right(), b.right()) - Math.max(p.left(), b.left());
        double overlapY = Math.min(p.bottom(), b.bottom()) - Math.max(p.top(), b.top());

        if (overlapX < overlapY) {
            // Resolve horizontally, toward whichever side the player center is on.
            if (p.pos.x < (b.left() + b.right()) / 2.0) {
                return new Hit(true, -overlapX, 0, -1, 0); // push left
            } else {
                return new Hit(true, overlapX, 0, 1, 0);   // push right
            }
        } else {
            // Resolve vertically.
            if (p.pos.y < (b.top() + b.bottom()) / 2.0) {
                return new Hit(true, 0, -overlapY, 0, -1); // push up (landing on top)
            } else {
                return new Hit(true, 0, overlapY, 0, 1);   // push down (hit ceiling)
            }
        }
    }
}
