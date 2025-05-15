package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class MultiplayerScene implements Scene {

    private MouseAdapter mouseAdapterInternal;
    private SceneManager sceneManager;
    private Button hostButton;
    private Button connectButton;
    private Button backButton;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;

    public MultiplayerScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        // La position et la taille seront ajustées dans render()
        hostButton = new Button(0,0,0,0, "Devenir Hôte", () -> {
            sceneManager.setScene(new HostingScene(sceneManager));
        });

        connectButton = new Button(0,0,0,0, "Rejoindre Hôte", () -> {
            sceneManager.setScene(new ConnectHostScene(sceneManager));
        });

        backButton = new Button(0,0,0,0, "Retour au Menu", () -> {
            sceneManager.setScene(new MenuScene(sceneManager));
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
                    if (hostButton.contains(mousePoint)) hostButton.onClick();
                    else if (connectButton.contains(mousePoint)) connectButton.onClick();
                    else if (backButton.contains(mousePoint)) backButton.onClick();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (hostButton.contains(mousePoint)) hostButton.setClicked(true);
                    else if (connectButton.contains(mousePoint)) connectButton.setClicked(true);
                    else if (backButton.contains(mousePoint)) backButton.setClicked(true);
                    repaintPanel();
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                hostButton.setClicked(false);
                connectButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel();
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    hostButton.update(mousePoint);
                    connectButton.update(mousePoint);
                    backButton.update(mousePoint);
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
            alpha = Math.min(1f, elapsed / 1000f);
            if (alpha >= 1f) {
                fadeComplete = true;
            }
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        g.setColor(new Color(50, 70, 90)); // Arrière-plan un peu khác
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 15;
        g2d.setFont(new Font("SansSerif", Font.BOLD, titleFontSize));
        String title = "Mode Multijoueur";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(title);
        g2d.drawString(title, (width - titleWidth) / 2, height / 5);

        int buttonWidth = Math.max(250, width / 2 - 100);
        int buttonHeight = Math.max(50, height / 10);
        Font buttonFont = new Font("Arial", Font.BOLD, Math.max(18, Math.min(width, height) / 28));
        int buttonSpacing = buttonHeight * 2/3;
        int buttonFontSize = Math.max(16, Math.min(width, height) / 30);


        hostButton.setSize(buttonWidth, buttonHeight);
        hostButton.setLocation(width / 2 - buttonWidth / 2, height / 2 - buttonHeight - buttonSpacing / 2);
        hostButton.setFont(buttonFont);
        hostButton.setNormalColor(new Color(70, 130, 180)); // Steel Blue

        connectButton.setSize(buttonWidth, buttonHeight);
        connectButton.setLocation(width / 2 - buttonWidth / 2, height / 2 + buttonSpacing / 2);
        connectButton.setFont(buttonFont);
        connectButton.setNormalColor(new Color(60, 179, 113)); // Medium Sea Green


        int backButtonWidth = Math.max(120, width / 6);
        int backButtonHeight = Math.max(40, height / 15);
        backButton.setSize(backButtonWidth, backButtonHeight);
        backButton.setLocation(50, height - backButtonHeight - 30); 
        backButton.setFont(new Font("Arial", Font.PLAIN, Math.max(14, buttonFontSize * 3/4)));
        backButton.setNormalColor(new Color(200,100,100));


        hostButton.render(g2d);
        connectButton.render(g2d);
        backButton.render(g2d);

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
        System.out.println("MultiplayerScene disposée.");
    }
}