package SceneManager;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MenuScene implements Scene {

    private SceneManager sceneManager;
    private Button singleButton;
    private Button multiButton;
    private Button quitButton;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private int WIDTHOFSCREEN;
    private int HEIGHTOFSCREEN;

    public MenuScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        // 创建按钮并设置点击事件
        singleButton = new Button(300, 250, 200, 50, "Single Player", () -> {
            GameScene gameScene = new GameScene(sceneManager);
            gameScene.updateLastLogin(0);
            sceneManager.setScene(gameScene);
        });
        
        multiButton = new Button(300, 350, 200, 50, "Multi Player", () -> {
            sceneManager.setScene(new HostOrConnectScene(sceneManager));
        });
        
        quitButton = new Button(300, 450, 200, 50, "Quit", () -> {
            System.exit(0); // Normallement retourner le main menu
        });

        // Mouse Listener
        sceneManager.getPanel().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (singleButton.contains(mousePoint)) {
                        singleButton.onClick();
                    } else if (multiButton.contains(mousePoint)) {
                        multiButton.onClick();
                    } else if (quitButton.contains(mousePoint)) {
                        quitButton.onClick();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (singleButton.contains(mousePoint)) {
                        singleButton.setClicked(true);
                    } else if (multiButton.contains(mousePoint)) {
                        multiButton.setClicked(true);
                    } else if (quitButton.contains(mousePoint)) {
                        quitButton.setClicked(true);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                singleButton.setClicked(false);
                multiButton.setClicked(false);
                quitButton.setClicked(false);
            }
        });

        sceneManager.getPanel().addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    singleButton.update(mousePoint);
                    multiButton.update(mousePoint);
                    quitButton.update(mousePoint);
                }
            }
        });
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

        // Title
        String title = "That Time You Killed Me";
        int fontSize = Math.min(width, height) / 15; // Dynamically adjust font size based on screen dimensions
        Font titleFont = new Font("Arial", Font.BOLD, fontSize);
        g2d.setFont(titleFont);
        g2d.setColor(Color.WHITE);
        int titleWidth = g2d.getFontMetrics(titleFont).stringWidth(title);
        int titleX = (width - titleWidth) / 2;
        int titleY = height / 5;
        g2d.drawString(title, titleX, titleY);

        // 调整按钮大小和位置
        int buttonWidth = width / 4;
        int buttonHeight = height / 12;
        Font buttonFont = new Font("Arial", Font.BOLD, Math.min(width, height) / 40);

        singleButton.setSize(buttonWidth, buttonHeight);
        singleButton.setLocation(width / 2 - buttonWidth / 2, height / 3);
        singleButton.setFont(buttonFont);

        multiButton.setSize(buttonWidth, buttonHeight);
        multiButton.setLocation(width / 2 - buttonWidth / 2, height / 3 + buttonHeight + height / 20);
        multiButton.setFont(buttonFont);

        quitButton.setSize(buttonWidth, buttonHeight);
        quitButton.setLocation(width / 2 - buttonWidth / 2, height / 3 + 2 * (buttonHeight + height / 20));
        quitButton.setFont(buttonFont);

        // 渲染按钮
        singleButton.render(g2d);
        multiButton.render(g2d);
        quitButton.render(g2d);

        g2d.dispose();
    }

    @Override
    public void dispose() {
        sceneManager.getPanel().removeMouseListener(sceneManager.getPanel().getMouseListeners()[0]);
        sceneManager.getPanel().removeMouseMotionListener(sceneManager.getPanel().getMouseMotionListeners()[0]);
    }
}
