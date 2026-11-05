package fluidswap;

/**
 * A box-shaped piece of solid terrain. Collision is always treated as an
 * axis-aligned bounding box (AABB); {@link Type} only changes how it is drawn.
 */
public final class Block extends Rect {

    public enum Type {
        /** Plain solid box — used for the level's boundary walls. */
        SOLID,
        /** Dirt body with a grassy top and blades — the main platform terrain. */
        GRASS
    }

    public final Type type;

    public Block(double x, double y, double width, double height, Type type) {
        super(x, y, width, height);
        this.type = type;
    }
}
