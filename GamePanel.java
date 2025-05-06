import SceneManager.IntroductionScene;
import SceneManager.SceneManager;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class GamePanel extends JPanel implements ActionListener {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int FPS = 60;
    private Timer timer;
    private SceneManager sceneManager;
    
    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        requestFocus();
        
        sceneManager = new SceneManager(this);
        
        sceneManager.setScene(new IntroductionScene(sceneManager));
        
        timer = new Timer(1000 / FPS, this);
        timer.start();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        sceneManager.render(g, getWidth(), getHeight());
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        sceneManager.update();
        
        repaint();
    }
} 