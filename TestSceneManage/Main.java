import javax.swing.*;
import java.awt.*;

public class Main extends JPanel implements Runnable {
    private SceneManager sceneManager;

    public Main() {
        sceneManager = new SceneManager();
        sceneManager.setScene(new IntroductionScene(sceneManager));
        new Thread(this).start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        sceneManager.render(g);
    }

    @Override
    public void run() {
        while (true) {
            sceneManager.update();
            repaint();
            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("ThatTimeYouKilledMe");
        Main gamePanel = new Main();
        frame.setContentPane(gamePanel);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
