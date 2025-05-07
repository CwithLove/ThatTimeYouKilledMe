import SceneManager.IntroductionScene;
import SceneManager.SceneManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class GamePanel extends JPanel implements ActionListener {
    private int WIDTH;
    private int HEIGHT;
    private static final int FPS = 60;
    private Timer timer;
    private SceneManager sceneManager;
    private JFrame frame;

    
    public GamePanel(JFrame frame) {
        this.frame = frame;
        WIDTH = frame.getWidth();
        HEIGHT = frame.getHeight();
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        requestFocus();
        
        sceneManager = new SceneManager(this);
        
        sceneManager.setScene(new IntroductionScene(sceneManager));
        
        timer = new Timer(1000 / FPS, this);
        timer.start();
    }

    public void onResizeStarted() {
        //System.out.println("GamePanel : début du redimensionnement");
    }

    // Appelée pendant le redimensionnement
    public void onResizing(int newWidth, int newHeight) {
        // Mise à jour progressive de l'interface pendant le redimensionnement
        // Attention à ne pas faire des calculs trop lourds ici, car cette
        // méthode est appelée très fréquemment pendant le redimensionnement
        //System.out.println("GamePanel : redimensionnement en cours");



        // Vous pouvez ajuster certains éléments d'interface ici
        // Par exemple :
        setPreferredSize(new Dimension(newWidth, newHeight));


        revalidate(); // Recalculer la disposition


    }

    // Appelée à la fin du redimensionnement
    public void onResizeCompleted(int finalWidth, int finalHeight) {
        // Faire des ajustements finaux une fois le redimensionnement terminé
        //System.out.println("GamePanel : redimensionnement terminé");

        // Adapter complètement l'interface à la nouvelle taille
        adjustLayout(finalWidth, finalHeight);
        repaint(); // Redessiner le panneau



    }

    private void adjustLayout(int width, int height) {
        // Méthode d'exemple pour ajuster la disposition en fonction de la nouvelle taille
        // Implémentez votre logique spécifique ici
    }

    @Override
    public Dimension getPreferredSize() {
        // Définir une taille par défaut pour le panneau
        //Dimension par default
        return new Dimension(800, 600); // Taille initiale
    }


    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        sceneManager.render(g, frame.getWidth(), frame.getHeight());
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        sceneManager.update();
        
        repaint();
    }


}