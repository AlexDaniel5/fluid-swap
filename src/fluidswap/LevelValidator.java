package fluidswap;

import java.util.ArrayList;
import java.util.List;

/**
 * Static sanity checks for a built {@link World}'s terrain. Catches layout
 * mistakes that hand-placed coordinates make easy — a spawn inside a wall, a
 * finish floating in mid-air, a waterfall that doesn't reach the pools it is
 * meant to connect — before anyone has to play the level to find them.
 *
 * This checks local placement only; it does NOT prove the finish is reachable
 * (that needs a movement-aware search).
 */
public final class LevelValidator {

    private LevelValidator() {}

    /** @return human-readable problems; empty when the layout is valid. */
    public static List<String> validate(World w) {
        List<String> problems = new ArrayList<>();
        double r = w.player.radius;
        double sl = w.spawnX() - r, st = w.spawnY() - r, sr = w.spawnX() + r, sb = w.spawnY() + r;

        for (Block b : w.blocks) {
            if (b.overlaps(sl, st, sr, sb)) problems.add("spawn is inside a block at " + at(b));
        }
        for (Hazard h : w.hazards) {
            if (h.overlaps(sl, st, sr, sb)) problems.add("spawn is inside a hazard at " + at(h));
        }

        if (w.finishes.isEmpty()) problems.add("level has no finish");
        for (Finish f : w.finishes) {
            if (!hasStandableBlockUnder(w, f)) {
                problems.add("finish at " + at(f) + " has no block top to stand on inside it");
            }
        }

        for (int i = 0; i < w.blocks.size(); i++) {
            for (int j = i + 1; j < w.blocks.size(); j++) {
                if (w.blocks.get(i).overlaps(w.blocks.get(j))) {
                    problems.add("blocks overlap: " + at(w.blocks.get(i)) + " and " + at(w.blocks.get(j)));
                }
            }
        }

        for (Waterfall f : w.waterfalls) {
            if (!edgeInWater(w, f, f.top())) {
                problems.add("waterfall at " + at(f) + " does not start inside a pool");
            }
            if (!edgeInWater(w, f, f.bottom())) {
                problems.add("waterfall at " + at(f) + " does not end inside a pool");
            }
        }
        return problems;
    }

    /** Finish completes only when grounded, so a block top must lie within it. */
    private static boolean hasStandableBlockUnder(World w, Finish f) {
        for (Block b : w.blocks) {
            boolean xOverlap = b.right() > f.left() && b.left() < f.right();
            if (xOverlap && b.top() > f.top() && b.top() <= f.bottom()) return true;
        }
        return false;
    }

    /** True if horizontal line y (across the waterfall's width) lies within some pool. */
    private static boolean edgeInWater(World w, Waterfall f, double y) {
        for (Water water : w.waters) {
            boolean xOverlap = water.right() > f.left() && water.left() < f.right();
            if (xOverlap && y >= water.top() && y <= water.bottom()) return true;
        }
        return false;
    }

    private static String at(Rect r) {
        return "(" + (int) r.x + "," + (int) r.y + " " + (int) r.width + "x" + (int) r.height + ")";
    }
}
