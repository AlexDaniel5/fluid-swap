package fluidswap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
    private long frame; // animation counter for the waterfalls

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

    private static final class Droplet {
        double x, y, vx, vy, life, maxLife, size;
    }

    public GamePanel(World world) {
        this.world = world;
        setPreferredSize(new Dimension(VIEW_WIDTH, VIEW_HEIGHT));
        setBackground(new Color(24, 26, 34));
        setFocusable(true);

        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) { setKey(e.getKeyCode(), true); }
            @Override public void keyReleased(KeyEvent e) { setKey(e.getKeyCode(), false); }
        });

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
        }
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
        updateSplash(dt);
        updateCamera(dt);
        frame++;
        repaint();
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
            d.vy += SPLASH_GRAVITY * dt;
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

    /** Draw the flying splash droplets. */
    private void drawSplash(Graphics2D g2) {
        for (Droplet d : droplets) {
            int a = (int) (230 * Math.min(1.0, d.life / d.maxLife * 2));
            int s = (int) Math.round(d.size);
            // Stretch along the direction of travel for a streaky water look.
            int sy = (int) Math.round(d.size * (1 + Math.min(1.5, Math.abs(d.vy) / 600)));
            g2.setColor(new Color(170, 225, 255, a));
            g2.fillOval((int) d.x - s / 2, (int) d.y - sy / 2, s, sy);
            g2.setColor(new Color(240, 252, 255, a));
            g2.fillRect((int) d.x - s / 4, (int) d.y - sy / 3, Math.max(1, s / 3), Math.max(1, s / 3));
        }
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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int viewW = getWidth() > 0 ? getWidth() : VIEW_WIDTH;
        int viewH = getHeight() > 0 ? getHeight() : VIEW_HEIGHT;

        // Immersive underwater backdrop (screen space, behind the world).
        drawUnderwaterBackdrop(g2, viewW, viewH);

        // Shift the world under the camera so the player stays centered.
        int cx = (int) Math.round(camX);
        int cy = (int) Math.round(camY);
        g2.translate(-cx, -cy);

        for (Block b : world.blocks) {
            drawBlock(g2, b);
        }

        // Water regions (drawn translucent, under the player).
        for (Water w : world.waters) {
            g2.setColor(new Color(60, 140, 230, 120));
            g2.fillRect((int) w.x, (int) w.y, (int) w.width, (int) w.height);
            g2.setColor(new Color(120, 190, 255, 160));
            g2.setStroke(new BasicStroke(2f));
            g2.drawLine((int) w.left(), (int) w.top(), (int) w.right(), (int) w.top());
        }

        // Hazards: the deadly abyss trench (dark water + jagged spikes).
        for (Hazard h : world.hazards) {
            drawHazard(g2, h);
        }

        // Waterfalls: translucent column with animated downward streaks.
        for (Waterfall f : world.waterfalls) {
            drawWaterfall(g2, f);
        }

        // One-way platforms: thin black lines (swim up through, land on top).
        g2.setColor(new Color(20, 20, 24));
        g2.setStroke(new BasicStroke(3f));
        for (Platform pf : world.platforms) {
            g2.drawLine((int) pf.left(), (int) pf.top(), (int) pf.right(), (int) pf.top());
        }

        // Finish: a simple flag pole on the dry platform.
        for (Finish gf : world.finishes) {
            drawFinish(g2, gf);
        }

        // Player: circle sprite (hidden mid-death, replaced by a bubble burst).
        Player p = world.player;
        if (!world.dead) {
            int d = (int) (p.radius * 2);
            g2.setColor(new Color(90, 200, 255));
            g2.fillOval((int) (p.pos.x - p.radius), (int) (p.pos.y - p.radius), d, d);
            g2.setColor(new Color(220, 245, 255));
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval((int) (p.pos.x - p.radius), (int) (p.pos.y - p.radius), d, d);
        } else {
            drawDeathBurst(g2);
        }
        drawSplash(g2);

        // HUD and overlays are fixed to the screen: undo the camera.
        g2.translate(cx, cy);
        if (world.finished) {
            drawVictoryOverlay(g2, viewW, viewH);
        } else if (world.dead) {
            // Darken toward the moment of respawn.
            int a = (int) Math.min(200, (world.deathElapsed / World.DEATH_DURATION) * 220);
            g2.setColor(new Color(4, 8, 16, a));
            g2.fillRect(0, 0, viewW, viewH);
            drawCenteredText(g2, "Lost to the depths…", viewW, viewH / 2,
                    Math.max(18, viewW / 36), new Color(150, 210, 240, a + 40));
        } else {
            if (respawnFade > 0) { // fading back in after respawn
                int a = (int) ((respawnFade / RESPAWN_FADE) * 170);
                g2.setColor(new Color(4, 8, 16, a));
                g2.fillRect(0, 0, viewW, viewH);
            }
            String medium = p.onWaterfall ? "WATERFALL (climb)"
                    : p.inWater ? "WATER (fast)"
                    : (p.onGround ? "LAND (slow)" : "AIR (medium)");
            g2.setColor(new Color(150, 160, 180));
            g2.drawString("Move: A/D or ←/→   Jump/Swim/Climb up: Space/W/↑   Climb down: S/↓", 12, 20);
            g2.drawString("Medium: " + medium + "   speed=" + (int) world.activeMoveSpeed(), 12, 38);
        }
    }

    /** Draw a string horizontally centered at the given screen y (baseline). */
    private void drawCenteredText(Graphics2D g2, String s, int viewW, int y, int size, Color c) {
        g2.setFont(new Font("SansSerif", Font.BOLD, size));
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(c);
        g2.drawString(s, viewW / 2 - fm.stringWidth(s) / 2, y);
    }

    /** Translucent blue column with streaks that scroll downward over time. */
    private void drawWaterfall(Graphics2D g2, Waterfall f) {
        int x = (int) f.x, y = (int) f.y, w = (int) f.width, h = (int) f.height;

        g2.setColor(new Color(70, 170, 240, 110));
        g2.fillRect(x, y, w, h);

        // Falling streaks: thin vertical highlights whose phase scrolls down.
        g2.setStroke(new BasicStroke(2f));
        Random r = new Random(((long) x << 16) ^ y); // stable lane layout per fall
        int lanes = Math.max(3, w / 14);
        int scroll = (int) ((frame * 6) % 48); // downward motion, wraps every 48px
        for (int i = 0; i < lanes; i++) {
            int lx = x + 4 + r.nextInt(Math.max(1, w - 8));
            int alpha = 120 + r.nextInt(100);
            g2.setColor(new Color(200, 235, 255, alpha));
            // Dashes marching downward within the column.
            for (int sy = y - 48 + (scroll + r.nextInt(48)) % 48; sy < y + h; sy += 48) {
                int y1 = Math.max(y, sy);
                int y2 = Math.min(y + h, sy + 26);
                if (y2 > y1) {
                    g2.drawLine(lx, y1, lx, y2);
                }
            }
        }

        // Bright lip where the water pours over the top.
        g2.setColor(new Color(220, 245, 255, 200));
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine(x, y, x + w, y);
    }

    /** Ambient underwater backdrop: depth gradient, god rays, drifting bubbles. */
    private void drawUnderwaterBackdrop(Graphics2D g2, int viewW, int viewH) {
        // Depth gradient: lit teal shallows up top fading to dark deep below.
        g2.setPaint(new GradientPaint(0, 0, new Color(28, 72, 104), 0, viewH, new Color(8, 18, 34)));
        g2.fillRect(0, 0, viewW, viewH);

        // Soft god rays slanting down from the surface, drifting slowly.
        double t = frame * 0.01;
        for (int i = 0; i < 5; i++) {
            int xTop = (int) (i * viewW / 4.0 + Math.sin(t + i) * 40);
            Polygon ray = new Polygon();
            ray.addPoint(xTop, 0);
            ray.addPoint(xTop + 60, 0);
            ray.addPoint(xTop + 60 + 160, viewH);
            ray.addPoint(xTop + 160, viewH);
            g2.setColor(new Color(150, 220, 255, 16));
            g2.fillPolygon(ray);
        }

        // Drifting ambient bubbles (screen-space parallax).
        Random r = new Random(7);
        for (int i = 0; i < 40; i++) {
            int bx = r.nextInt(viewW);
            int span = viewH + 80;
            double speed = 12 + r.nextInt(30);
            double ph = (r.nextDouble() * span + frame * speed * 0.05) % span;
            int by = (int) (viewH - ph);
            int s = 2 + r.nextInt(4);
            g2.setColor(new Color(180, 220, 255, 40 + r.nextInt(40)));
            g2.fillOval(bx, by, s, s);
        }
    }

    /** The deadly abyss trench: dark water with jagged spikes along its rim. */
    private void drawHazard(Graphics2D g2, Hazard h) {
        int x = (int) h.x, y = (int) h.y, w = (int) h.width, hh = (int) h.height;
        g2.setColor(new Color(10, 14, 22, 190));
        g2.fillRect(x, y, w, hh);
        // Ominous glow line along the mouth of the trench.
        g2.setColor(new Color(130, 50, 70, 110));
        g2.fillRect(x, y, w, 8);
        // Jagged spikes jutting up from the rim.
        int sp = 22;
        for (int sx = x; sx < x + w; sx += sp) {
            Polygon tri = new Polygon();
            tri.addPoint(sx, y);
            tri.addPoint(sx + sp / 2, y - 18);
            tri.addPoint(sx + sp, y);
            g2.setColor(new Color(40, 46, 58));
            g2.fillPolygon(tri);
            g2.setColor(new Color(70, 78, 92));
            g2.drawPolygon(tri);
        }
    }

    /** Death effect: an expanding ring and a scatter of bubbles bursting out. */
    private void drawDeathBurst(Graphics2D g2) {
        double e = world.deathElapsed;
        int cx = (int) world.deathX, cy = (int) world.deathY;

        int rad = (int) (e * 260);
        int ringA = (int) Math.max(0, 200 - e * 220);
        if (ringA > 0) {
            g2.setStroke(new BasicStroke(3f));
            g2.setColor(new Color(220, 245, 255, ringA));
            g2.drawOval(cx - rad, cy - rad, rad * 2, rad * 2);
        }
        Random r = new Random(99);
        for (int i = 0; i < 26; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = (40 + r.nextDouble() * 160) * e;
            int bx = (int) (cx + Math.cos(ang) * dist);
            int by = (int) (cy + Math.sin(ang) * dist - e * 60); // drift upward
            int ba = (int) Math.max(0, 200 - e * 160);
            if (ba <= 0) continue;
            int s = 3 + r.nextInt(6);
            g2.setColor(new Color(210, 240, 255, ba));
            g2.fillOval(bx, by, s, s);
        }
    }

    /** A simple flag pole marking the finish; the flag turns green on win. */
    private void drawFinish(Graphics2D g2, Finish f) {
        int poleX = (int) f.left() + 20;
        int baseY = (int) f.bottom();
        int topY = (int) f.top() + 10;

        // Pole with a small finial on top.
        g2.setColor(new Color(225, 228, 235));
        g2.setStroke(new BasicStroke(5f));
        g2.drawLine(poleX, baseY, poleX, topY);
        g2.setColor(new Color(245, 215, 90));
        g2.fillOval(poleX - 6, topY - 12, 12, 12);

        // Triangular flag near the top, gently waving.
        int flagW = 54, flagH = 34;
        int wave = (int) (Math.sin(frame * 0.12) * 4);
        Polygon flag = new Polygon();
        flag.addPoint(poleX, topY + 4);
        flag.addPoint(poleX + flagW + wave, topY + 4 + flagH / 2);
        flag.addPoint(poleX, topY + 4 + flagH);
        g2.setColor(world.finished ? new Color(90, 220, 120) : new Color(70, 190, 230));
        g2.fillPolygon(flag);
        g2.setColor(new Color(255, 255, 255, 140));
        g2.setStroke(new BasicStroke(2f));
        g2.drawPolygon(flag);
    }

    /** Fixed-to-screen "LEVEL COMPLETE!" overlay: a dim wash + a brief title. */
    private void drawVictoryOverlay(Graphics2D g2, int viewW, int viewH) {
        double appear = Math.min(1.0, world.finishElapsed / 0.4);
        g2.setColor(new Color(6, 12, 22, (int) (appear * 150)));
        g2.fillRect(0, 0, viewW, viewH);

        String title = "LEVEL COMPLETE!";
        g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(28, viewW / 16)));
        FontMetrics fm = g2.getFontMetrics();
        int tx = viewW / 2 - fm.stringWidth(title) / 2;
        int ty = viewH / 2;
        g2.setColor(new Color(0, 0, 0, (int) (appear * 160)));
        g2.drawString(title, tx + 3, ty + 3);
        g2.setColor(new Color(235, 250, 255, (int) (appear * 255)));
        g2.drawString(title, tx, ty);
    }

    private void drawBlock(Graphics2D g2, Block b) {
        int x = (int) b.x, y = (int) b.y, w = (int) b.width, h = (int) b.height;
        switch (b.type) {
            case SOLID -> {
                g2.setColor(new Color(70, 76, 96));
                g2.fillRect(x, y, w, h);
                g2.setColor(new Color(100, 108, 132));
                g2.drawRect(x, y, w, h);
            }
            case CREST -> {
                g2.setColor(new Color(58, 110, 78));
                g2.fillRect(x, y, w, h);
                // Ridged "crest" highlight along the top edge.
                g2.setColor(new Color(120, 200, 140));
                g2.setStroke(new BasicStroke(3f));
                g2.drawLine(x, y, x + w, y);
            }
            case BLOTCHY -> {
                g2.setColor(new Color(96, 70, 60));
                g2.fillRect(x, y, w, h);
                // Speckled blotches scattered across the block.
                g2.setColor(new Color(130, 100, 84));
                Random r = new Random(((long) x << 20) ^ (y * 31L) ^ w); // stable per block
                int count = Math.max(6, (w * h) / 500);
                for (int i = 0; i < count; i++) {
                    int bx = x + r.nextInt(Math.max(1, w));
                    int by = y + r.nextInt(Math.max(1, h));
                    int bs = 3 + r.nextInt(6);
                    g2.fillOval(bx, by, bs, bs);
                }
            }
            case GRASS -> {
                Random r = new Random(((long) x << 20) ^ (y * 31L) ^ w); // stable per block
                // Dirt body with speckles.
                g2.setColor(new Color(110, 78, 52));
                g2.fillRect(x, y, w, h);
                g2.setColor(new Color(92, 62, 42));
                int count = Math.max(6, (w * h) / 700);
                for (int i = 0; i < count; i++) {
                    int bx = x + r.nextInt(Math.max(1, w));
                    int by = y + 12 + r.nextInt(Math.max(1, h - 12));
                    int bs = 2 + r.nextInt(5);
                    g2.fillOval(bx, by, bs, bs);
                }
                // Grass band across the top, with a brighter lip.
                g2.setColor(new Color(72, 150, 72));
                g2.fillRect(x, y, w, 12);
                g2.setColor(new Color(118, 202, 104));
                g2.fillRect(x, y, w, 4);
                // Grass blades poking up above the surface.
                g2.setColor(new Color(96, 182, 92));
                for (int bx = x + 3; bx < x + w; bx += 10) {
                    int bh = 4 + r.nextInt(6);
                    g2.fillRect(bx, y - bh, 3, bh);
                }
                g2.setColor(new Color(50, 40, 32));
                g2.drawRect(x, y, w, h);
            }
        }
    }
}
