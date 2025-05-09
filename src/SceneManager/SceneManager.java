package SceneManager;

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

    public void render(Graphics g, int width, int height) {
        if (currentScene != null) {
            currentScene.render(g, width, height);
        }
    }


    
    public JPanel getPanel() {
        return gamePanel;
    }

    public Scene getCurrentScene() {
        return currentScene;
    }


}
