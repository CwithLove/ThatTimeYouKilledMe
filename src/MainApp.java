import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.*;

public class MainApp {
    // Ratio 16:9 pour la fenêtre de jeu
    private static final double ASPECT_RATIO = 16.0 / 9.0;
    // Taille minimale de la fenêtre
    private static final int MIN_WIDTH = 640;
    private static final int MIN_HEIGHT = 360;
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // Création de la fenêtre principale
            JFrame frame = new JFrame("That Time You Killed Me");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);
            frame.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
            
            // Création du panneau de jeu
            GamePanel gamePanel = new GamePanel(frame);
            
            // Création d'un wrapper avec fond noir pour maintenir le ratio 16:9
            JPanel wrapperPanel = new JPanel();
            wrapperPanel.setBackground(Color.BLACK);
            wrapperPanel.setLayout(new AspectRatioKeeperLayout());
            wrapperPanel.add(gamePanel);
            
            frame.getContentPane().add(wrapperPanel);
            
            // Calcul de la taille initiale basée sur la taille de l'écran
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int initialWidth = (int) (screenSize.width * 0.8);
            int initialHeight = (int) (initialWidth / ASPECT_RATIO);
            
            // Ajustement si la hauteur dépasse l'écran
            if (initialHeight > screenSize.height * 0.8) {
                initialHeight = (int) (screenSize.height * 0.8);
                initialWidth = (int) (initialHeight * ASPECT_RATIO);
            }
            
            frame.setSize(initialWidth, initialHeight);
            
            // Listener pour le redimensionnement
            frame.addComponentListener(new ComponentAdapter() {
                private Timer resizeTimer;
                
                @Override
                public void componentResized(ComponentEvent e) {
                    // Notification au GamePanel pendant le redimensionnement
                    gamePanel.onResizing(gamePanel.getWidth(), gamePanel.getHeight());
                    
                    // Utilisation d'un timer pour détecter la fin du redimensionnement
                    if (resizeTimer != null && resizeTimer.isRunning()) {
                        resizeTimer.restart();
                    } else {
                        resizeTimer = new Timer(200, evt -> {
                            gamePanel.onResizeCompleted(gamePanel.getWidth(), gamePanel.getHeight());
                            resizeTimer.stop();
                        });
                        resizeTimer.setRepeats(false);
                        resizeTimer.start();
                    }
                }
            });
            
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
