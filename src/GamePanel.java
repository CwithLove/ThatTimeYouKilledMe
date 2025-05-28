import SceneManager.IntroductionScene;
import SceneManager.SceneManager;
import SceneManager.MenuScene; // Added import for MenuScene
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class GamePanel extends JPanel implements ActionListener {
    // Dimensions de base pour le ratio 16:9
    private static final int BASE_WIDTH = 640;
    private static final int BASE_HEIGHT = 360; // 16:9
    // Images par seconde pour le jeu
    private static final int FPS = 60;
    
    private Timer timer;
    private SceneManager sceneManager;
    private JFrame frame;
    
    public GamePanel(JFrame frame) {
        this.frame = frame;
        setBackground(Color.BLACK);
        setFocusable(true);
        requestFocus();
        
        // Forcer la taille de la fenêtre 16:9
        setPreferredSize(new Dimension(BASE_WIDTH, BASE_HEIGHT));
        
        // Initialisation du gestionnaire de scènes
        sceneManager = new SceneManager(this);
        //sceneManager.setScene(new MenuScene(sceneManager));//TO DO change here 
        sceneManager.setScene(new IntroductionScene(sceneManager));
        
        // Démarrage de la boucle de jeu
        timer = new Timer(1000 / FPS, this);
        timer.start();
    }

    /**
     * Appelé au début du redimensionnement
     */
    public void onResizeStarted() {
        // Rien à faire pour l'instant
    }

    /**
     * Appelé pendant le redimensionnement
     */
    public void onResizing(int newWidth, int newHeight) {
        repaint();
    }

    /**
     * Appelé à la fin du redimensionnement
     */
    public void onResizeCompleted(int finalWidth, int finalHeight) {
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        // Toujours retourner la taille 16:9
        return new Dimension(BASE_WIDTH, BASE_HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Rendu de la scène actuelle
        sceneManager.render(g, getWidth(), getHeight());
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        // Mise à jour de la logique du jeu
        sceneManager.update();
        repaint();
    }
}