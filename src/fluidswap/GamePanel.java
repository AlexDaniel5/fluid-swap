package fluidswap;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Swing panel: runs the fixed-step loop, reads keys, and draws the world. */
public final class GamePanel extends JPanel {

    // The window is a viewport into the (much larger) world; the camera scrolls.
    public static final int VIEW_WIDTH = 1280;
    public static final int VIEW_HEIGHT = 720;

    private final World world;

    private boolean left, right, jump, down;
    private boolean dashHeld; // F is down (a dash fires once per press, not on key repeat)
    private long frame; // animation counter for the waterfalls

    // Blocks are static, so each is rendered once into a sprite (see
    // renderBlockSprite) and blitted every frame.
    private final Map<Block, BufferedImage> blockSprites = new IdentityHashMap<>();
    // Extra world-space margin when culling against the view, so things that
    // draw slightly outside their bounds (blades, glows) don't pop at the edge.
    private static final int CULL_MARGIN = 32;

    // Camera top-left in world coordinates (eased toward the player each tick).
    private double camX, camY;
    private boolean camInit;

    // Short fade-in played right after a respawn (view-layer only).
    private static final double RESPAWN_FADE = 0.4;
    private boolean prevDead;
    private double respawnFade;

    // Water-exit splash (view-layer only): droplets thrown up from the surface
    // and a short dripping trail shed by the player while they're still wet.
    private static final double SPLASH_GRAVITY = 1400.0; // px / s^2 on droplets
    private static final double DRIP_DURATION = 0.7;     // seconds of dripping
    private static final double SPLASH_COOLDOWN = 0.15;  // ignore surface jitter
    private final List<Droplet> droplets = new ArrayList<>();
    private final Random fx = new Random();
    private int seenWaterExits;
    private double dripTime, dripAccum, splashCooldown;

    // "Play Again" button on the victory overlay (screen space). It fades in
    // with the overlay and becomes clickable once BUTTON_DELAY has passed, so a
    // held key or stray click right at the finish doesn't instantly restart.
    private static final double BUTTON_DELAY = 0.6;
    // The "YOU DIED" screen (enemy deaths) shows its Retry button after the
    // death burst has had a moment to play.
    private static final double DIED_BUTTON_DELAY = 0.9;
    private final Rectangle overlayBtn = new Rectangle();
    private boolean btnHover;

    private static final class Droplet {
        double x, y, vx, vy, life, maxLife, size;
        boolean smoke; // void wisp from a dash: no gravity, drifts and fades
    }

    // Dash effects (view-layer only): fading afterimages left along the dash
    // path, plus a puff of void smoke where it starts.
    private static final double GHOST_LIFE = 0.22;
    private final List<double[]> ghosts = new ArrayList<>(); // {x, y, life}
    private int seenDashes;

    public GamePanel(World world) {
        this.world = world;
        setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
        setBackground(new Color(246, 245, 250));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int c = e.getKeyCode();
                if ((c == KeyEvent.VK_ENTER || c == KeyEvent.VK_R) && buttonReady()) {
                    playAgain();
                    return;
                }
                setKey(c, true);
            }
            @Override public void keyReleased(KeyEvent e) { setKey(e.getKeyCode(), false); }
        });

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                boolean hover = buttonReady() && overlayBtn.contains(e.getPoint());
                if (hover != btnHover) {
                    btnHover = hover;
                    setCursor(Cursor.getPredefinedCursor(hover ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                }
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (buttonReady() && overlayBtn.contains(e.getPoint())) {
                    playAgain();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);

        // ~60 FPS loop.
        Timer timer = new Timer(16, e -> tick(16 / 1000.0));
        timer.start();
    }

    private void setKey(int code, boolean down) {
        switch (code) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> left = down;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> right = down;
            case KeyEvent.VK_SPACE, KeyEvent.VK_UP, KeyEvent.VK_W -> jump = down;
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> this.down = down;
            case KeyEvent.VK_F -> {
                if (down && !dashHeld) {
                    world.dashRequested = true;
                }
                dashHeld = down;
            }
        }
    }

    /** True when the victory "Play Again" or the death "Retry" button is clickable. */
    private boolean buttonReady() {
        if (world.finished) {
            return world.finishElapsed >= BUTTON_DELAY;
        }
        return world.dead && world.killedByEnemy && world.deathElapsed >= DIED_BUTTON_DELAY;
    }

    /** Restart the level and reset view-only effects back to a fresh start. */
    private void playAgain() {
        world.restart();
        left = right = jump = down = dashHeld = false;
        droplets.clear();
        ghosts.clear();
        seenDashes = world.dashes;
        dripTime = dripAccum = splashCooldown = 0;
        seenWaterExits = world.waterExits;
        prevDead = false;
        respawnFade = RESPAWN_FADE; // fade in at the spawn like a respawn
        camInit = false;            // snap the camera back to the start
        btnHover = false;
        setCursor(Cursor.getDefaultCursor());
        requestFocusInWindow();
    }

    private void tick(double dt) {
        world.moveDir = (right ? 1 : 0) - (left ? 1 : 0);
        if (jump) {
            world.jumpRequested = true;
        }
        world.climbUp = jump;    // hold up/jump to climb a waterfall
        world.climbDown = down;  // hold down to descend a waterfall
        boolean wasDead = prevDead;
        world.step(dt);
        if (wasDead && !world.dead) {
            respawnFade = RESPAWN_FADE; // just respawned: fade back in from dark
        }
        if (respawnFade > 0) {
            respawnFade = Math.max(0, respawnFade - dt);
        }
        prevDead = world.dead;
        updateDashFx(dt);
        updateSplash(dt);
        updateCamera(dt);
        frame++;
        repaint();
    }

    /** Puff of smoke when a dash starts; afterimages while it runs; age them. */
    private void updateDashFx(double dt) {
        Player p = world.player;
        if (world.dashes != seenDashes) {
            seenDashes = world.dashes;
            for (int i = 0; i < 14; i++) {
                Droplet d = new Droplet();
                d.smoke = true;
                d.x = p.pos.x - p.facing * p.radius * 0.6 + (fx.nextDouble() - 0.5) * 8;
                d.y = p.pos.y + (fx.nextDouble() - 0.5) * p.radius * 1.6;
                d.vx = -p.facing * (60 + fx.nextDouble() * 160);
                d.vy = (fx.nextDouble() - 0.5) * 120 - 30;
                d.life = d.maxLife = 0.3 + fx.nextDouble() * 0.3;
                d.size = 1 + fx.nextInt(2);
                droplets.add(d);
            }
        }
        for (Iterator<double[]> it = ghosts.iterator(); it.hasNext(); ) {
            double[] gh = it.next();
            gh[2] -= dt;
            if (gh[2] <= 0) it.remove();
        }
        if (p.dashing() && !world.dead) {
            ghosts.add(new double[] {p.pos.x, p.pos.y, GHOST_LIFE});
            // A thin streak of wisps shed from the trailing edge.
            Droplet d = new Droplet();
            d.smoke = true;
            d.x = p.pos.x - p.facing * p.radius;
            d.y = p.pos.y + (fx.nextDouble() - 0.5) * p.radius * 1.4;
            d.vx = -p.facing * 40;
            d.vy = -20 - fx.nextDouble() * 30;
            d.life = d.maxLife = 0.25 + fx.nextDouble() * 0.2;
            d.size = 1;
            droplets.add(d);
        }
    }

    /** Spawn a splash when the player leaves water, then advance all particles. */
    private void updateSplash(double dt) {
        splashCooldown = Math.max(0, splashCooldown - dt);
        if (world.waterExits != seenWaterExits) {
            seenWaterExits = world.waterExits;
            if (splashCooldown == 0) {
                spawnSplash();
                splashCooldown = SPLASH_COOLDOWN;
            }
        }

        // While still wet, the player sheds the odd drip as they fly.
        Player p = world.player;
        if (dripTime > 0) {
            dripTime = Math.max(0, dripTime - dt);
            if (p.inWater || world.dead) {
                dripTime = 0;
            } else {
                dripAccum += dt * 30 * (dripTime / DRIP_DURATION); // tapers off
                while (dripAccum >= 1) {
                    dripAccum -= 1;
                    addDroplet(p.pos.x + (fx.nextDouble() - 0.5) * p.radius * 1.6,
                            p.pos.y + p.radius * 0.6,
                            p.vel.x * 0.3 + (fx.nextDouble() - 0.5) * 40,
                            p.vel.y * 0.2 + 40,
                            0.35 + fx.nextDouble() * 0.25, 2 + fx.nextDouble() * 2);
                }
            }
        }

        for (Iterator<Droplet> it = droplets.iterator(); it.hasNext(); ) {
            Droplet d = it.next();
            d.life -= dt;
            if (d.life <= 0) {
                it.remove();
                continue;
            }
            if (d.smoke) {
                double drag = Math.max(0, 1 - 6 * dt);
                d.vx *= drag;
                d.vy = d.vy * drag - 40 * dt; // curls gently upward
            } else {
                d.vy += SPLASH_GRAVITY * dt;
            }
            d.x += d.vx * dt;
            d.y += d.vy * dt;
        }
    }

    /**
     * Burst of droplets thrown out of whichever edge the player left through,
     * scaled by how hard they were moving out of it.
     */
    private void spawnSplash() {
        double x = world.waterExitX, y = world.waterExitY;
        double nx = world.waterExitNX, ny = world.waterExitNY; // outward normal
        double tx = -ny, ty = nx;                                // along the edge
        double vx = world.waterExitVX, vy = world.waterExitVY;

        // Power from speed out through the edge: the surface is judged against
        // the exit burst, a side against top swim speed.
        double outSpeed = Math.max(0, vx * nx + vy * ny);
        double maxOut = ny != 0 ? World.WATER_EXIT_JUMP_SPEED : World.WATER_SPEED;
        double power = Math.min(1.0, outSpeed / maxOut);
        double strength = 0.4 + 0.6 * power;
        double r = world.player.radius;
        double along = vx * tx + vy * ty; // carried along the edge

        int count = (int) (10 + 22 * strength);
        double base = Math.atan2(ny, nx);
        for (int i = 0; i < count; i++) {
            // Fan outward, mostly within ±60° of the edge's normal.
            double ang = base + (fx.nextDouble() - 0.5) * (Math.PI * 2 / 3);
            double speed = (180 + fx.nextDouble() * 320) * strength;
            double off = (fx.nextDouble() - 0.5) * r * 2; // spread along the edge
            addDroplet(x + tx * off, y + ty * off,
                    Math.cos(ang) * speed + tx * along * 0.35,
                    Math.sin(ang) * speed + ty * along * 0.35,
                    0.5 + fx.nextDouble() * 0.5, 2.5 + fx.nextDouble() * 4 * strength);
        }

        dripTime = DRIP_DURATION;
        dripAccum = 0;
    }

    private void addDroplet(double x, double y, double vx, double vy, double life, double size) {
        Droplet d = new Droplet();
        d.x = x; d.y = y; d.vx = vx; d.vy = vy;
        d.life = d.maxLife = life;
        d.size = size;
        droplets.add(d);
    }

    /** Ease the camera toward keeping the player centered, clamped to the world. */
    private void updateCamera(double dt) {
        int viewW = getWidth() > 0 ? getWidth() : VIEW_WIDTH;
        int viewH = getHeight() > 0 ? getHeight() : VIEW_HEIGHT;
        Player p = world.player;

        double targetX = clampCam(p.pos.x - viewW / 2.0, Level.WIDTH, viewW);
        double targetY = clampCam(p.pos.y - viewH / 2.0, Level.HEIGHT, viewH);

        if (!camInit) {              // snap on the first frame, then ease
            camX = targetX;
            camY = targetY;
            camInit = true;
            return;
        }
        double k = Math.min(1.0, 10.0 * dt); // follow stiffness
        camX += (targetX - camX) * k;
        camY += (targetY - camY) * k;
    }

    /** Clamp a camera edge so the view stays inside the world (centering if smaller). */
    private static double clampCam(double v, int worldSize, int viewSize) {
        if (worldSize <= viewSize) {
            return (worldSize - viewSize) / 2.0; // world narrower than view: center it
        }
        return Math.max(0, Math.min(worldSize - viewSize, v));
    }

    // ------------------------------------------------------------------
    // Rendering — pale "white palace" pixel art.
    //
    // The world is drawn into a small canvas where one canvas pixel is one
    // art pixel (PX screen pixels wide), with antialiasing off, then blown up
    // with nearest-neighbour scaling so every edge is a crisp, chunky pixel.
    // All world drawing below works in grid units (world px / PX).
    // ------------------------------------------------------------------

    /** Screen pixels per art pixel. */
    private static final int PX = 4;

    // Palette: ivory marble, cool lavender-grey shadow, a touch of pale gold,
    // and the black void as the only deep tone.
    private static final Color OUTLINE      = new Color(86, 82, 106);
    private static final Color MARBLE_HI    = new Color(253, 252, 249);
    private static final Color MARBLE       = new Color(238, 236, 238);
    private static final Color MARBLE_MID   = new Color(218, 216, 226);
    private static final Color MARBLE_SHADE = new Color(190, 188, 206);
    private static final Color GOLD         = new Color(228, 206, 142);
    private static final Color GOLD_DARK    = new Color(178, 150, 96);
    private static final Color VOID         = new Color(16, 14, 24);
    private static final Color VOID_EDGE    = new Color(44, 40, 60);
    private static final Color SKY_TOP      = new Color(246, 245, 250);
    private static final Color SKY_BOTTOM   = new Color(204, 202, 220);
    private static final Color SPIRE_FAR    = new Color(226, 224, 236);
    private static final Color SPIRE_NEAR   = new Color(212, 210, 226);
    private static final Color SPIRE_NEAR_HI = new Color(234, 233, 242);

    // 4x4 ordered-dither thresholds (0..15) for the stepped sky gradient.
    private static final int[][] BAYER = {
        {0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}
    };

    // Blocks are static, so each is rendered once into a grid-sized sprite.
    // BLOCK_PAD (grid px) leaves room for trim that pokes past the box.
    private static final int BLOCK_PAD = 4;

    private BufferedImage canvas;   // low-res world canvas
    private BufferedImage skyImage; // cached dithered gradient (per canvas size)

    /** World px -> grid px (floor, so adjacent shapes tile without gaps). */
    private static int gx(double world) {
        return (int) Math.floor(world / PX);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int viewW = getWidth() > 0 ? getWidth() : VIEW_WIDTH;
        int viewH = getHeight() > 0 ? getHeight() : VIEW_HEIGHT;
        int lw = (viewW + PX - 1) / PX, lh = (viewH + PX - 1) / PX;
        if (canvas == null || canvas.getWidth() != lw || canvas.getHeight() != lh) {
            canvas = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_RGB);
            skyImage = null;
        }

        // Camera snapped to the art-pixel grid so nothing shimmers as it scrolls.
        int camGX = (int) Math.round(camX / PX);
        int camGY = (int) Math.round(camY / PX);

        Graphics2D c = canvas.createGraphics();
        c.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        c.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        drawBackdrop(c, lw, lh, camGX, camGY);

        c.translate(-camGX, -camGY);
        // World-space view rectangle for culling.
        double vl = camGX * PX - CULL_MARGIN, vt = camGY * PX - CULL_MARGIN;
        double vr = camGX * PX + viewW + CULL_MARGIN, vb = camGY * PX + viewH + CULL_MARGIN;

        for (Waterfall f : world.waterfalls) {
            if (f.overlaps(vl, vt, vr, vb)) drawWaterfall(c, f);
        }
        for (Water w : world.waters) {
            if (w.overlaps(vl, vt, vr, vb)) drawWater(c, w);
        }
        for (Block b : world.blocks) {
            if (b.overlaps(vl, vt, vr, vb)) {
                c.drawImage(blockSprite(b), gx(b.x) - BLOCK_PAD, gx(b.y) - BLOCK_PAD, null);
            }
        }
        for (Hazard h : world.hazards) {
            if (h.overlaps(vl, vt, vr, vb)) drawHazard(c, h);
        }
        for (Platform pf : world.platforms) {
            if (pf.right() < vl || pf.left() > vr || pf.top() < vt || pf.top() > vb) continue;
            drawPlatform(c, pf);
        }
        for (Urchin u : world.urchins) {
            double o = u.outerRadius();
            if (u.x + o < vl || u.x - o > vr || u.y + o < vt || u.y - o > vb) continue;
            drawUrchin(c, u);
        }
        for (Finish gf : world.finishes) {
            if (gf.overlaps(vl, vt, vr, vb)) drawFinish(c, gf);
        }

        Player p = world.player;
        drawGhosts(c, p);
        if (!world.dead) {
            drawPlayer(c, p);
        } else {
            drawDeathBurst(c);
        }
        drawSplash(c);
        c.dispose();

        // Blow the canvas up to the window with hard nearest-neighbour pixels.
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.drawImage(canvas, 0, 0, lw * PX, lh * PX, null);

        // HUD and overlays (screen space, but still drawn on the pixel grid).
        if (world.finished) {
            drawVictoryOverlay(g2, viewW, viewH);
        } else if (world.dead && world.killedByEnemy) {
            drawDiedOverlay(g2, viewW, viewH);
        } else if (world.dead) {
            // Sink into the void toward the moment of respawn.
            int a = (int) Math.min(210, (world.deathElapsed / World.DEATH_DURATION) * 230);
            g2.setColor(withAlpha(VOID, a));
            g2.fillRect(0, 0, viewW, viewH);
            pixelText(g2, "Lost to the depths…", viewW / 2, viewH / 2, 12, 3,
                    withAlpha(MARBLE_HI, Math.min(255, a + 40)), null, true);
        } else {
            if (respawnFade > 0) {
                int a = (int) ((respawnFade / RESPAWN_FADE) * 190);
                g2.setColor(withAlpha(VOID, a));
                g2.fillRect(0, 0, viewW, viewH);
            }
            String medium = switch (p.medium()) {
                case WATERFALL -> "WATERFALL (climb)";
                case WATER -> "WATER (fast)";
                case LAND -> "LAND (slow)";
                case AIR -> "AIR (medium)";
            };
            Color hud = new Color(104, 100, 128);
            pixelText(g2, "Move: A/D or ←/→   Jump/Swim/Climb up: Space/W/↑   Climb down: S/↓   Dash: F",
                    12, 22, 9, 2, hud, MARBLE_HI, false);
            String dash = p.dashing() ? "DASHING"
                    : p.dashReady && p.dashCooldown == 0 ? "ready" : "spent";
            pixelText(g2, "Medium: " + medium + "   speed=" + (int) world.activeMoveSpeed()
                    + "   dash: " + dash,
                    12, 44, 9, 2, hud, MARBLE_HI, false);
        }
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    private static Color lerp(Color a, Color b, double t) {
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Cheap stable integer hash for procedural layout. */
    private static int hash(int i, int seed) {
        int h = i * 0x27d4eb2d ^ seed * 0x165667b1;
        h ^= h >>> 15;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        return h & 0x7fffffff;
    }

    /** Fill a filled pixel disc centred at (cx, cy) in grid units. */
    private static void disc(Graphics2D g, double cx, double cy, double r) {
        blob(g, cx, cy, r, r);
    }

    /** Fill a filled pixel ellipse with radii (rx, ry) centred at (cx, cy). */
    private static void blob(Graphics2D g, double cx, double cy, double rx, double ry) {
        if (rx <= 0 || ry <= 0) return;
        int x0 = (int) Math.floor(cx - rx), x1 = (int) Math.ceil(cx + rx);
        int y0 = (int) Math.floor(cy - ry), y1 = (int) Math.ceil(cy + ry);
        for (int y = y0; y <= y1; y++) {
            int run = -1;
            for (int x = x0; x <= x1 + 1; x++) {
                double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                boolean in = x <= x1 && dx * dx + dy * dy <= 1.0;
                if (in && run < 0) run = x;
                if (!in && run >= 0) {
                    g.fillRect(run, y, x - run, 1);
                    run = -1;
                }
            }
        }
    }

    /**
     * Text rendered small without antialiasing, then scaled up by {@code scale}
     * so it reads as pixel lettering. (x, baseline) are screen coordinates; x is
     * the centre when {@code centered}, else the left edge.
     */
    private void pixelText(Graphics2D g, String s, int x, int baseline, int size, int scale,
                           Color color, Color shadow, boolean centered) {
        Font f = new Font(Font.SANS_SERIF, Font.BOLD, size);
        FontMetrics fm = getFontMetrics(f);
        int w = fm.stringWidth(s) + 2, h = fm.getAscent() + fm.getDescent() + 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig = img.createGraphics();
        ig.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        ig.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        ig.setFont(f);
        if (shadow != null) {
            ig.setColor(shadow);
            ig.drawString(s, 1, fm.getAscent() + 1);
        }
        ig.setColor(color);
        ig.drawString(s, 0, fm.getAscent());
        ig.dispose();
        int left = centered ? x - (w * scale) / 2 : x;
        g.drawImage(img, left, baseline - fm.getAscent() * scale, w * scale, h * scale, null);
    }

    // ---------------------------------------------------------------- backdrop

    /** Pale palace sky: dithered gradient, parallax spires, light shafts, motes. */
    private void drawBackdrop(Graphics2D g, int lw, int lh, int camGX, int camGY) {
        if (skyImage == null) {
            skyImage = renderSky(lw, lh);
        }
        g.drawImage(skyImage, 0, 0, null);

        // Soft light shafts slanting down from above.
        double t = frame * 0.006;
        for (int i = 0; i < 4; i++) {
            int xTop = (int) (i * lw / 3.5 + Math.sin(t + i * 1.7) * 10) - camGX / 20;
            Polygon ray = new Polygon();
            ray.addPoint(xTop, 0);
            ray.addPoint(xTop + 14, 0);
            ray.addPoint(xTop + 14 + 50, lh);
            ray.addPoint(xTop + 50, lh);
            g.setColor(new Color(255, 255, 255, 34));
            g.fillPolygon(ray);
        }

        // Two layers of distant palace spires; the nearer one scrolls faster.
        drawSpires(g, lw, lh, camGX * 0.12, camGY * 0.06, 11, 17, 30, 95, SPIRE_FAR, null, false);
        // Low mist between the layers.
        g.setColor(new Color(255, 255, 255, 60));
        g.fillRect(0, lh - 34, lw, 34);
        drawSpires(g, lw, lh, camGX * 0.28, camGY * 0.14, 23, 34, 20, 70,
                SPIRE_NEAR, SPIRE_NEAR_HI, true);
        g.setColor(new Color(255, 255, 255, 70));
        g.fillRect(0, lh - 14, lw, 14);

        // Drifting motes of light, rising slowly with a gentle sway.
        for (int i = 0; i < 46; i++) {
            int h1 = hash(i, 7);
            int span = lh + 20;
            double speed = 4 + (h1 % 9);
            double ph = ((h1 >>> 4) % span + frame * speed * 0.02) % span;
            int my = (int) (lh + 10 - ph);
            int mx = (int) (((h1 >>> 9) % (lw + 40)) - 20
                    + Math.sin(frame * 0.02 + i) * 3 - camGX * 0.2 % (lw + 40));
            mx = Math.floorMod(mx, lw + 40) - 20;
            boolean big = h1 % 5 == 0;
            int a = 120 + (int) (80 * Math.sin(frame * 0.05 + i * 2.3));
            g.setColor(new Color(255, 255, 255, Math.max(30, Math.min(255, a))));
            if (big) { // little twinkle cross
                g.fillRect(mx - 1, my, 3, 1);
                g.fillRect(mx, my - 1, 1, 3);
            } else {
                g.fillRect(mx, my, 1, 1);
            }
        }
    }

    /** Stepped, ordered-dithered vertical gradient from SKY_TOP to SKY_BOTTOM. */
    private static BufferedImage renderSky(int lw, int lh) {
        BufferedImage img = new BufferedImage(lw, lh, BufferedImage.TYPE_INT_RGB);
        final int bands = 7;
        int[] band = new int[bands + 1];
        for (int i = 0; i <= bands; i++) {
            band[i] = lerp(SKY_TOP, SKY_BOTTOM, i / (double) bands).getRGB();
        }
        for (int y = 0; y < lh; y++) {
            double level = y / (double) Math.max(1, lh - 1) * bands;
            int b = Math.min(bands - 1, (int) level);
            double frac = level - b;
            for (int x = 0; x < lw; x++) {
                boolean next = BAYER[y & 3][x & 3] < frac * 16;
                img.setRGB(x, y, next ? band[b + 1] : band[b]);
            }
        }
        return img;
    }

    /** A repeating skyline of slender towers with pointed roofs. */
    private static void drawSpires(Graphics2D g, int lw, int lh, double offX, double offY,
                                   int seed, int spacing, int minH, int maxH,
                                   Color body, Color hi, boolean detailed) {
        int base = lh + 24 - (int) offY;
        int first = (int) Math.floor(offX / spacing) - 2;
        int last = first + lw / spacing + 4;
        for (int i = first; i <= last; i++) {
            int h = hash(i, seed);
            int x = (int) Math.round(i * spacing - offX) + (h % 7) - 3;
            int w = 4 + (h >>> 3) % 6;
            int height = minH + (h >>> 7) % (maxH - minH);
            int top = base - height;
            g.setColor(body);
            g.fillRect(x, top, w, base - top);
            // Pointed roof stepping in one pixel per row (two for wider towers).
            int roof = w / 2 + (h >>> 11) % 6 + 2;
            for (int k = 0; k < roof; k++) {
                int rw = Math.max(1, w - 2 * (int) Math.round(k * (w / 2.0) / roof));
                g.fillRect(x + (w - rw) / 2, top - k - 1, rw, 1);
            }
            g.fillRect(x + w / 2, top - roof - 3, 1, 3); // needle tip
            if (detailed) {
                g.setColor(hi);
                g.fillRect(x, top, 1, base - top);           // lit edge
                g.fillRect(x - 1, top + 6, w + 2, 1);        // cornice band
                g.fillRect(x - 1, top + 18, w + 2, 1);
                if (w >= 6) {                                 // tall arched windows
                    g.setColor(new Color(200, 198, 216));
                    for (int wy = top + 9; wy < base - 8; wy += 13) {
                        g.fillRect(x + w / 2 - 1, wy + 1, 2, 6);
                        g.fillRect(x + w / 2, wy, 1, 1);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- terrain

    /** Pale luminous pool with a rippling bright surface and drifting glints. */
    private void drawWater(Graphics2D g, Water w) {
        int x0 = gx(w.left()), x1 = gx(w.right()), y0 = gx(w.top()), y1 = gx(w.bottom());
        int ww = x1 - x0, hh = y1 - y0;
        g.setColor(new Color(170, 214, 234, 120));
        g.fillRect(x0, y0, ww, hh);
        // Slightly denser water toward the bottom.
        g.setColor(new Color(150, 196, 226, 50));
        g.fillRect(x0, y0 + hh / 2, ww, hh - hh / 2);
        // Faint glassy sides.
        g.setColor(new Color(240, 250, 255, 110));
        g.fillRect(x0, y0, 1, hh);
        g.fillRect(x1 - 1, y0, 1, hh);

        // Drifting glints inside the pool.
        for (int i = 0; i < Math.max(4, ww * hh / 500); i++) {
            int h = hash(i, x0 * 31 + y0);
            int gy = y0 + 4 + (h % Math.max(1, hh - 6));
            int gxp = x0 + 2 + (int) (((h >>> 8) + frame * (0.08 + (h % 5) * 0.03)) % Math.max(1, ww - 6));
            int a = 60 + (int) (50 * Math.sin(frame * 0.04 + i));
            g.setColor(new Color(255, 255, 255, Math.max(10, a)));
            g.fillRect(gxp, gy, 3 + h % 3, 1);
        }

        // Surface: bright line with little travelling wave crests.
        g.setColor(new Color(226, 244, 252, 200));
        g.fillRect(x0, y0 + 1, ww, 1);
        g.setColor(new Color(255, 255, 255, 240));
        for (int x = x0; x < x1; x++) {
            double wv = Math.sin((x + frame * 0.12) * 0.45) + Math.sin((x - frame * 0.07) * 0.21);
            g.fillRect(x, wv > 1.1 ? y0 - 1 : y0, 1, 1);
        }
    }

    /** A pale falling column with streaks marching downward. */
    private void drawWaterfall(Graphics2D g, Waterfall f) {
        int x0 = gx(f.left()), x1 = gx(f.right()), y0 = gx(f.top()), y1 = gx(f.bottom());
        int ww = x1 - x0, hh = y1 - y0;
        g.setColor(new Color(182, 222, 240, 130));
        g.fillRect(x0, y0, ww, hh);
        g.setColor(new Color(245, 252, 255, 140));
        g.fillRect(x0, y0, 1, hh);
        g.fillRect(x1 - 1, y0, 1, hh);

        int lanes = Math.max(3, ww / 3);
        for (int i = 0; i < lanes; i++) {
            int h = hash(i, x0 * 17 + y0);
            int lx = x0 + 1 + h % Math.max(1, ww - 2);
            int period = 10 + (h >>> 5) % 6;
            int len = 4 + (h >>> 9) % 4;
            int scroll = (int) ((frame * (1.2 + (h % 3) * 0.4) + (h >>> 12)) % period);
            g.setColor(new Color(255, 255, 255, 120 + (h >>> 3) % 110));
            for (int sy = y0 - period + scroll; sy < y1; sy += period) {
                int a = Math.max(y0, sy), b = Math.min(y1, sy + len);
                if (b > a) g.fillRect(lx, a, 1, b - a);
            }
        }
        // Foam where it pours over the top.
        g.setColor(new Color(255, 255, 255, 230));
        g.fillRect(x0, y0, ww, 1);
        for (int x = x0; x < x1; x += 2) {
            if (((x + frame / 6) & 3) == 0) g.fillRect(x, y0 + 1, 1, 1);
        }
    }

    /** One-way ledge: a thin marble beam with gold end caps. */
    private void drawPlatform(Graphics2D g, Platform pf) {
        int x0 = gx(pf.left()), x1 = gx(pf.right()), y = gx(pf.top());
        int w = x1 - x0;
        g.setColor(OUTLINE);
        g.fillRect(x0, y - 1, w, 1);
        g.fillRect(x0, y + 2, w, 1);
        g.setColor(MARBLE_HI);
        g.fillRect(x0, y, w, 1);
        g.setColor(MARBLE_SHADE);
        g.fillRect(x0, y + 1, w, 1);
        // Dotted gold inlay along the underside.
        g.setColor(GOLD);
        for (int x = x0 + 3; x < x1 - 2; x += 6) {
            g.fillRect(x, y + 1, 1, 1);
        }
        // End caps.
        g.setColor(OUTLINE);
        g.fillRect(x0 - 1, y - 1, 1, 4);
        g.fillRect(x1, y - 1, 1, 4);
        g.setColor(GOLD_DARK);
        g.fillRect(x0, y, 1, 2);
        g.fillRect(x1 - 1, y, 1, 2);
    }

    /** The abyss: the black void, wisping at its rim and lined with pale thorns. */
    private void drawHazard(Graphics2D g, Hazard h) {
        int x0 = gx(h.left()), x1 = gx(h.right()), y0 = gx(h.top()), y1 = gx(h.bottom());
        g.setColor(VOID);
        g.fillRect(x0, y0, x1 - x0, y1 - y0);

        // Wavering void tendrils licking up from the rim.
        for (int x = x0; x < x1; x++) {
            double wv = Math.sin(x * 0.37 + frame * 0.05) + Math.sin(x * 0.11 - frame * 0.03) * 1.4;
            int up = (int) Math.max(0, Math.round(wv + 1));
            if (up > 0) {
                g.setColor(VOID);
                g.fillRect(x, y0 - up, 1, up);
            }
            if (((x * 7 + frame / 8) % 29) == 0) {
                g.setColor(VOID_EDGE);
                g.fillRect(x, y0 - up - 2, 1, 1); // stray drifting fleck
            }
        }

        // Pale thorns jutting up from the void.
        for (int sx = x0 + 1; sx < x1 - 5; sx += 6) {
            int hgt = 4 + hash(sx, 3) % 4;
            int lean = (hash(sx, 5) % 3) - 1;
            for (int k = 0; k < hgt; k++) {
                int half = (hgt - k) * 2 / hgt; // 2 -> 0 toward the tip
                int cx = sx + 2 + (k * lean) / hgt;
                int y = y0 - k;
                g.setColor(OUTLINE);
                g.fillRect(cx - half - 1, y, half * 2 + 3, 1);
                g.setColor(k > hgt / 2 ? MARBLE_HI : MARBLE_MID);
                g.fillRect(cx - half, y, half * 2 + 1, 1);
                g.setColor(MARBLE_SHADE);
                if (half > 0) g.fillRect(cx + half, y, 1, 1);
            }
            g.setColor(OUTLINE);
            g.fillRect(sx + 2 + lean, y0 - hgt, 1, 1);
        }
    }

    /** The cached sprite for a block, rendering it on first use. */
    private BufferedImage blockSprite(Block b) {
        return blockSprites.computeIfAbsent(b, GamePanel::renderBlockSprite);
    }

    /**
     * Render a block once into a grid-resolution sprite. The box's top-left is
     * at (BLOCK_PAD, BLOCK_PAD) in the image, so draw it at
     * (gx(b.x) - BLOCK_PAD, gx(b.y) - BLOCK_PAD).
     */
    private static BufferedImage renderBlockSprite(Block b) {
        int w = gx(b.right()) - gx(b.left()), h = gx(b.bottom()) - gx(b.top());
        BufferedImage img = new BufferedImage(w + 2 * BLOCK_PAD, h + 2 * BLOCK_PAD,
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.translate(BLOCK_PAD, BLOCK_PAD);
        int seed = (int) b.x * 31 + (int) b.y;
        boolean ornate = b.type == Block.Type.GRASS;
        int bodyTop = ornate ? 5 : 1;

        // Marble body laid in offset courses of bricks.
        g.setColor(MARBLE);
        g.fillRect(0, 0, w, h);
        int row = 0;
        for (int y = bodyTop; y < h - 1; y += 4, row++) {
            int off = (row & 1) * 5;
            for (int x = -off; x < w; x += 10) {
                int bx = Math.max(0, x), bw = Math.min(w, x + 10) - bx;
                int hsh = hash(x * 13 + row, seed);
                g.setColor(hsh % 4 == 0 ? MARBLE_HI : MARBLE);
                g.fillRect(bx, y, bw, Math.min(3, h - 1 - y));
                g.setColor(MARBLE_HI);
                g.fillRect(bx, y, Math.max(0, bw - 1), 1);               // lit top edge
                g.setColor(MARBLE_MID);
                if (x + 9 < w) g.fillRect(x + 9, y, 1, Math.min(3, h - 1 - y)); // joint
                if (hsh % 7 == 0 && bw > 4) {                           // hairline vein
                    g.fillRect(bx + 2 + hsh % 3, y + 1, 2, 1);
                }
            }
            g.setColor(MARBLE_MID);
            if (y + 3 < h - 1) g.fillRect(0, y + 3, w, 1);               // mortar course
        }
        // Shaded underside and right side.
        g.setColor(MARBLE_SHADE);
        g.fillRect(0, h - 2, w, 1);
        g.fillRect(w - 2, bodyTop, 1, h - bodyTop - 1);

        if (ornate) {
            // Polished capstone overhanging the body by a pixel each side.
            g.setColor(MARBLE_HI);
            g.fillRect(-1, 0, w + 2, 2);
            g.setColor(MARBLE_MID);
            g.fillRect(-1, 2, w + 2, 1);
            // Gold inlay line and dentil frieze under the cap.
            g.setColor(GOLD);
            g.fillRect(0, 3, w, 1);
            g.setColor(GOLD_DARK);
            for (int x = 2; x < w; x += 4) g.fillRect(x, 3, 1, 1);
            g.setColor(MARBLE_SHADE);
            for (int x = 0; x < w; x += 4) g.fillRect(x, 4, 2, 1);
            g.setColor(OUTLINE);
            g.fillRect(-2, 0, 1, 3);
            g.fillRect(w + 1, 0, 1, 3);
            g.fillRect(-1, 3, 1, 1);
            g.fillRect(w, 3, 1, 1);
        }
        // Crisp dark outline.
        g.setColor(OUTLINE);
        g.fillRect(ornate ? -1 : 0, -1, ornate ? w + 2 : w, 1);
        g.fillRect(0, h - 1, w, 1);
        g.fillRect(0, ornate ? 3 : 0, 1, h - (ornate ? 3 : 0));
        g.fillRect(w - 1, ornate ? 3 : 0, 1, h - (ornate ? 3 : 0));

        if (ornate) {
            // Little finials along the top and drop pendants hanging beneath.
            for (int x = 6; x < w - 4; x += 16) {
                g.setColor(OUTLINE);
                g.fillRect(x - 1, -2, 3, 1);
                g.fillRect(x, -3, 1, 1);
                g.setColor(MARBLE_HI);
                g.fillRect(x, -2, 1, 1);
            }
            for (int x = 4; x < w - 4; x += 12) {
                g.setColor(OUTLINE);
                g.fillRect(x - 1, h, 4, 1);
                g.fillRect(x, h + 1, 2, 1);
                g.setColor(MARBLE_MID);
                g.fillRect(x, h, 2, 1);
                g.setColor(GOLD);
                g.fillRect(x, h + 2, 2, 1);
            }
        }
        g.dispose();
        return img;
    }

    // ---------------------------------------------------------------- actors

    /** Dash afterimages: void-dark copies of the orb fading out behind it. */
    private void drawGhosts(Graphics2D g, Player p) {
        double r = p.radius / PX;
        for (double[] gh : ghosts) {
            double t = gh[2] / GHOST_LIFE; // 1 fresh -> 0 gone
            g.setColor(new Color(30, 26, 44, (int) (150 * t)));
            blob(g, gh[0] / PX, gh[1] / PX, r * (0.6 + 0.4 * t), r * (0.45 + 0.4 * t));
        }
    }

    /** The player: a small dark orb with a pale rim light — ink on marble. */
    private void drawPlayer(Graphics2D g, Player p) {
        double cx = p.pos.x / PX, cy = p.pos.y / PX, r = p.radius / PX;
        if (p.dashing()) {
            // Stretched along the dash, with a pale leading edge.
            double rx = r + 1.5, ry = r - 1;
            g.setColor(new Color(20, 18, 30));
            blob(g, cx - p.facing * 0.5, cy, rx, ry);
            g.setColor(new Color(46, 42, 64));
            blob(g, cx - p.facing * 0.5, cy, rx - 1, ry - 1);
            g.setColor(new Color(236, 236, 248));
            int lead = (int) Math.floor(cx - p.facing * 0.5 + p.facing * (rx - 2));
            g.fillRect(lead, (int) Math.floor(cy) - 1, 1, 2);
            return;
        }
        g.setColor(new Color(20, 18, 30));
        disc(g, cx, cy, r);
        g.setColor(new Color(46, 42, 64));
        disc(g, cx, cy, r - 1);
        g.setColor(new Color(64, 60, 88));
        disc(g, cx - 0.8, cy - 0.8, r - 2.2);
        // Pale rim light along the lower right (reflected marble).
        g.setColor(new Color(200, 198, 220));
        int ix = (int) Math.floor(cx), iy = (int) Math.floor(cy), ir = (int) Math.round(r);
        g.fillRect(ix + ir - 2, iy + 1, 1, 2);
        g.fillRect(ix + 1, iy + ir - 2, 2, 1);
        // Specular highlight.
        g.setColor(new Color(244, 244, 252));
        g.fillRect(ix - ir / 2 - 1, iy - ir / 2 - 1, 2, 1);
        g.fillRect(ix - ir / 2 - 1, iy - ir / 2, 1, 1);
    }

    /** Splash droplets as tiny pale pixels, stretched when falling fast. */
    private void drawSplash(Graphics2D g) {
        for (Droplet d : droplets) {
            if (d.smoke) {
                int a = (int) (200 * Math.min(1.0, d.life / d.maxLife * 1.5));
                int s = (int) d.size;
                g.setColor(new Color(26, 22, 38, a));
                g.fillRect(gx(d.x), gx(d.y), s, s);
                continue;
            }
            int a = (int) (240 * Math.min(1.0, d.life / d.maxLife * 2));
            int s = d.size > 4.5 ? 2 : 1;
            int sy = s + (Math.abs(d.vy) > 500 ? 1 : 0);
            int x = gx(d.x), y = gx(d.y);
            g.setColor(new Color(196, 230, 246, a));
            g.fillRect(x, y, s, sy);
            g.setColor(new Color(255, 255, 255, a));
            g.fillRect(x, y, 1, 1);
        }
    }

    /** Death effect: a dotted expanding ring and pale shards bursting outward. */
    private void drawDeathBurst(Graphics2D g) {
        double e = world.deathElapsed;
        double cx = world.deathX / PX, cy = world.deathY / PX;

        double rad = e * 260 / PX;
        int ringA = (int) Math.max(0, 230 - e * 240);
        if (ringA > 0 && rad > 0.5) {
            g.setColor(new Color(255, 255, 255, ringA));
            int steps = Math.max(12, (int) (rad * 6));
            for (int i = 0; i < steps; i++) {
                if ((i / 2) % 2 == 1) continue; // dashed
                double ang = i * Math.PI * 2 / steps;
                g.fillRect((int) Math.floor(cx + Math.cos(ang) * rad),
                        (int) Math.floor(cy + Math.sin(ang) * rad), 1, 1);
            }
        }
        Random r = new Random(99);
        for (int i = 0; i < 26; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = (40 + r.nextDouble() * 160) * e / PX;
            int bx = (int) Math.floor(cx + Math.cos(ang) * dist);
            int by = (int) Math.floor(cy + Math.sin(ang) * dist - e * 60 / PX);
            int ba = (int) Math.max(0, 230 - e * 170);
            int s = r.nextInt(3) == 0 ? 2 : 1;
            if (ba <= 0) continue;
            g.setColor(new Color(i % 3 == 0 ? 46 : 255, i % 3 == 0 ? 42 : 255,
                    i % 3 == 0 ? 64 : 255, ba));
            g.fillRect(bx, by, s, s);
        }
    }

    /** A thorned sentinel orb: void core, pale-tipped spines, one cold eye-slit. */
    private void drawUrchin(Graphics2D g, Urchin u) {
        double cx = u.x / PX, cy = u.y / PX, br = u.bodyRadius / PX;
        int spines = 16;
        double sway = Math.sin(frame * 0.04) * 0.08;
        for (int i = 0; i < spines; i++) {
            double ang = i * Math.PI * 2 / spines + sway;
            double len = u.spineLength / PX * (i % 2 == 0 ? 1.0 : 0.65)
                    * (0.9 + 0.1 * Math.sin(frame * 0.08 + i));
            int x1 = (int) Math.floor(cx + Math.cos(ang) * br);
            int y1 = (int) Math.floor(cy + Math.sin(ang) * br);
            int x2 = (int) Math.floor(cx + Math.cos(ang) * (br + len));
            int y2 = (int) Math.floor(cy + Math.sin(ang) * (br + len));
            g.setColor(OUTLINE);
            g.drawLine(x1, y1, x2, y2);
            g.setColor(MARBLE_HI);
            g.fillRect(x2, y2, 1, 1);
        }
        g.setColor(OUTLINE);
        disc(g, cx, cy, br + 0.6);
        g.setColor(VOID);
        disc(g, cx, cy, br - 0.4);
        g.setColor(VOID_EDGE);
        disc(g, cx - 0.7, cy - 0.7, br - 1.6);
        // A pale ring of plating and a narrow glowing eye.
        int ix = (int) Math.floor(cx), iy = (int) Math.floor(cy);
        g.setColor(MARBLE_MID);
        g.fillRect(ix - 2, iy - (int) br, 4, 1);
        boolean blink = (frame % 180) < 6;
        g.setColor(new Color(255, 250, 236));
        g.fillRect(ix - 1, iy, 3, blink ? 0 : 1);
        g.setColor(GOLD);
        g.fillRect(ix - 2, iy, 1, blink ? 0 : 1);
        g.fillRect(ix + 2, iy, 1, blink ? 0 : 1);
    }

    /** The finish: a slender white pillar flying an ivory pennant; gilds on win. */
    private void drawFinish(Graphics2D g, Finish f) {
        int poleX = gx(f.left()) + 5;
        int baseY = gx(f.bottom() - Level.FINISH_SINK);
        int topY = gx(f.top()) + 3;
        boolean won = world.finished;

        // Pedestal.
        g.setColor(OUTLINE);
        g.fillRect(poleX - 3, baseY - 3, 8, 3);
        g.setColor(MARBLE_HI);
        g.fillRect(poleX - 2, baseY - 3, 6, 1);
        g.setColor(MARBLE_MID);
        g.fillRect(poleX - 2, baseY - 2, 6, 1);
        // Pole.
        g.setColor(OUTLINE);
        g.fillRect(poleX - 1, topY, 4, baseY - 3 - topY);
        g.setColor(MARBLE_HI);
        g.fillRect(poleX, topY, 1, baseY - 3 - topY);
        g.setColor(MARBLE_SHADE);
        g.fillRect(poleX + 1, topY, 1, baseY - 3 - topY);
        for (int y = topY + 4; y < baseY - 4; y += 5) { // gold bands
            g.setColor(GOLD);
            g.fillRect(poleX, y, 2, 1);
        }
        // Finial orb, glowing when won.
        if (won) {
            int a = 90 + (int) (60 * Math.sin(frame * 0.15));
            g.setColor(new Color(255, 236, 170, a));
            disc(g, poleX + 1, topY - 2, 4.5);
        }
        g.setColor(OUTLINE);
        disc(g, poleX + 1, topY - 2, 2.2);
        g.setColor(won ? new Color(255, 240, 180) : GOLD);
        disc(g, poleX + 1, topY - 2, 1.3);

        // Swallow-tailed pennant, each row rippling a pixel with the breeze.
        int fw = 13, fh = 9, fy = topY + 1;
        Color cloth = won ? GOLD : MARBLE_HI, trim = won ? GOLD_DARK : GOLD;
        for (int k = 0; k < fh; k++) {
            int wave = (int) Math.round(Math.sin(frame * 0.12 - k * 0.6));
            int notch = Math.max(0, 3 - Math.abs(k - fh / 2) * 3 / (fh / 2)); // swallowtail cut
            int len = fw - notch + wave;
            int x = poleX + 3;
            boolean edge = k == 0 || k == fh - 1;
            g.setColor(OUTLINE);
            g.fillRect(x, fy + k, len + 1, 1);
            if (!edge) {
                g.setColor(k == 1 || k == 2 ? trim : cloth);
                g.fillRect(x, fy + k, len, 1);
            }
            if (!edge && k > fh / 2) {
                g.setColor(won ? GOLD_DARK : MARBLE_MID); // cloth shading
                g.fillRect(x + len - 3, fy + k, 2, 1);
            }
        }
        g.setColor(OUTLINE);
        g.fillRect(poleX + 3, fy - 1, fw - 2, 1);
        g.fillRect(poleX + 3, fy + fh, fw - 2, 1);
    }

    // ---------------------------------------------------------------- overlays

    /** "LEVEL COMPLETE!" — a soft white wash with ink-and-gold lettering. */
    private void drawVictoryOverlay(Graphics2D g2, int viewW, int viewH) {
        double appear = Math.min(1.0, world.finishElapsed / 0.4);
        g2.setColor(new Color(250, 249, 253, (int) (appear * 175)));
        g2.fillRect(0, 0, viewW, viewH);

        int ty = viewH / 2;
        int size = 18, scale = Math.max(3, viewW / 240);
        pixelText(g2, "LEVEL COMPLETE!", viewW / 2, ty, size, scale,
                withAlpha(new Color(40, 36, 58), (int) (appear * 255)),
                withAlpha(GOLD, (int) (appear * 255)), true);

        double btnAppear = Math.max(0, Math.min(1.0, (world.finishElapsed - 0.3) / 0.3));
        if (btnAppear <= 0) {
            return;
        }
        drawOverlayButton(g2, "Play Again", viewW, viewH, ty + 40, btnAppear,
                MARBLE_HI, GOLD, new Color(40, 36, 58), OUTLINE, new Color(110, 104, 136));
    }

    /** "YOU DIED" — the void closes in; pale lettering and a Retry button. */
    private void drawDiedOverlay(Graphics2D g2, int viewW, int viewH) {
        double e = world.deathElapsed;
        double appear = Math.min(1.0, e / 0.5);
        g2.setColor(withAlpha(VOID, (int) (appear * 210)));
        g2.fillRect(0, 0, viewW, viewH);

        int ty = viewH / 2;
        int scale = Math.max(4, viewW / 200);
        pixelText(g2, "YOU DIED", viewW / 2, ty, 18, scale,
                withAlpha(MARBLE_HI, (int) (appear * 255)),
                withAlpha(new Color(120, 60, 80), (int) (appear * 255)), true);
        pixelText(g2, "Pricked by a sea urchin", viewW / 2, ty + 36, 10, 2,
                withAlpha(MARBLE_SHADE, (int) (appear * 230)), null, true);

        double btnAppear = Math.max(0, Math.min(1.0, (e - DIED_BUTTON_DELAY + 0.3) / 0.3));
        drawOverlayButton(g2, "Retry", viewW, viewH, ty + 64, btnAppear,
                VOID_EDGE, new Color(78, 72, 102), MARBLE_HI, MARBLE_SHADE, MARBLE_SHADE);
    }

    /**
     * A centred pixel-style button with notched corners (sets
     * {@link #overlayBtn}) and a key hint below it.
     */
    private void drawOverlayButton(Graphics2D g2, String label, int viewW, int viewH, int y,
                                   double appear, Color fill, Color hotFill, Color text,
                                   Color border, Color hint) {
        if (appear <= 0) {
            return;
        }
        int bw = Math.max(200, viewW / 6) / PX * PX, bh = Math.max(52, viewH / 13) / PX * PX;
        int bx = (viewW / 2 - bw / 2) / PX * PX;
        overlayBtn.setBounds(bx, y, bw, bh);
        int ba = (int) (appear * 255);
        Color f = btnHover && buttonReady() ? hotFill : fill;

        // Border with chamfered corners, then fill, then a lit top row.
        g2.setColor(withAlpha(border, ba));
        g2.fillRect(bx + PX, y, bw - 2 * PX, bh);
        g2.fillRect(bx, y + PX, bw, bh - 2 * PX);
        g2.setColor(withAlpha(f, ba));
        g2.fillRect(bx + PX, y + PX, bw - 2 * PX, bh - 2 * PX);
        g2.setColor(withAlpha(new Color(255, 255, 255), ba / 3));
        g2.fillRect(bx + 2 * PX, y + PX, bw - 4 * PX, PX);
        g2.setColor(withAlpha(GOLD, ba));
        g2.fillRect(bx + PX, y + bh - 2 * PX, bw - 2 * PX, PX / 2);

        FontMetrics lfm = getFontMetrics(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        int th = (lfm.getAscent() + lfm.getDescent()) * 2;
        pixelText(g2, label, viewW / 2, y + (bh - th) / 2 + lfm.getAscent() * 2, 12, 2,
                withAlpha(text, ba), null, true);
        pixelText(g2, "Click or press Enter / R", viewW / 2, y + bh + 28, 8, 2,
                withAlpha(hint, (int) (appear * 230)), null, true);
    }
}
