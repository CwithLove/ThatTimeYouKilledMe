import java.awt.Graphics;

public class SceneManager {
    private Scene currentScene;

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
}
