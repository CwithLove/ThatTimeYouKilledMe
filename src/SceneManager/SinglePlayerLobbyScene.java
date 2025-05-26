package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;

/**
 * SinglePlayerLobbyScene est la scène du lobby pour le mode solo
 * Permet au joueur de sélectionner la difficulté de l'IA et de se connecter
 * automatiquement avec l'IA comme deuxième joueur
 */
public class SinglePlayerLobbyScene implements Scene {

    private SceneManager sceneManager;
    private Button startGameButton;
    private Button loadGameButton; // Bouton pour charger une partie
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
            System.out.println("Difficulty changed to: " + selectedDifficulty);
            repaintPanel();
        });

        // Bouton de démarrage du jeu
        startGameButton = new Button(300, 450, 200, 50, "Commencer", () -> {
            sceneManager.setScene(new GameScene(sceneManager, true, levelAI));
        });

        // Bouton pour charger une partie sauvegardée
        loadGameButton = new Button(300, 520, 200, 50, "Charger Partie", this::handleLoadGame);

        // Bouton de retour
        backButton = new Button(50, 500, 150, 40, "Retour", this::cleanUpAndGoToMenu);
    }

    /**
     * Classe interne pour stocker les informations de sauvegarde
     */
    private static class SaveInfo {
        String saveName;
        int playerId;
        String saveTime;
        long fileSize;
        String gameData;
        Path filePath;

        SaveInfo(String saveName, int playerId, String saveTime, long fileSize, String gameData, Path filePath) {
            this.saveName = saveName;
            this.playerId = playerId;
            this.saveTime = saveTime;
            this.fileSize = fileSize;
            this.gameData = gameData;
            this.filePath = filePath;
        }
    }

    /**
     * Gère le chargement d'une partie sauvegardée
     */
    private void handleLoadGame() {
        if (transitioningToGameScene) {
            return;
        }

        // Vérifier si le dossier de sauvegarde existe
        Path savesDir = Paths.get("saves");
        if (!Files.exists(savesDir)) {
            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "No save folder found.",
                    "No Saves", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Lister les fichiers de sauvegarde disponibles
        List<SaveInfo> saves = new ArrayList<>();
        try {
            Files.list(savesDir)
                .filter(path -> path.toString().endsWith(".save"))
                .forEach(path -> {
                    try {
                        SaveInfo saveInfo = parseSaveFile(path);
                        if (saveInfo != null) {
                            saves.add(saveInfo);
                        }
                    } catch (Exception e) {
                        System.err.println("Error reading save file: " + path + " - " + e.getMessage());
                    }
                });
        } catch (IOException e) {
            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "Error reading save folder: " + e.getMessage(),
                    "Read Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (saves.isEmpty()) {
            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "No valid saves found in 'saves' folder.",
                    "No Saves", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Trier les sauvegardes par date (plus récentes en premier)
        saves.sort((a, b) -> b.saveTime.compareTo(a.saveTime));

        // Créer la liste des descriptions de sauvegarde pour l'utilisateur
        String[] saveDescriptions = saves.stream()
                .map(save -> String.format("%s (Player %d, %s, %.1fKB)",
                     save.saveName, save.playerId,
                     formatSaveTime(save.saveTime), save.fileSize / 1024.0))
                .toArray(String[]::new);

        // Demander à l'utilisateur de choisir une sauvegarde
        String selected = (String) JOptionPane.showInputDialog(
                sceneManager.getPanel(),
                "Select a save to load:",
                "Load Game",
                JOptionPane.QUESTION_MESSAGE,
                null,
                saveDescriptions,
                saveDescriptions[0]);

        if (selected != null) {
            // Trouver la sauvegarde sélectionnée
            int selectedIndex = java.util.Arrays.asList(saveDescriptions).indexOf(selected);
            if (selectedIndex >= 0 && selectedIndex < saves.size()) {
                SaveInfo selectedSave = saves.get(selectedIndex);
                loadGameFromSave(selectedSave);
            }
        }
    }

    /**
     * Méthode pour analyser un fichier de sauvegarde
     */
    private SaveInfo parseSaveFile(Path filePath) {
        try {
            List<String> lines = Files.readAllLines(filePath, java.nio.charset.StandardCharsets.UTF_8);

            String saveName = null;
            int playerId = -1;
            String saveTime = null;
            String gameData = null;

            // Analyser les métadonnées
            for (String line : lines) {
                if (line.startsWith("SaveName: ")) {
                    saveName = line.substring("SaveName: ".length()).trim();
                } else if (line.startsWith("PlayerID: ")) {
                    try {
                        playerId = Integer.parseInt(line.substring("PlayerID: ".length()).trim());
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid PlayerID format in " + filePath);
                    }
                } else if (line.startsWith("SaveTime: ")) {
                    saveTime = line.substring("SaveTime: ".length()).trim();
                } else if (!line.startsWith("#") && !line.contains("SaveName:") &&
                          !line.contains("PlayerID:") && !line.contains("SaveTime:") &&
                          !line.contains("GameVersion:") && !line.contains("FileSize:") &&
                          !line.contains("Checksum:") && !line.contains("CurrentPlayer:") &&
                          !line.contains("GamePhase:") && !line.trim().isEmpty()) {
                    // Cette ligne contient les données du jeu
                    gameData = line.trim();
                    break;
                }
            }

            // Vérifier que toutes les données requises sont présentes
            if (saveName != null && playerId != -1 && saveTime != null && gameData != null) {
                long fileSize = Files.size(filePath);
                return new SaveInfo(saveName, playerId, saveTime, fileSize, gameData, filePath);
            } else {
                System.err.println("Incomplete save file: " + filePath);
                return null;
            }

        } catch (IOException e) {
            System.err.println("Error reading save file: " + filePath + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Méthode pour formater le temps de sauvegarde
     */
    private String formatSaveTime(String saveTime) {
        try {
            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(saveTime);
            java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return dateTime.format(formatter);
        } catch (Exception e) {
            // Si le parsing échoue, retourner le format original
            return saveTime;
        }
    }

    /**
     * Charge le jeu depuis une sauvegarde sélectionnée
     */
    private void loadGameFromSave(SaveInfo saveInfo) {
        System.out.println("Loading save: " + saveInfo.saveName);
        System.out.println("Game data: " + saveInfo.gameData);

        transitioningToGameScene = true;
        statusMessage = "Loading saved game...";
        repaintPanel();

        try {
            // Créer une GameScene avec les données de sauvegarde
            GameScene gameScene = new GameScene(sceneManager, true, levelAI);

            // Charger les données de sauvegarde dans la GameScene
            if (gameScene.loadFromSaveData(saveInfo.gameData)) {
                sceneManager.setScene(gameScene);
                System.out.println("Game loaded successfully");
            } else {
                throw new Exception("Failed to load game data");
            }

        } catch (Exception e) {
            System.err.println("Error loading save: " + e.getMessage());
            e.printStackTrace();

            JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "Error loading save: " + e.getMessage(),
                    "Load Error", JOptionPane.ERROR_MESSAGE);

            transitioningToGameScene = false;
            statusMessage = "Press Start to begin the game";
            repaintPanel();
        }
    }

    /**
     * Nettoie les ressources et retourne au menu principal
     */
    private void cleanUpAndGoToMenu() {
        System.out.println("SinglePlayerLobbyScene: cleanUpAndGoToMenu called. transitioningToGameScene = " + transitioningToGameScene);
        if (!transitioningToGameScene) {
            System.out.println("SinglePlayerLobbyScene: Stopping GameServerManager.");
        } else if (transitioningToGameScene) {
            System.out.println("SinglePlayerLobbyScene: GameServerManager will be managed by GameScene.");
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
        loadGameButton.setEnabled(true);
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
                loadGameButton.update(mousePos);
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
            String displayMessage = statusMessage;
            if (transitioningToGameScene) {
                displayMessage += ".".repeat(animationDots);
            }
            int statusWidth = statusMetrics.stringWidth(displayMessage);
            g2d.drawString(displayMessage, (width - statusWidth) / 2, height * 2 / 3 + 40);
        }

        // Paramètres du bouton
        int btnWidth = width / 4;
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
        startGameButton.setLocation(width / 2 - btnWidth / 2, height * 3 / 4);
        startGameButton.setFont(commonBtnFont);

        // Bouton de chargement de partie
        loadGameButton.setSize(btnWidth, btnHeight);
        loadGameButton.setLocation(width / 2 - btnWidth / 2, height * 3 / 4 + btnHeight + 10);
        loadGameButton.setFont(commonBtnFont);

        // Bouton de retour
        backButton.setSize(btnWidth * 3 / 4, btnHeight * 3 / 4);
        backButton.setLocation(40, height - btnHeight * 3 / 4 - 25);
        backButton.setFont(new Font("Arial", Font.PLAIN, Math.max(12, btnFontSize * 3 / 4)));

        // Rendu des boutons
        startGameButton.render(g2d);
        loadGameButton.render(g2d);
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

        // Affichage de l'ID du joueur
        g2d.setColor(Color.WHITE);
        String idText = isHost ? "ID: 1 (Lemiel)" : "ID: 2 (Zarek)";
        FontMetrics idMetrics = g2d.getFontMetrics();
        int idWidth = idMetrics.stringWidth(idText);
        g2d.drawString(idText, x + (width - idWidth) / 2, y + fontSize * 6);
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
                } else if (loadGameButton.contains(mousePoint)) {
                    loadGameButton.onClick();
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
                } else if (loadGameButton.isEnabled() && loadGameButton.contains(mousePoint)) {
                    loadGameButton.setClicked(true);
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
                loadGameButton.setClicked(false);
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
        System.out.println("SinglePlayerLobbyScene: transitioningToGameScene set to " + transitioning);
    }

    /**
     * Libération des ressources
     */
    @Override
    public void dispose() {
        clearMouseListeners();
        System.out.println("SinglePlayerLobbyScene: Dispose called. transitioningToGameScene = " + transitioningToGameScene);

        // Suppression du menu déroulant de sélection de difficulté
        if (sceneManager.getPanel() != null && difficultyComboBox != null) {
            sceneManager.getPanel().remove(difficultyComboBox);
        }
        difficultyComboBox = null;
        System.out.println("SinglePlayerLobbyScene: Dispose completed.");
    }
}