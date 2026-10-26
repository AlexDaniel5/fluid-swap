package fluidswap;

/**
 * A box-shaped piece of terrain. Collision is always treated as an
 * axis-aligned bounding box (AABB); {@link Type} only changes how it is drawn.
 *
 * "Crest" and "blotchy" are purely visual terrain flavors for now.
 */
public final class Block {

    public enum Type {
        /** Plain solid box. */
        SOLID,
        /** Ridged top edge ("crest") — think a hill cap or platform lip. */
        CREST,
        /** Irregular, speckled fill ("blotchy") — rough ground. */
        BLOTCHY,
        /** Dirt body with a grassy top and blades — the main platform terrain. */
        GRASS
    }

    public final double x;
    public final double y;
    public final double width;
    public final double height;
    public final Type type;

    public Block(double x, double y, double width, double height, Type type) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.type = type;
    }

    public double left()   { return x; }
    public double right()  { return x + width; }
    public double top()    { return y; }
    public double bottom() { return y + height; }
}
