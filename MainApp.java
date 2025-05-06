import javax.swing.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class MainApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("TTYKM");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);
            
            GamePanel gamePanel = new GamePanel(frame);
            frame.add(gamePanel);

            frame.addComponentListener(new ComponentAdapter() {
                private boolean estEnRedimensionnement = false;
                private Timer timerFinRedimensionnement;

                {
                    // Initialiser le timer dans un bloc d'initialisation
                    timerFinRedimensionnement = new Timer(200, e -> {
                        estEnRedimensionnement = false;
                        System.out.println("Fin du redimensionnement. Nouvelle taille : "
                                          + frame.getWidth() + "x" + frame.getHeight());

                        // Notifier le GamePanel du changement de taille final
                        gamePanel.onResizeCompleted(frame.getWidth(), frame.getHeight());

                        timerFinRedimensionnement.stop();
                    });
                    timerFinRedimensionnement.setRepeats(false);
                }

                @Override
                public void componentResized(ComponentEvent e) {
                    // Détection de début de redimensionnement
                    if (!estEnRedimensionnement) {
                        estEnRedimensionnement = true;
                        System.out.println("Début du redimensionnement");

                        // Notifier le GamePanel que le redimensionnement commence
                        gamePanel.onResizeStarted();
                    }


                    // Notifier le GamePanel à chaque étape du redimensionnement
                    gamePanel.onResizing(frame.getWidth(), frame.getHeight());

                    // Redémarrer le timer pour détecter la fin du redimensionnement
                    timerFinRedimensionnement.restart();
                }
            });



            
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
} 