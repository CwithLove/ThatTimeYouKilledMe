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
    private static final double ASPECT_RATIO = 16.0 / 9.0;
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
        
    }

    public void onResizing(int newWidth, int newHeight) {
        setPreferredSize(new Dimension(newWidth, newHeight));
        WIDTH = newWidth;
        HEIGHT = newHeight;
        
        revalidate();
    }

    public void onResizeCompleted(int finalWidth, int finalHeight) {
        
        WIDTH = finalWidth;
        HEIGHT = finalHeight;
        
        adjustLayout(finalWidth, finalHeight);
        repaint();
    }

    private void adjustLayout(int width, int height) {
        WIDTH = width;
        HEIGHT = height;
    }

    @Override
    public Dimension getPreferredSize() {
        int preferredWidth = 1280;
        int preferredHeight = (int)(preferredWidth / ASPECT_RATIO);
        return new Dimension(preferredWidth, preferredHeight);
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