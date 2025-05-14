package SceneManager;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.RenderingHints;

public class MenuScene implements Scene {

    private SceneManager sceneManager;
    private MouseAdapter mouseAdapterInternal;
    private Button singleButton;
    private Button multiButton;
    private Button quitButton;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;

    public MenuScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;

        // Vị trí và kích thước sẽ được điều chỉnh trong render()
        singleButton = new Button(0, 0, 0, 0, "Single Player (vs AI)", () -> {
            // GameScene sẽ tự khởi động server local và AI client
            GameScene gameScene = new GameScene(sceneManager, true /* isSelfHostAndRunAI = true */);
            sceneManager.setScene(gameScene);
        });

        multiButton = new Button(0, 0, 0, 0, "Multi Player", () -> {
            sceneManager.setScene(new MultiplayerScene(sceneManager));
        });

        quitButton = new Button(0, 0, 0, 0, "Quitter", () -> {
            // Trước khi thoát, có thể muốn dừng server nếu có server nào đang chạy (ví dụ từ single player trước đó)
            // Tuy nhiên, GameScene.dispose() nên xử lý việc dừng server của nó.
            System.exit(0);
        });
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        alpha = 0f;
        fadeComplete = false;
        setupMouseListeners();
        repaintPanel();
    }

    private void setupMouseListeners() {
        clearMouseListeners();

        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (singleButton.contains(mousePoint)) singleButton.onClick();
                    else if (multiButton.contains(mousePoint)) multiButton.onClick();
                    else if (quitButton.contains(mousePoint)) quitButton.onClick();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (singleButton.contains(mousePoint)) singleButton.setClicked(true);
                    else if (multiButton.contains(mousePoint)) multiButton.setClicked(true);
                    else if (quitButton.contains(mousePoint)) quitButton.setClicked(true);
                    repaintPanel();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                singleButton.setClicked(false);
                multiButton.setClicked(false);
                quitButton.setClicked(false);
                repaintPanel();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    singleButton.update(mousePoint);
                    multiButton.update(mousePoint);
                    quitButton.update(mousePoint);
                    repaintPanel();
                }
            }
        };
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal);
        }
    }

    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
        }
    }

    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1f, elapsed / 1000f); // Fade sur 1 seconde
            if (alpha >= 1f) {
                fadeComplete = true;
            }
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        g.setColor(new Color(50, 50, 100)); // Arrière-plan
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)); // Appliquer fade

        // Titre
        String title = "That Time You Killed Me";
        int fontSize = Math.max(24, Math.min(width, height) / 12); // Taille de police dynamique
        Font titleFont = new Font("Serif", Font.BOLD | Font.ITALIC, fontSize);
        g2d.setFont(titleFont);
        g2d.setColor(Color.ORANGE); // Couleur du titre
        FontMetrics titleMetrics = g2d.getFontMetrics(titleFont);
        int titleWidth = titleMetrics.stringWidth(title);
        g2d.drawString(title, (width - titleWidth) / 2, height / 5);

        // Boutons
        int buttonWidth = Math.max(200, width / 3);
        int buttonHeight = Math.max(50, height / 10);
        Font buttonFont = new Font("Arial", Font.BOLD, Math.max(16, Math.min(width, height) / 30));
        int buttonSpacing = buttonHeight / 2;

        singleButton.setSize(buttonWidth, buttonHeight);
        singleButton.setLocation(width / 2 - buttonWidth / 2, height / 2 - buttonHeight - buttonSpacing);
        singleButton.setFont(buttonFont);

        multiButton.setSize(buttonWidth, buttonHeight);
        multiButton.setLocation(width / 2 - buttonWidth / 2, height / 2);
        multiButton.setFont(buttonFont);

        quitButton.setSize(buttonWidth, buttonHeight);
        quitButton.setLocation(width / 2 - buttonWidth / 2, height / 2 + buttonHeight + buttonSpacing);
        quitButton.setFont(buttonFont);

        singleButton.render(g2d);
        multiButton.render(g2d);
        quitButton.render(g2d);

        g2d.dispose();
    }

    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }

    @Override
    public void dispose() {
        clearMouseListeners();
        System.out.println("MenuScene disposée.");
    }
}