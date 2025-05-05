import java.awt.Graphics;

public interface Scene {
    void init();
    void update();
    void render(Graphics g);
    void dispose();
}