package fluidswap;

/**
 * The end-of-level goal: a non-solid trigger region — an ancient glowing water
 * shrine sunk into the summit pool. Like {@link Water} and {@link Waterfall} it
 * never collides; {@link World} checks it as a trigger after movement, and the
 * first time the player's hitbox enters it the level is completed.
 *
 * (This is the "glowing area" the engine's comments always reserved a slot for.)
 */
public final class Finish extends Rect {

    public Finish(double x, double y, double width, double height) {
        super(x, y, width, height);
    }
}
