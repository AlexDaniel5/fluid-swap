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

    private boolean left, right, jump;

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
        }
    }

    private void tick(double dt) {
        world.moveDir = (right ? 1 : 0) - (left ? 1 : 0);
        if (jump) {
            world.jumpRequested = true;
        }
        world.step(dt);
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

        // Player circle.
        Player p = world.player;
        int d = (int) (p.radius * 2);
        g2.setColor(new Color(90, 200, 255));
        g2.fillOval((int) (p.pos.x - p.radius), (int) (p.pos.y - p.radius), d, d);
        g2.setColor(new Color(220, 245, 255));
        g2.setStroke(new BasicStroke(2f));
        g2.drawOval((int) (p.pos.x - p.radius), (int) (p.pos.y - p.radius), d, d);

        g2.setColor(new Color(150, 160, 180));
        g2.drawString("Move: A/D or ←/→   Jump: Space/W/↑", 12, 20);
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
