package fluidswap;

/** Circle-vs-AABB collision detection and resolution. */
public final class Collision {

    private Collision() {}

    /** Result of resolving one circle against one block. */
    public static final class Hit {
        public final boolean collided;
        /** How far the circle was pushed out, along the collision normal. */
        public final double pushX;
        public final double pushY;
        /** Unit normal pointing from the block toward the circle. */
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

    /** @return true if the circle overlaps the block at all. */
    public static boolean intersects(Player p, Block b) {
        double cx = clamp(p.pos.x, b.left(), b.right());
        double cy = clamp(p.pos.y, b.top(), b.bottom());
        double dx = p.pos.x - cx;
        double dy = p.pos.y - cy;
        return (dx * dx + dy * dy) < (p.radius * p.radius);
    }

    /**
     * Compute how to separate the circle from the block. Finds the closest
     * point on the box to the circle center, then pushes the circle out along
     * the vector to that point (or out the nearest face if the center is
     * inside the box).
     */
    public static Hit resolve(Player p, Block b) {
        double closestX = clamp(p.pos.x, b.left(), b.right());
        double closestY = clamp(p.pos.y, b.top(), b.bottom());

        double dx = p.pos.x - closestX;
        double dy = p.pos.y - closestY;
        double distSq = dx * dx + dy * dy;
        double r = p.radius;

        if (distSq >= r * r) {
            return Hit.NONE;
        }

        if (distSq > 1e-9) {
            // Center is outside the box: push along the line to the closest point.
            double dist = Math.sqrt(distSq);
            double nx = dx / dist;
            double ny = dy / dist;
            double overlap = r - dist;
            return new Hit(true, nx * overlap, ny * overlap, nx, ny);
        }

        // Center is inside the box: eject out of the nearest face.
        double leftPen = p.pos.x - b.left();
        double rightPen = b.right() - p.pos.x;
        double topPen = p.pos.y - b.top();
        double bottomPen = b.bottom() - p.pos.y;

        double minPen = Math.min(Math.min(leftPen, rightPen), Math.min(topPen, bottomPen));
        if (minPen == leftPen) {
            return new Hit(true, -(leftPen + r), 0, -1, 0);
        } else if (minPen == rightPen) {
            return new Hit(true, rightPen + r, 0, 1, 0);
        } else if (minPen == topPen) {
            return new Hit(true, 0, -(topPen + r), 0, -1);
        } else {
            return new Hit(true, 0, bottomPen + r, 0, 1);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
