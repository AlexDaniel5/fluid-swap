package fluidswap;

/**
 * A vertical column of falling water. Like {@link Water} it is NOT solid — the
 * character passes through it — but instead of simply swimming, while
 * overlapping a waterfall the character can <em>cling</em> to the rushing water
 * and climb it slowly (up or down). Gravity does not apply while on a waterfall;
 * if the player holds no vertical input they slip down gently.
 *
 * Waterfalls are the vertical "ladders" that connect islands at different
 * heights.
 */
public final class Waterfall extends Rect {

    public Waterfall(double x, double y, double width, double height) {
        super(x, y, width, height);
    }
}
