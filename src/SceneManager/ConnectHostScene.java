package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

public class ConnectHostScene implements Scene {
    private SceneManager sceneManager;
    private Button connectButton;
    private Button backButton;
    private JTextField ipAddressField;
    private String statusMessage = "Entrez l'adresse IP de l'hôte.";

    private MouseAdapter mouseAdapterInternal;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;

    public ConnectHostScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        ipAddressField = new JTextField("127.0.0.1"); // Default IP

        connectButton = new Button(300, 300, 200, 50, "Se connecter", () -> {
            String ip = ipAddressField.getText().trim();
            if (ip.isEmpty()) {
                statusMessage = "L'adresse IP ne peut pas être vide.";
                JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur de Saisie", JOptionPane.WARNING_MESSAGE);
                repaintPanel();
                return;
            }
            System.out.println("ConnectHostScene: Passage à GameScene avec IP: " + ip);
            
            GameScene gameScene = new GameScene(sceneManager, ip);
            sceneManager.setScene(gameScene);
        });

        backButton = new Button(50, 400, 150, 40, "Retour", () -> {
            sceneManager.setScene(new MultiplayerScene(sceneManager));
        });
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        alpha = 0f;
        fadeComplete = false;
        statusMessage = "Entrez l'adresse IP de l'hôte.";

        if (sceneManager.getPanel() != null) {
            int panelWidth = sceneManager.getPanel().getWidth();
            int panelHeight = sceneManager.getPanel().getHeight();
            int fieldWidth = Math.max(200, panelWidth / 3); // Augmenter légèrement la largeur
            int fieldHeight = 35; // Augmenter légèrement la hauteur
            ipAddressField.setBounds(panelWidth / 2 - fieldWidth / 2, panelHeight / 2 - 60, fieldWidth, fieldHeight);
            ipAddressField.setFont(new Font("Arial", Font.PLAIN, 18)); // Augmenter la taille de la police
            ipAddressField.setHorizontalAlignment(JTextField.CENTER); // Centrer le texte
            sceneManager.getPanel().setLayout(null);
            sceneManager.getPanel().add(ipAddressField);
            ipAddressField.setVisible(true);
            SwingUtilities.invokeLater(() -> ipAddressField.requestFocusInWindow()); // Demander le focus après l'affichage du panel
        }

        setupMouseListeners();
        repaintPanel();
    }

    private void setupMouseListeners() {
        clearMouseListeners();
        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!fadeComplete) return;
                Point mousePoint = e.getPoint();
                if (ipAddressField != null && ipAddressField.getBounds().contains(mousePoint)) {
                    ipAddressField.requestFocusInWindow();
                    return;
                }
                if (connectButton.contains(mousePoint)) connectButton.onClick();
                else if (backButton.contains(mousePoint)) backButton.onClick();
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (!fadeComplete) return;
                Point mousePoint = e.getPoint();
                if (connectButton.contains(mousePoint)) connectButton.setClicked(true);
                else if (backButton.contains(mousePoint)) backButton.setClicked(true);
                repaintPanel();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                connectButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel();
            }
             @Override
            public void mouseMoved(MouseEvent e) { // Ajouter mouseMoved pour mettre à jour le hover
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
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
            if (alpha >= 1f) fadeComplete = true;
        }
        // Mettre à jour le hover et le clic des boutons
    }

    @Override
    public void render(Graphics g, int width, int height) {
        g.setColor(new Color(45, 60, 75));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 18;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String titleText = "Rejoindre une Partie";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int textWidth = titleMetrics.stringWidth(titleText);
        g2d.drawString(titleText, (width - textWidth) / 2, height / 5);

        if (statusMessage != null && !statusMessage.isEmpty()) {
            g2d.setFont(new Font("Arial", Font.ITALIC, Math.min(width,height)/35));
            FontMetrics statusMetrics = g2d.getFontMetrics();
            int statusWidth = statusMetrics.stringWidth(statusMessage);
            g2d.drawString(statusMessage, (width - statusWidth) / 2, height / 2 - 90);
        }

        // ipAddressField 

        int btnWidth = width / 4;
        int btnHeight = height / 12;
        int btnFontSize = Math.min(width, height) / 30;
        Font commonBtnFont = new Font("Arial", Font.BOLD, btnFontSize);

        connectButton.setSize(btnWidth, btnHeight);
        connectButton.setLocation(width / 2 - btnWidth / 2, height / 2 + 30); 
        connectButton.setFont(commonBtnFont);

        backButton.setSize(btnWidth * 3/4, btnHeight * 3/4);
        backButton.setLocation(50, height - (btnHeight*3/4) - 30 );
        backButton.setFont(new Font("Arial", Font.PLAIN, btnFontSize * 3/4));


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
        if (sceneManager.getPanel() != null && ipAddressField != null) {
            ipAddressField.setVisible(false); 
            sceneManager.getPanel().remove(ipAddressField);
            sceneManager.getPanel().revalidate(); 
            sceneManager.getPanel().repaint();
        }
        System.out.println("ConnectHostScene disposée.");
    }
}