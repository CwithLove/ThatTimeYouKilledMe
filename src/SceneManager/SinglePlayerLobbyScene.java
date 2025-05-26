package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComboBox;

/**
 * SinglePlayerLobbyScene est la scène du lobby pour le mode solo Permet au
 * joueur de sélectionner la difficulté de l'IA et de se connecter
 * automatiquement avec l'IA comme deuxième joueur
 */
public class SinglePlayerLobbyScene implements Scene {

    private SceneManager sceneManager;
    private Button startGameButton;
    private Button backButton;

    // Menu déroulant pour la sélection de la difficulté de l'IA
    private JComboBox<String> difficultyComboBox;
    private String selectedDifficulty = "Facile"; // Difficulté par défaut: facile
    private final String[] DIFFICULTY_OPTIONS = {"Facile", "Moyen", "Difficile"};

    private volatile boolean serverSuccessfullyStarted = false;
    private volatile boolean aiPlayerConnected = false;
    private volatile boolean hostClientConnected = false;

    private MouseAdapter mouseAdapterInternal;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private String hostIP = "127.0.0.1"; // IP locale pour le jeu solo
    private int animationDots = 0;
    private long lastDotTime = 0;
    private String statusMessage = "Appuyer sur Commencer pour commencer le jeu";

    // vs AI
    private int levelAI = 2;

    private boolean transitioningToGameScene = false;

    /**
     * Constructeur
     *
     * @param sceneManager Gestionnaire de scènes
     */
    public SinglePlayerLobbyScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.transitioningToGameScene = false;

        // Initialisation du menu déroulant
        difficultyComboBox = new JComboBox<>(DIFFICULTY_OPTIONS);
        difficultyComboBox.setSelectedItem("Facile"); // Sélection par défaut: facile
        difficultyComboBox.addActionListener(e -> {
            selectedDifficulty = (String) difficultyComboBox.getSelectedItem();
            System.out.println("Difficulté changée à: " + selectedDifficulty);
            repaintPanel();
        });

        // A COMPLETER: AJOUETER LA DIFFICULTE SÉLECTIONNÉE À L'IA
        // Bouton de démarrage du jeu
        startGameButton = new Button(300, 450, 200, 50, "Commencer", () -> {
            sceneManager.setScene(new GameScene(sceneManager, true, levelAI));
        });

        // Bouton de retour
        backButton = new Button(50, 500, 150, 40, "Retour", this::cleanUpAndGoToMenu);
    }

    /**
     * Nettoie les ressources et retourne au menu principal
     */
    private void cleanUpAndGoToMenu() {
        System.out.println("SinglePlayerLobbyScene: cleanUpAndGoToMenu appelé. transitioningToGameScene = " + transitioningToGameScene);
        if (!transitioningToGameScene) {
            System.out.println("SinglePlayerLobbyScene: Arrêt du GameServerManager.");
        } else if (transitioningToGameScene) {
            System.out.println("SinglePlayerLobbyScene: GameServerManager sera géré par GameScene.");
        }


        sceneManager.setScene(new MenuScene(sceneManager));
    }

    /**
     * Initialisation de la scène
     */
    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        alpha = 0f;
        fadeComplete = false;
        transitioningToGameScene = false;
        startGameButton.setEnabled(true);
        setupMouseListeners();
        lastDotTime = startTime;
        repaintPanel();
    }

    /**
     * Mise à jour de la logique de la scène
     */
    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1.0f, elapsed / 1000.0f);
            if (alpha >= 1.0f) {
                fadeComplete = true;
            }
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDotTime > 500) {
            animationDots = (animationDots + 1) % 4;
            lastDotTime = currentTime;
        }

        if (fadeComplete && sceneManager.getPanel() != null) {
            Point mousePos = sceneManager.getPanel().getMousePosition();
            if (mousePos != null) {

                startGameButton.update(mousePos);

                backButton.update(mousePos);
            }
        }

        selectedDifficulty = (String) difficultyComboBox.getSelectedItem();
        switch (selectedDifficulty) {
            case "Facile":
                levelAI = 1; // Niveau facile
                break;
            case "Moyen":
                levelAI = 3; // Niveau moyen
                break;
            case "Difficile":
                levelAI = 5; // Niveau difficile
                break;
            default:
                levelAI = 1; // Par défaut, niveau facile
        }
    }

    /**
     * Rendu de la scène
     */
    @Override
    public void render(Graphics g, int width, int height) {
        g.setColor(new Color(40, 40, 80));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Titre
        g2d.setColor(Color.WHITE);
        int titleFontSize = height / 18;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String titleText = "Mode Solo - Combat contre IA";
        FontMetrics titleMetricsFont = g2d.getFontMetrics();
        int titleTextWidth = titleMetricsFont.stringWidth(titleText);
        g2d.drawString(titleText, (width - titleTextWidth) / 2, height / 7);

        // Titre de sélection de la difficulté
        int infoFontSize = height / 28;
        g2d.setFont(new Font("Arial", Font.BOLD, infoFontSize));
        g2d.setColor(Color.LIGHT_GRAY);
        String difficultyText = "Sélectionner la difficulté:";
        FontMetrics diffMetrics = g2d.getFontMetrics();
        int diffTextWidth = diffMetrics.stringWidth(difficultyText);
        g2d.drawString(difficultyText, width * 3 / 4 - diffTextWidth / 2, height * 4 / 9 - infoFontSize / 2);

        // Zone des joueurs
        int zoneWidth = width / 2 - 20;
        int zoneHeight = height / 3;
        int zoneY = height / 5;

        // Zone du joueur 1 (gauche)
        drawPlayerZone(g2d, 10, zoneY, zoneWidth, zoneHeight, true, infoFontSize);

        // Zone du joueur IA (droite)
        drawPlayerZone(g2d, width / 2 + 10, zoneY, zoneWidth, zoneHeight, false, infoFontSize);

        // Message d'état
        if (statusMessage != null && !statusMessage.isEmpty()) {
            g2d.setFont(new Font("Arial", Font.ITALIC, Math.max(12, infoFontSize * 3 / 4)));
            FontMetrics statusMetrics = g2d.getFontMetrics();
            int statusWidth = statusMetrics.stringWidth(statusMessage);
            g2d.drawString(statusMessage, (width - statusWidth) / 2, height * 2 / 3 + 40);
        }

        // Paramètres du bouton
        int btnWidth =  width / 4;
        int btnHeight = height / 13;
        int btnFontSize = height / 35;
        Font commonBtnFont = new Font("Arial", Font.BOLD, btnFontSize);

        // Placement du menu déroulant de sélection de difficulté
        if (sceneManager.getPanel() != null && !sceneManager.getPanel().isAncestorOf(difficultyComboBox)) {
            sceneManager.getPanel().add(difficultyComboBox);
        }
        int comboBoxWidth = width / 10;
        int comboBoxHeight = height / 25;
        int comboBoxX = width * 75 / 100 - comboBoxWidth / 2; // 75% de la largeur;
        int comboBoxY = height * 6 / 13 - comboBoxHeight / 2; // Centré verticalement
        difficultyComboBox.setBounds(comboBoxX, comboBoxY, comboBoxWidth, comboBoxHeight);
        difficultyComboBox.setFont(new Font("Arial", Font.PLAIN, btnFontSize * 2 / 3));
        difficultyComboBox.setVisible(fadeComplete); // Seulement visible après la fin de la transition

        // Bouton de démarrage du jeu
        startGameButton.setSize(btnWidth, btnHeight);
        startGameButton.setLocation(width / 2 - btnWidth / 2, height * 4 / 5);
        startGameButton.setFont(commonBtnFont);

        // Bouton de retour
        backButton.setSize(btnWidth * 3 / 4, btnHeight * 3 / 4);
        backButton.setLocation(40, height - btnHeight * 3 / 4 - 25);
        backButton.setFont(new Font("Arial", Font.PLAIN, Math.max(12, btnFontSize * 3 / 4)));

        // Rendu du bouton
        startGameButton.render(g2d);
        backButton.render(g2d);

        g2d.dispose();
    }

    /**
     * Dessine la zone du joueur
     */
    private void drawPlayerZone(Graphics2D g2d, int x, int y, int width, int height,
            boolean isHost, int fontSize) {
        g2d.setColor(new Color(60, 60, 100, 180));
        g2d.fillRoundRect(x, y, width, height, 20, 20);
        g2d.setColor(isHost ? new Color(100, 150, 255) : new Color(255, 150, 100));
        g2d.setStroke(new BasicStroke(3));
        g2d.drawRoundRect(x, y, width, height, 20, 20);

        String playerTitle = isHost ? "Joueur" : "IA";
        g2d.setFont(new Font("Arial", Font.BOLD, fontSize * 3 / 2));
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(playerTitle);
        g2d.drawString(playerTitle, x + (width - titleWidth) / 2, y + fontSize * 2);

        String statusText = isHost ? "Vous" : "BOT (" + selectedDifficulty + ")";
        g2d.setColor(Color.GREEN);

        g2d.setFont(new Font("Arial", Font.PLAIN, fontSize));
        FontMetrics statusMetrics = g2d.getFontMetrics();
        int statusWidth = statusMetrics.stringWidth(statusText);
        g2d.drawString(statusText, x + (width - statusWidth) / 2, y + fontSize * 4);

        // A COMPLETER: AJOUTER CE QUI COMMENCE PAR "Joueur" OU "IA"
        // g2d.setColor(Color.WHITE);
        // String idText = "ID: " + playerId;
        // FontMetrics idMetrics = g2d.getFontMetrics();
        // int idWidth = idMetrics.stringWidth(idText);
        // g2d.drawString(idText, x + (width - idWidth) / 2, y + fontSize * 6);

    }

    /**
     * Configuration des écouteurs de souris
     */
    private void setupMouseListeners() {
        clearMouseListeners();
        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!fadeComplete || transitioningToGameScene) {
                    return;
                }
                Point mousePoint = e.getPoint();
                if (startGameButton.contains(mousePoint)) {
                    startGameButton.onClick();
                } else if (backButton.contains(mousePoint)) {
                    backButton.onClick();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!fadeComplete || transitioningToGameScene) {
                    return;
                }
                Point mousePoint = e.getPoint();
                if (startGameButton.isEnabled() && startGameButton.contains(mousePoint)) {
                    startGameButton.setClicked(true);
                } else if (backButton.isEnabled() && backButton.contains(mousePoint)) {
                    backButton.setClicked(true);
                }
                repaintPanel();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (transitioningToGameScene) {
                    return;
                }
                startGameButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel();
            }
        };
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal);
        }
    }

    /**
     * Suppression des écouteurs de souris
     */
    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
        }
    }

    /**
     * Rafraîchissement du panneau
     */
    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }

    /**
     * Définit si la transition vers la scène de jeu est en cours
     */
    public void setTransitioningToGameScene(boolean transitioning) {
        this.transitioningToGameScene = transitioning;
        System.out.println("SinglePlayerLobbyScene: transitioningToGameScene défini à " + transitioning);
    }

    /**
     * Libération des ressources
     */
    @Override
    public void dispose() {
        clearMouseListeners();
        System.out.println("SinglePlayerLobbyScene: Dispose appelé. transitioningToGameScene = " + transitioningToGameScene);

        // Suppression du menu déroulant de sélection de difficulté
        if (sceneManager.getPanel() != null && difficultyComboBox != null) {
            sceneManager.getPanel().remove(difficultyComboBox);
        }
        difficultyComboBox = null;
        System.out.println("SinglePlayerLobbyScene: Dispose terminé.");
    }
}
