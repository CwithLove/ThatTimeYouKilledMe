package SceneManager;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

// A CHANGER DYNAMIQUE LA RESOLUTION

public class MenuScene implements Scene {
    private SceneManager sceneManager;
    private Rectangle singleButton;
    private Rectangle multiButton;
    private Rectangle quitButton;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private int WIDTHOFSCREEN;
    private int HEIGHTOFSCREEN;

    public MenuScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;

    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();

        
    }

    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1f, elapsed / 1000f); 
            if (alpha >= 1f) {
                fadeComplete = true;
            }
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        // Background
        g.setColor(new Color(50, 50, 100));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        
        // 绘制标题
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Menu", width/2, height/4);

        // A CHANGER DYNAMIQUE
        singleButton = new Rectangle(width/2, height/4 + 100, 200, 50);
        multiButton = new Rectangle(width/2, height/4 + 200, 200, 50);
        quitButton = new Rectangle(width/2, height/4 + 300, 200, 50);

        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    if (singleButton.contains(e.getPoint())) {
                        sceneManager.setScene(new GameScene(sceneManager));
                    } else if (multiButton.contains(e.getPoint())) {
                        sceneManager.setScene(new HostOrConnectScene(sceneManager));
                    } else if (quitButton.contains(e.getPoint())) {
                        System.exit(0); // Normallement retourner le main menu
                    }
                }
            }
        });
        
        // Button
        g2d.setColor(new Color(100, 100, 200));
        g2d.fill(singleButton);
        g2d.fill(multiButton);
        g2d.fill(quitButton);


        

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("Single Player", singleButton.x + 50, singleButton.y + 35);
        g2d.drawString("Multi Player", multiButton.x + 50, multiButton.y + 35);
        

        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        g2d.drawString("Quit", quitButton.x + 45, quitButton.y + 25);
        
        g2d.dispose();
    }

    @Override
    public void dispose() {
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);
    }
}
