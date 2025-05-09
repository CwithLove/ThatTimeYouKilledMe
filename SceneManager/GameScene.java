package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class GameScene implements Scene {

    private static int lastLogin = 0; // 0: single, 1: multi
    private static boolean isHost = true; // 默认为主机
    private SceneManager sceneManager;
    private MouseAdapter mouseAdapter;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private Rectangle backButton;

    // 添加鼠标悬停和点击效果的变量
    private Rectangle hoverButton = null;
    private Rectangle clickButton = null;
    private long clickTime = 0;

    public GameScene() {
        // Pour le cas multi-joueur
    }

    public GameScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        backButton = new Rectangle(50, 500, 150, 40);

        // 创建统一的MouseAdapter来处理所有鼠标事件
        mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete && backButton.contains(e.getPoint())) {
                    clickButton = backButton;
                    clickTime = System.currentTimeMillis();

                    if (lastLogin == 0) {
                        sceneManager.setScene(new MenuScene(sceneManager));
                    } else if (lastLogin == 1) {
                        if (isHost) {
                            sceneManager.setScene(new MultiHostScene(sceneManager));
                        } else {
                            sceneManager.setScene(new LobbyScene(sceneManager, isHost));
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete && backButton.contains(e.getPoint())) {
                    clickButton = backButton;
                    clickTime = System.currentTimeMillis();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                clickButton = null;
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    hoverButton = null;
                    if (backButton.contains(e.getPoint())) {
                        hoverButton = backButton;
                    }
                }
            }
        };

        // 注册鼠标监听器
        sceneManager.getPanel().addMouseListener(mouseAdapter);
        sceneManager.getPanel().addMouseMotionListener(mouseAdapter);
    }

    public GameScene(SceneManager sceneManager, boolean isHost) {
        this(sceneManager);
        GameScene.isHost = isHost;
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
        // Dessiner le fond
        g.setColor(new Color(20, 20, 20));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Dessiner le contenu du jeu
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 32));
        g2d.drawString("Partie en cours...", width / 4, height / 2);

        int buttonWidth = width / 6;
        int buttonHeight = height / 16;

        backButton.setSize(buttonWidth, buttonHeight);
        backButton.setLocation(width / 12, height * 9 / 10);

        // Dessiner le bouton de retour
        if (clickButton == backButton) {
            g2d.setColor(new Color(70, 70, 150));
        } else if (hoverButton == backButton) {
            g2d.setColor(new Color(130, 130, 230));
        } else {
            g2d.setColor(new Color(100, 100, 200));
        }
        g2d.fill(backButton);

        // Si le bouton est clique, dessiner un effet de shadow
        if (clickButton == backButton) {
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillRect(backButton.x + 2, backButton.y + 2, backButton.width - 4, backButton.height - 4);
        }

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics metrics = g2d.getFontMetrics();
        String returnText = "Retour";
        int textWidth = metrics.stringWidth(returnText);
        int textHeight = metrics.getHeight();
        g2d.drawString(returnText,
                backButton.x + (backButton.width - textWidth) / 2,
                backButton.y + (backButton.height + textHeight / 2) / 2);

        g2d.dispose();
    }

    @Override
    public void dispose() {
        // 安全移除鼠标监听器
        if (mouseAdapter != null && sceneManager != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapter);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapter);
        }
    }

    public void updateLastLogin(int loginType) {
        lastLogin = loginType;
    }
}
