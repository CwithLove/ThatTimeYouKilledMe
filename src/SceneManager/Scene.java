package SceneManager;

import java.awt.Graphics;

public interface Scene {
    void init();
    void update();
    void render(Graphics g, int width, int height);
    void dispose();
}
