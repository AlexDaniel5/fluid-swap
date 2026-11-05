package fluidswap;

/**
 * A lethal region — the jagged "crushing depths" at the bottom of an abyss
 * trench. Like {@link Water}, {@link Waterfall} and {@link Finish} it is a
 * non-solid trigger checked by {@link World} after movement; entering it kills
 * the player, who then respawns at the level's start.
 */
public final class Hazard extends Rect {

    public Hazard(double x, double y, double width, double height) {
        super(x, y, width, height);
    }
}
