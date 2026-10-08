package fluidswap;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Launches the windowed game. */
public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            World world = Level.build();
            JFrame frame = new JFrame("Fluid Swap");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            GamePanel panel = new GamePanel(world);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setResizable(false);
            frame.setVisible(true);
            panel.requestFocusInWindow();
        });
    }
}
