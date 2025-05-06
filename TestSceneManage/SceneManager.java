import java.awt.*;
import javax.swing.*;

public class SceneManager {
    private Scene currentScene;
    private JPanel gamePanel;
    
    public SceneManager(JPanel gamePanel) {
        this.gamePanel = gamePanel;
    }
    
    public void setScene(Scene scene) {
        if (currentScene != null) {
            currentScene.dispose();
        }
        currentScene = scene;
        currentScene.init();
    }

    public void update() {
        if (currentScene != null) {
            currentScene.update();
        }
    }

    public void render(Graphics g) {
        if (currentScene != null) {
            currentScene.render(g);
        }
    }
    
    public JPanel getPanel() {
        return gamePanel;
    }
}
