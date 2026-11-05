package fluidswap;

/**
 * A rectangular region of water. Unlike {@link Block} it is NOT solid — the
 * character passes through it — but while overlapping it the character moves
 * faster and feels buoyant.
 */
public final class Water extends Rect {

    public Water(double x, double y, double width, double height) {
        super(x, y, width, height);
    }
}
