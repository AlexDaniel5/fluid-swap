package fluidswap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Random;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Swing panel: runs the fixed-step loop, reads keys, and draws the world. */
public final class GamePanel extends JPanel {

    private final World world;

    private boolean left, right, jump, down;
    private long frame; // animation counter for the waterfalls

    public GamePanel(World world) {
        this.world = world;
        setPreferredSize(new Dimension(Level.WIDTH, Level.HEIGHT));
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
        world.step(dt);
        frame++;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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

        // Waterfalls: translucent column with animated downward streaks.
        for (Waterfall f : world.waterfalls) {
            drawWaterfall(g2, f);
        }

        // Player: circle sprite (its collision hitbox is a box of the same bounds).
        Player p = world.player;
        int d = (int) (p.radius * 2);
        g2.setColor(new Color(90, 200, 255));
        g2.fillOval((int) (p.pos.x - p.radius), (int) (p.pos.y - p.radius), d, d);
        g2.setColor(new Color(220, 245, 255));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval((int) (p.pos.x - p.radius), (int) (p.pos.y - p.radius), d, d);

        String medium = p.onWaterfall ? "WATERFALL (climb)"
                : p.inWater ? "WATER (fast)"
                : (p.onGround ? "LAND (slow)" : "AIR (medium)");
        g2.setColor(new Color(150, 160, 180));
        g2.drawString("Move: A/D or ←/→   Jump/Swim/Climb up: Space/W/↑   Climb down: S/↓", 12, 20);
        g2.drawString("Medium: " + medium + "   speed=" + (int) world.activeMoveSpeed(), 12, 38);
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
        }
    }
}
