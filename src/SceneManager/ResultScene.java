package SceneManager;

import Network.GameClient;
import Network.GameServerManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Scène affichant le résultat d'une partie terminée.
 * Cette scène montre le vainqueur et propose des options pour rejouer ou revenir au menu.
 *
 * Cette implémentation suit le même modèle que MenuScene avec un effet de fondu
 * et une gestion correcte des événements de souris.
 */
public class ResultScene implements Scene {

    private SceneManager sceneManager;
    private String winnerMessage;
    private int winnerId; // 1 pour Lemiel, 2 pour Zarek

    // Ressources graphiques
    private BufferedImage backgroundImage;
    private BufferedImage lemielVictoryImage;
    private BufferedImage zarekVictoryImage;

    // Animation de fondu à l'entrée (comme dans MenuScene)
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    private final long FADE_DURATION = 800; // Durée du fondu en millisecondes

    // Animation de pulsation
    private long animationStartTime;

    // Boutons d'interface
    private Button menuButton;
    private Button lobbyButton;

    // Gestion des interactions souris
    private MouseAdapter mouseAdapterInternal;

    // Référence au client de jeu (pour retourner au lobby si connecté)
    private GameClient gameClient;
    private GameServerManager serverManager;
    private boolean canReturnToLobby;
    
    // Flag pour identifier si c'est un mode solo contre IA
    private boolean isSinglePlayerMode;

    /**
     * Constructeur pour un résultat de jeu sans connexion réseau.
     * @param sceneManager Le gestionnaire de scènes
     * @param winnerId L'ID du joueur gagnant (1 pour Lemiel, 2 pour Zarek)
     * @param winnerMessage Le message de victoire à afficher
     */
    public ResultScene(SceneManager sceneManager, int winnerId, String winnerMessage) {
        this.sceneManager = sceneManager;
        this.winnerId = winnerId;
        this.winnerMessage = winnerMessage;
        this.canReturnToLobby = false;
        this.isSinglePlayerMode = false;

        loadResources();
        initButtons();
        System.out.println("ResultScene: Constructeur appelé (mode solo)");
    }

    /**
     * Constructeur pour un résultat de jeu avec connexion réseau, spécifiant s'il s'agit d'un mode solo.
     * @param sceneManager Le gestionnaire de scènes
     * @param winnerId L'ID du joueur gagnant (1 pour Lemiel, 2 pour Zarek)
     * @param winnerMessage Le message de victoire à afficher
     * @param gameClient Le client de jeu connecté
     * @param serverManager Le gestionnaire de serveur (si c'est l'hôte)
     * @param isSinglePlayerMode True si c'est un mode solo (IA), false si c'est multijoueur
     */
    public ResultScene(SceneManager sceneManager, int winnerId, String winnerMessage,
                      GameClient gameClient, GameServerManager serverManager, boolean isSinglePlayerMode) {
        this.sceneManager = sceneManager;
        this.winnerId = winnerId;
        this.winnerMessage = winnerMessage;
        this.gameClient = gameClient;
        this.serverManager = serverManager;
        this.canReturnToLobby = gameClient != null && gameClient.isConnected();
        this.isSinglePlayerMode = isSinglePlayerMode;

        loadResources();
        initButtons();
        System.out.println("ResultScene: Constructeur appelé (mode " + (isSinglePlayerMode ? "solo contre IA" : "multijoueur") + ")");
    }

    /**
     * Charge les ressources graphiques nécessaires.
     */
    private void loadResources() {
        try {
            backgroundImage = ImageIO.read(new File("res/Background/Background.png"));
                // Si l'image de victoire n'existe pas, essayer l'avatar
            try {
                    lemielVictoryImage = ImageIO.read(new File("res/Character/Lemiel/Lemiel_Avatar.png"));
                } catch (IOException e2) {
                    System.out.println("Aucune image pour Lemiel trouvée");
                }



                // Si l'image de victoire n'existe pas, essayer l'avatar
            try {
                    zarekVictoryImage = ImageIO.read(new File("res/Character/Zarek/Zarek_Avatar.png"));
                } catch (IOException e2) {
                    System.out.println("Aucune image pour Zarek trouvée");
                }


        } catch (IOException e) {
            System.err.println("Erreur lors du chargement des ressources: " + e.getMessage());
        }
    }

    /**
     * Initialise les boutons d'interface.
     */
    private void initButtons() {
        int buttonWidth = 200;
        int buttonHeight = 50;

        // Bouton pour retourner au menu principal
        menuButton = new Button(0, 0, buttonWidth, buttonHeight, "Menu Principal", this::returnToMainMenu);
        menuButton.setFont(new Font("Arial", Font.BOLD, 18));


        // Bouton pour retourner au lobby (uniquement si connecté)
        lobbyButton = new Button(0, 0, buttonWidth, buttonHeight, "Retour au Lobby", this::returnToLobby);
        lobbyButton.setFont(new Font("Arial", Font.BOLD, 18));
        lobbyButton.setEnabled(canReturnToLobby);
    }

    @Override
    public void init() {
        System.out.println("ResultScene: Méthode init appelée");

        // Initialiser les variables d'animation
        startTime = System.currentTimeMillis();
        animationStartTime = startTime;
        alpha = 0f;
        fadeComplete = false;

        System.out.println("ResultScene: isSinglePlayerMode = " + isSinglePlayerMode);
        // Configurer l'écouteur de souris
        setupMouseListeners();

        // Demander explicitement un repaint
        repaintPanel();
    }

    /**
     * Configure l'écouteur de souris pour les interactions avec les boutons.
     * Implémenté suivant le modèle de MenuScene.
     */
    private void setupMouseListeners() {
        clearMouseListeners(); // Supprimer les écouteurs existants pour éviter les doublons

        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) { // Traiter les clics seulement quand le fondu est terminé
                    Point mousePoint = e.getPoint();
                    System.out.println("ResultScene: Clic à " + mousePoint.x + "," + mousePoint.y);

                    if (menuButton.isEnabled() && menuButton.contains(mousePoint)) {
                        System.out.println("ResultScene: Clic sur Menu Principal");
                        menuButton.onClick();
                    } else if (lobbyButton.isEnabled() && lobbyButton.contains(mousePoint)) {
                        System.out.println("ResultScene: Clic sur Retour au Lobby");
                        lobbyButton.onClick();
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();

                    if (menuButton.isEnabled() && menuButton.contains(mousePoint)) {
                        menuButton.setClicked(true);
                    }  else if (lobbyButton.isEnabled() && lobbyButton.contains(mousePoint)) {
                        lobbyButton.setClicked(true);
                    }

                    repaintPanel();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Réinitialiser tous les boutons
                menuButton.setClicked(false);
                lobbyButton.setClicked(false);

                repaintPanel();
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();

                    menuButton.update(mousePoint);
                    lobbyButton.update(mousePoint);

                    repaintPanel();
                }
            }
        };

        // Ajouter l'adaptateur au panneau
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal);
            System.out.println("ResultScene: Écouteurs de souris configurés");
        } else {
            System.err.println("ResultScene: ERREUR - Panel null dans setupMouseListeners");
        }
    }

    /**
     * Supprime les écouteurs de souris existants.
     */
    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
        }
    }

    @Override
    public void update() {
        // Mise à jour de l'animation de fondu
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1.0f, (float)elapsed / FADE_DURATION);
            if (alpha >= 1.0f) {
                fadeComplete = true;
                System.out.println("ResultScene: Animation de fondu terminée");
            }
            repaintPanel();
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        //System.out.println("ResultScene: Méthode render appelée - largeur=" + width + ", hauteur=" + height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Dessiner l'arrière-plan
        if (backgroundImage != null) {
            g2d.drawImage(backgroundImage, 0, 0, width, height, null);
        } else {
            // Couleur de fond par défaut si l'image n'est pas disponible
            g2d.setColor(new Color(25, 25, 35));
            g2d.fillRect(0, 0, width, height);
        }

        // Appliquer l'effet de fondu
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Panneau central semi-transparent
        int panelWidth = width * 3 / 4;
        int panelHeight = height * 3 / 4;
        int panelX = (width - panelWidth) / 2;
        int panelY = (height - panelHeight) / 2;

        // Dessiner le panneau
        g2d.setColor(new Color(40, 40, 80, 200));
        g2d.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 20, 20);
        g2d.setColor(new Color(100, 100, 200, 200));
        g2d.setStroke(new BasicStroke(3f));
        g2d.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 20, 20);

        // Titre "Victoire!"
        g2d.setFont(new Font("Arial", Font.BOLD, 48));
        g2d.setColor(Color.YELLOW);
        String titleText;
        if(winnerId == 1){
            titleText = "Victoire pour le joueur 1!";
        }else{
            titleText = "Victoire pour le joueur 2!";
        }
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleWidth = titleMetrics.stringWidth(titleText);
        g2d.drawString(titleText, (width - titleWidth) / 2, panelY + panelHeight / 6);

        // Message de victoire
        g2d.setFont(new Font("Arial", Font.BOLD, 24));
        g2d.setColor(Color.WHITE);
        String[] lines = splitToLines(winnerMessage, panelWidth - 40, g2d.getFontMetrics());
        int lineHeight = g2d.getFontMetrics().getHeight();
        int textY = panelY + panelHeight / 4;

        for (String line : lines) {
            int lineWidth = g2d.getFontMetrics().stringWidth(line);
            g2d.drawString(line, (width - lineWidth) / 2, textY);
            textY += lineHeight;
        }

        // Afficher l'image du vainqueur avec effet de pulsation
        BufferedImage winnerImage = (winnerId == 1) ? lemielVictoryImage : zarekVictoryImage;
        if (winnerImage != null) {
            int imageSize = Math.min(panelWidth, panelHeight) / 3;
            int imageX = (width - imageSize) / 2;
            int imageY = panelY + panelHeight / 2;

            // Calculer la pulsation (similaire à MenuScene qui utilise System.currentTimeMillis())
            double pulseFactor = 1.0 + Math.sin(System.currentTimeMillis() / 300.0) * 0.05;
            int pulseSize = (int)(imageSize * pulseFactor);

            g2d.drawImage(winnerImage, imageX - (pulseSize - imageSize)/2,
                          imageY - (pulseSize - imageSize)/2, pulseSize, pulseSize, null);


        }

        // Positionner et dessiner les boutons
        int buttonsY = panelY + panelHeight * 3 / 4;
        int spacing = width / 20;

        // Positionnement des boutons
        if (canReturnToLobby) {
            // Trois boutons si le lobby est disponible
            menuButton.setLocation((width / 2) - spacing - menuButton.getWidth(), buttonsY);
            lobbyButton.setLocation((width - lobbyButton.getWidth()) / 2 + 60, buttonsY);
        } else {
            // Deux boutons sinon
            menuButton.setLocation((width / 2) - (menuButton.getWidth() / 2) - spacing, buttonsY);
            // Le bouton lobby est invisible ou désactivé
        }

        // Rendu des boutons
        menuButton.render(g2d);
        if (canReturnToLobby) {
            lobbyButton.render(g2d);
        }

        g2d.dispose();
    }

    @Override
    public void dispose() {
        System.out.println("ResultScene: Méthode dispose appelée");
        clearMouseListeners();

        // Ne pas libérer gameClient ou serverManager ici, car ils pourraient être réutilisés
    }

    /**
     * Action pour retourner au menu principal.
     */
    private void returnToMainMenu() {
        System.out.println("ResultScene: Retour au menu principal");
        // Nettoyer les connexions réseau si nécessaires
        if (gameClient != null) {
            gameClient.disconnect();
            gameClient = null;
        }

        if (serverManager != null) {
            serverManager.stopServer();
            serverManager = null;
        }

        // Transition vers le menu principal
        sceneManager.setScene(new MenuScene(sceneManager));
    }



    /**
     * Action pour retourner au lobby (uniquement en mode multijoueur).
     */
    private void returnToLobby() {
        System.out.println("ResultScene: Retour au lobby");
        if (!canReturnToLobby || gameClient == null) {
            System.out.println("ResultScene: Impossible de retourner au lobby - client null ou déconnecté");
            return;
        }

        // Sauvegarder les références pour les passer à la scène de lobby
        GameClient clientToTransfer = this.gameClient;
        GameServerManager serverToTransfer = this.serverManager;

        // Éviter de les libérer dans dispose()
        this.gameClient = null;
        this.serverManager = null;

        // Vérifier si c'est un mode solo (contre IA)
        if (isSinglePlayerMode) {
            this.serverManager.stopServer(); // Arrêter le serveur si c'est un mode solo
            this.serverManager = null; // Libérer la référence au serveur
            System.out.println("ResultScene: Mode solo détecté, retour à SinglePlayerLobbyScene");
            sceneManager.setScene(new SinglePlayerLobbyScene(sceneManager));
            return;
        }
        
        // Mode multijoueur normal - Déterminer la scène de lobby appropriée selon l'ID du joueur
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("ResultScene: ID du joueur: " + clientToTransfer.getMyPlayerId());
                if (clientToTransfer.getMyPlayerId() == 1) {
                    // Joueur 1 (hôte) - Retour à HostingScene
                    System.out.println("ResultScene: Transfert vers HostingScene");
                    HostingScene hostingScene = new HostingScene(sceneManager);
                    if (serverToTransfer != null && serverToTransfer.isServerRunning()) {
                        hostingScene.setExistingClient(clientToTransfer, serverToTransfer);
                    } else {
                        hostingScene.setExistingClient(clientToTransfer, null);
                    }
                    sceneManager.setScene(hostingScene);
                } else {
                    // Joueur 2 (client) - Retour à ClientLobbyScene
                    System.out.println("ResultScene: Transfert vers ClientLobbyScene");
                    ClientLobbyScene lobbyScene = new ClientLobbyScene(sceneManager, clientToTransfer);
                    sceneManager.setScene(lobbyScene);
                }
            } catch (Exception e) {
                System.err.println("ResultScene: Erreur lors du retour au lobby: " + e.getMessage());
                e.printStackTrace();
                // En cas d'erreur, retour au menu
                if (clientToTransfer != null) clientToTransfer.disconnect();
                if (serverToTransfer != null) serverToTransfer.stopServer();
                sceneManager.setScene(new MenuScene(sceneManager));
            }
        });
    }

    private String[] splitToLines(String text, int maxWidth, FontMetrics metrics) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }

        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();

        for (String word : words) {
            if (currentLine.length() > 0) {
                String lineWithWord = currentLine + " " + word;
                if (metrics.stringWidth(lineWithWord) <= maxWidth) {
                    currentLine.append(" ").append(word);
                } else {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                }
            } else {
                currentLine.append(word);
            }
        }

        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }


    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }
}