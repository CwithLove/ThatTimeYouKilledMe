package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import Network.GameClient;
import Network.GameServerManager;
import Network.GameStateUpdateListener;
import Modele.Jeu; // Nécessaire pour l'implémentation de GameStateUpdateListener

/**
 * HostingScene est la scène où l'hôte attend qu'un autre joueur se connecte
 * pour démarrer une partie multijoueur. Elle gère le démarrage du serveur,
 * la connexion du client de l'hôte lui-même, et attend la connexion du deuxième joueur.
 */
public class HostingScene implements Scene, GameStateUpdateListener {

    private SceneManager sceneManager;
    private GameServerManager gameServerManager; // Gère le serveur de jeu (démarrage, arrêt, connexions).
    private Button startGameButton;             // Bouton pour lancer la partie une fois les conditions remplies.
    private Button backButton;                  // Bouton pour retourner à la scène précédente (MultiplayerScene).

    private GameClient hostClient;              // Le client de l'hôte, qui se connecte à son propre serveur.
    private volatile boolean serverSuccessfullyStarted = false; // Indique si le GameServerManager a démarré correctement.
    private volatile boolean playerTwoConfirmedConnected = false; // Indique si le Joueur 2 s'est connecté (via callback).
    private volatile boolean hostClientConnected = false;     // Indique si le client de l'hôte lui-même est connecté.

    private MouseAdapter mouseAdapterInternal;  // Adaptateur pour les événements de la souris.
    private long startTime;                     // Pour l'animation de fondu (fade-in).
    private float alpha = 0f;                   // Niveau d'opacité pour le fondu.
    private boolean fadeComplete = false;       // Indique si le fondu est terminé.
    private String hostIP = "Obtention de l'IP..."; // Adresse IP de l'hôte à afficher.
    private int animationDots = 0;              // Pour l'animation des points ("En attente...").
    private long lastDotTime = 0;               // Pour cadencer l'animation des points.
    private String statusMessage = "Initialisation..."; // Message d'état affiché à l'utilisateur.

    // Drapeau pour gérer correctement la ressource gameServerManager lors du passage à GameScene.
    private boolean transitioningToGameScene = false;

    /**
     * Constructeur de HostingScene.
     * @param sceneManager Le gestionnaire de scènes principal.
     */
    public HostingScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.transitioningToGameScene = false; // Initialise le drapeau de transition.

        // Configuration du bouton "Lancer la partie"
        startGameButton = new Button(300, 400, 200, 50, "Lancer la partie", () -> {
            // Conditions pour lancer la partie : serveur démarré, joueur 2 connecté, client hôte connecté.
            if (serverSuccessfullyStarted && playerTwoConfirmedConnected && hostClientConnected) {
                System.out.println("HostingScene: Tentative de lancement du jeu...");
                if (gameServerManager != null && gameServerManager.areAllPlayersConnected()) {
                    // Désactive le bouton pour éviter les clics multiples pendant le chargement.
                    startGameButton.setEnabled(false);
                    statusMessage = "Démarrage du jeu en cours...";
                    repaintPanel();

                    // Utilise SwingWorker pour démarrer le moteur de jeu et passer à GameScene
                    // afin de ne pas bloquer l'EDT.
                    SwingWorker<Void, String> gameStarterWorker = new SwingWorker<Void, String>() {
                        @Override
                        protected Void doInBackground() throws Exception {
                            try {
                                publish("Initialisation du moteur de jeu...");
                                gameServerManager.startGameEngine(); // Démarre la logique du jeu sur le serveur.

                                // Pause pour laisser le temps au moteur de jeu de s'initialiser complètement
                                // et pour que l'état initial du jeu soit potentiellement envoyé.
                                Thread.sleep(800); // Ajuster cette valeur si nécessaire.

                                // Vérifications cruciales avant de passer à GameScene.
                                if (hostClient == null || !hostClient.isConnected()) {
                                    throw new Exception("Client hôte non connecté ou invalide avant transition.");
                                }
                                // S'assurer que GameClient a reçu le premier état du jeu peut être utile,
                                // mais GameScene peut aussi gérer l'attente de cet état.
                                if (hostClient.getGameInstance() == null) { // Vérifie si Jeu a été initialisé dans hostClient
                                    publish("Attente de l'état initial du jeu pour le client hôte...");
                                    for (int i = 0; i < 10; i++) { // Tente pendant 2 secondes max
                                        Thread.sleep(200);
                                        if (hostClient.getGameInstance() != null && hostClient.getGameInstance().getJoueurCourant() != null) {
                                            System.out.println("HostingScene: État de jeu initial reçu par le client hôte.");
                                            break;
                                        }
                                    }
                                    if (hostClient.getGameInstance() == null || hostClient.getGameInstance().getJoueurCourant() == null){
                                        // Peut être normal si le serveur n'envoie pas d'état avant que le jeu ne commence vraiment.
                                        System.out.println("HostingScene: Pas d'état de jeu initial reçu par client hôte, passage à GameScene quand même.");
                                    }
                                }
                                return null;
                            } catch (Exception e) {
                                System.err.println("HostingScene: Erreur dans gameStarterWorker doInBackground: " + e.getMessage());
                                e.printStackTrace();
                                throw e; // Relance pour que done() puisse le traiter.
                            }
                        }

                        @Override
                        protected void process(java.util.List<String> chunks) {
                            // Met à jour le message de statut sur l'EDT.
                            if (!chunks.isEmpty()) {
                                statusMessage = chunks.get(chunks.size() - 1);
                                repaintPanel();
                            }
                        }

                        @Override
                        protected void done() {
                            try {
                                get(); // Pour attraper les exceptions de doInBackground.
                                if (hostClient != null && hostClient.isConnected()) {
                                    System.out.println("HostingScene: Préparation de la transition vers GameScene. Client hôte ID: " + hostClient.getMyPlayerId());
                                    setTransitioningToGameScene(true); // Informe que nous allons transférer la gestion.

                                    // Change de scène sur l'EDT.
                                    SwingUtilities.invokeLater(() -> {
                                        try {
                                            // GameScene prendra en charge le GameServerManager et le hostClient.
                                            GameScene gameScene = new GameScene(sceneManager, hostClient, gameServerManager);
                                            System.out.println("HostingScene: GameScene créée, gameServerManager transféré.");
                                            // HostingScene ne gère plus gameServerManager.
                                            HostingScene.this.gameServerManager = null;
                                            sceneManager.setScene(gameScene);
                                            System.out.println("HostingScene: Transition vers GameScene terminée.");
                                        } catch (Exception e_scene) {
                                            // Erreur lors de la création ou de l'init de GameScene.
                                            System.err.println("HostingScene: Erreur critique lors de la création/transition vers GameScene: " + e_scene.getMessage());
                                            e_scene.printStackTrace();
                                            statusMessage = "Erreur critique: " + e_scene.getMessage();
                                            JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur Critique", JOptionPane.ERROR_MESSAGE);
                                            startGameButton.setEnabled(true); // Réactive le bouton en cas d'erreur.
                                            setTransitioningToGameScene(false); // Annule la transition.
                                            repaintPanel();
                                        }
                                    });
                                } else {
                                    throw new Exception("Client hôte déconnecté ou non initialisé avant le passage à GameScene.");
                                }
                            } catch (Exception e) {
                                // Erreur attrapée depuis doInBackground ou une nouvelle exception.
                                System.err.println("HostingScene: Erreur dans gameStarterWorker done(): " + e.getMessage());
                                statusMessage = "Échec du lancement du jeu: " + e.getMessage();
                                JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur de Lancement", JOptionPane.ERROR_MESSAGE);
                                startGameButton.setEnabled(true); // Réactive le bouton.
                                repaintPanel();
                            }
                        }
                    };
                    gameStarterWorker.execute(); // Exécute le SwingWorker.
                } else {
                    statusMessage = "Attente: Tous les joueurs ne sont pas encore prêts.";
                    startGameButton.setEnabled(true); // Réactive si les conditions ne sont pas remplies (devrait être rare ici).
                    repaintPanel();
                }
            } else {
                 // Log si les conditions de base ne sont pas remplies.
                 System.out.println("HostingScene: Conditions de lancement non remplies (Serveur: " + serverSuccessfullyStarted +
                                    ", Joueur2: " + playerTwoConfirmedConnected + ", ClientHôte: " + hostClientConnected +")");
                 startGameButton.setEnabled(true); // Assure que le bouton est réactivable si l'utilisateur a cliqué trop tôt.
            }
        });

        // Configuration du bouton "Retour"
        backButton = new Button(50, 500, 150, 40, "Retour", this::cleanUpAndGoToMenu);

        // Tente d'obtenir l'adresse IP locale pour l'afficher.
        try {
            hostIP = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            hostIP = "IP Inconnue";
            System.err.println("HostingScene: Erreur lors de l'obtention de l'adresse IP: " + e.getMessage());
        }
    }


    /**
     * Gère les erreurs de connexion du client hôte.
     * Inutilisé si la logique de connexion est entièrement dans initWorker.
     * @param e L'exception d'entrée/sortie.
     */
    private void handleHostClientConnectionError(IOException e) {
        System.err.println("HostingScene: Erreur de connexion du client hôte: " + e.getMessage());
        e.printStackTrace(); // Utile pour le débogage.
        statusMessage = "Erreur client hôte: " + e.getMessage();
        JOptionPane.showMessageDialog(sceneManager.getPanel(),
                "Erreur de connexion pour le client hôte : " + e.getMessage(),
                "Erreur Hôte", JOptionPane.ERROR_MESSAGE);
        repaintPanel();
        // Réactive le bouton de démarrage pour permettre une nouvelle tentative ou un retour.
        if (startGameButton != null) startGameButton.setEnabled(true);
    }

    /**
     * Nettoie les ressources (serveur, client) et retourne à la scène multijoueur.
     * Appelé par le bouton "Retour".
     */
    private void cleanUpAndGoToMenu() {
        System.out.println("HostingScene: cleanUpAndGoToMenu appelé. transitioningToGameScene = " + transitioningToGameScene);
        // Arrête le serveur seulement si nous ne sommes PAS en train de passer à GameScene.
        // Si transitioningToGameScene est vrai, GameScene prendra la responsabilité du gameServerManager.
        if (!transitioningToGameScene && gameServerManager != null) {
            System.out.println("HostingScene: Arrêt du GameServerManager.");
            gameServerManager.stopServer();
            gameServerManager = null; // Laisser le GC le récupérer
        } else if (transitioningToGameScene) {
             System.out.println("HostingScene: GameServerManager sera géré par GameScene.");
        }

        if (hostClient != null) {
            System.out.println("HostingScene: Déconnexion du hostClient.");
            hostClient.disconnect();
            hostClient = null;
        }
        sceneManager.setScene(new MultiplayerScene(sceneManager));
    }

    /**
     * Initialise la scène. Démarrage du serveur et connexion du client hôte en arrière-plan.
     */
    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        alpha = 0f;
        fadeComplete = false;
        playerTwoConfirmedConnected = false;
        serverSuccessfullyStarted = false;
        hostClientConnected = false;
        transitioningToGameScene = false; // Réinitialise à chaque init
        statusMessage = "Démarrage du serveur hôte...";
        if(startGameButton != null) startGameButton.setEnabled(true); // S'assurer que le bouton est actif à l'init

        setupMouseListeners(); // Configure les écouteurs de souris.

        // Utilise SwingWorker pour les opérations de réseau et de démarrage du serveur.
        SwingWorker<Void, String> initWorker = new SwingWorker<Void, String>() {
            @Override
            protected Void doInBackground() throws Exception {
                // Étape 1: Démarrer le GameServerManager.
                publish("Démarrage du serveur...");
                if (gameServerManager != null && gameServerManager.isServerRunning()){
                    // Si un serveur est déjà là (d'une instance précédente non nettoyée?), l'arrêter.
                    // Ceci est une mesure de sécurité, normalement dispose() devrait s'en charger.
                    System.out.println("HostingScene: Un serveur existant a été trouvé, tentative d'arrêt.");
                    gameServerManager.stopServer();
                }
                gameServerManager = new GameServerManager(HostingScene.this); // 'this' est le callback.
                try {
                    gameServerManager.startServer();
                    serverSuccessfullyStarted = true;
                    publish("Serveur démarré sur IP: " + hostIP + ". Connexion de l'hôte...");
                    System.out.println("HostingScene: Serveur démarré avec succès.");

                    // Petite pause pour s'assurer que le socket serveur est bien ouvert.
                    Thread.sleep(200);

                    // Étape 2: L'hôte se connecte à son propre serveur.
                    hostClient = new GameClient("127.0.0.1", HostingScene.this); // Utilise "127.0.0.1"
                    hostClient.connect(); // Tente de se connecter.
                    hostClientConnected = true; // Suppose la connexion réussie, GameClient gère les erreurs.
                    System.out.println("HostingScene: Client hôte (ID: " + hostClient.getMyPlayerId() + ") connecté. En attente Joueur 2.");
                    publish("Hôte (Joueur 1) connecté! En attente du Joueur 2...");

                } catch (IOException e) {
                    serverSuccessfullyStarted = false; // Échec du démarrage du serveur ou de la connexion du client hôte.
                    hostClientConnected = false;
                    System.err.println("HostingScene: Erreur critique pendant l'initialisation (serveur/client hôte): " + e.getMessage());
                    e.printStackTrace();
                    publish("Erreur Init: " + e.getMessage());
                    // Affiche l'erreur sur l'EDT via done() ou un mécanisme de callback si nécessaire.
                    throw e; // Relance pour que done() puisse le gérer.
                }
                return null;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    statusMessage = chunks.get(chunks.size() - 1);
                    repaintPanel();
                }
            }

            @Override
            protected void done() {
                try {
                    get(); // Vérifie les exceptions de doInBackground.
                    if (!serverSuccessfullyStarted) {
                        statusMessage = "Échec du démarrage du serveur.";
                        JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur Serveur", JOptionPane.ERROR_MESSAGE);
                    } else if (!hostClientConnected) {
                        statusMessage = "Serveur démarré, mais échec connexion client hôte.";
                         JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur Client Hôte", JOptionPane.ERROR_MESSAGE);
                    }
                    // statusMessage final sera "Hôte connecté! En attente du Joueur 2..." si tout va bien.
                } catch (Exception e) {
                    // Attrapé depuis doInBackground.
                    statusMessage = "Erreur initialisation: " + e.getMessage();
                     JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur Init", JOptionPane.ERROR_MESSAGE);
                }
                repaintPanel();
            }
        };
        initWorker.execute(); // Lance le worker.
        lastDotTime = startTime; // Pour l'animation des points.
        repaintPanel(); // Rafraîchit l'UI initiale.
    }

    /**
     * Callback appelé par GameServerManager lorsque le deuxième joueur s'est connecté.
     */
    public void onPlayerTwoConnected() {
        SwingUtilities.invokeLater(() -> { // Assure l'exécution sur l'EDT.
            if (!playerTwoConfirmedConnected) {
                playerTwoConfirmedConnected = true;
                statusMessage = "Joueur 2 connecté! Prêt à lancer la partie.";
                System.out.println("HostingScene: Joueur 2 confirmé connecté.");
                repaintPanel();
            }
        });
    }

    /**
     * Met à jour la logique de la scène (animation de fondu, points d'attente, état des boutons).
     */
    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1.0f, elapsed / 1000.0f);
            if (alpha >= 1.0f) fadeComplete = true;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDotTime > 500) { // Met à jour l'animation des points toutes les 0.5s.
            animationDots = (animationDots + 1) % 4; // Cycle de 0 à 3 points.
            lastDotTime = currentTime;
        }

        // Met à jour l'état de survol des boutons.
        if (fadeComplete && sceneManager.getPanel() != null) {
            Point mousePos = sceneManager.getPanel().getMousePosition();
            if (mousePos != null) {
                // Le bouton "Lancer la partie" est actif seulement si toutes les conditions sont remplies.
                boolean canStartGame = serverSuccessfullyStarted && playerTwoConfirmedConnected && hostClientConnected && startGameButton.isEnabled();
                if (canStartGame) {
                    startGameButton.update(mousePos);
                } else {
                    // Force l'état non-survolé si le bouton n'est pas réellement interactif.
                    if (startGameButton.contains(mousePos)) startGameButton.update(new Point(-1, -1)); // Point hors bouton.
                }
                backButton.update(mousePos);
                // repaintPanel(); // Peut causer des rafraîchissements excessifs, render s'en charge.
            }
        }
    }

    /**
     * Dessine la scène.
     * @param g L'objet Graphics pour dessiner.
     * @param width Largeur de la zone de dessin.
     * @param height Hauteur de la zone de dessin.
     */
    @Override
    public void render(Graphics g, int width, int height) {
        // Dessin de l'arrière-plan.
        g.setColor(new Color(40, 40, 80)); // Bleu-gris foncé.
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)); // Applique le fondu.

        // Titre de la scène.
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 18;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String titleText = "Salle d'Attente de l'Hôte";
        FontMetrics titleMetricsFont = g2d.getFontMetrics(); // Utiliser titleMetricsFont pour éviter conflit
        int titleTextWidth = titleMetricsFont.stringWidth(titleText);
        g2d.drawString(titleText, (width - titleTextWidth) / 2, height / 7);

        // Informations (IP, état du joueur 2).
        int infoFontSize = Math.min(width, height) / 28;
        g2d.setFont(new Font("Arial", Font.PLAIN, infoFontSize));
        int currentY = height / 4;

        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString("Votre IP (pour le Joueur 2): " + hostIP, width / 10, currentY);
        currentY += infoFontSize + 10;

        // État du client hôte (Joueur 1)
        String hostClientStatusText;
        if (hostClientConnected) {
            hostClientStatusText = "Hôte (Joueur 1): Connecté (ID " + (hostClient != null ? hostClient.getMyPlayerId() : "?") + ")";
            g2d.setColor(Color.CYAN);
        } else if (serverSuccessfullyStarted) {
            hostClientStatusText = "Hôte (Joueur 1): Connexion en cours...";
            g2d.setColor(Color.YELLOW);
        } else {
            hostClientStatusText = "Hôte (Joueur 1): (Serveur non prêt)";
            g2d.setColor(Color.ORANGE);
        }
        g2d.drawString(hostClientStatusText, width / 10, currentY);
        currentY += infoFontSize + 15;


        // État du Joueur 2.
        String player2StatusText;
        if (playerTwoConfirmedConnected) {
            player2StatusText = "Joueur 2: Connecté !";
            g2d.setColor(Color.GREEN);
        } else if (serverSuccessfullyStarted && hostClientConnected) { // Attend joueur 2 seulement si serveur et hôte OK
            String dots = "";
            for (int i = 0; i < animationDots; i++) dots += ".";
            player2StatusText = "Joueur 2: En attente de connexion" + dots;
            g2d.setColor(Color.YELLOW);
        } else {
            player2StatusText = "Joueur 2: (En attente de la configuration du serveur/hôte)";
            g2d.setColor(Color.ORANGE);
        }
        g2d.drawString(player2StatusText, width / 10, currentY);
        g2d.setColor(Color.WHITE); // Réinitialise la couleur.

        // Message de statut général.
        if (statusMessage != null && !statusMessage.isEmpty()) {
            g2d.setFont(new Font("Arial", Font.ITALIC, Math.max(12, infoFontSize * 3 / 4)));
            FontMetrics statusMetrics = g2d.getFontMetrics();
            int statusWidth = statusMetrics.stringWidth(statusMessage);
            g2d.drawString(statusMessage, (width - statusWidth) / 2, height * 4 / 7); // Positionné plus bas.
        }

        // Configuration et dessin des boutons.
        int btnWidth = Math.max(180, width / 4);
        int btnHeight = Math.max(45, height / 13);
        int btnFontSize = Math.min(width, height) / 35;
        Font commonBtnFont = new Font("Arial", Font.BOLD, btnFontSize);

        startGameButton.setSize(btnWidth, btnHeight);
        startGameButton.setLocation(width / 2 - btnWidth / 2, height * 3 / 4); // Positionné plus bas.
        startGameButton.setFont(commonBtnFont);

        backButton.setSize(Math.max(120, btnWidth * 3/4), Math.max(35,btnHeight * 3/4));
        backButton.setLocation(40, height - Math.max(35,btnHeight * 3/4) - 25); // En bas à gauche.
        backButton.setFont(new Font("Arial", Font.PLAIN, Math.max(12,btnFontSize * 3/4)));

        // Active ou désactive le bouton "Lancer la partie" en fonction de l'état.
        startGameButton.setEnabled(serverSuccessfullyStarted && playerTwoConfirmedConnected && hostClientConnected && !transitioningToGameScene);
        startGameButton.render(g2d); // La méthode render du bouton gère l'apparence si désactivé.

        backButton.render(g2d);
        g2d.dispose();
    }

    /**
     * Configure les écouteurs d'événements de la souris.
     */
    private void setupMouseListeners() {
        clearMouseListeners(); // Nettoie les anciens listeners.
        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!fadeComplete || transitioningToGameScene) return; // Ignore les clics pendant le fondu ou la transition.
                Point mousePoint = e.getPoint();
                // Le bouton "Lancer la partie" est vérifié avec son état 'enabled' dans son propre onClick.
                if (startGameButton.contains(mousePoint)) {
                    startGameButton.onClick();
                } else if (backButton.contains(mousePoint)) {
                    backButton.onClick();
                }
            }
            @Override
            public void mousePressed(MouseEvent e) {
                if (!fadeComplete || transitioningToGameScene) return;
                Point mousePoint = e.getPoint();
                if (startGameButton.isEnabled() && startGameButton.contains(mousePoint)) startGameButton.setClicked(true);
                else if (backButton.isEnabled() && backButton.contains(mousePoint)) backButton.setClicked(true);
                repaintPanel();
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if(transitioningToGameScene) return;
                startGameButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel();
            }
            // mouseMoved est géré dans update() pour le survol.
        };
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal); // Pour Button.update() dans update()
        }
    }

    /**
     * Nettoie les écouteurs de souris du panneau.
     */
    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
            // mouseAdapterInternal = null; // Peut aider le GC, mais setupMouseListeners crée un nouveau.
        }
    }

    /**
     * Demande au panneau de se redessiner.
     */
    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }

    /**
     * Définit si la scène est en train de passer à GameScene.
     * Utilisé par dispose() pour savoir s'il faut arrêter le serveur.
     * @param transitioning vrai si une transition est en cours.
     */
    public void setTransitioningToGameScene(boolean transitioning) {
        this.transitioningToGameScene = transitioning;
        System.out.println("HostingScene: transitioningToGameScene mis à " + transitioning);
    }

    /**
     * Libère les ressources lorsque la scène est détruite.
     */
    @Override
    public void dispose() {
        clearMouseListeners();
        System.out.println("HostingScene: Dispose appelé. transitioningToGameScene = " + transitioningToGameScene);

        // Si nous ne passons PAS à GameScene (ex: retour au menu), alors arrêtez le serveur et le client hôte.
        // Si nous passons à GameScene, GameScene prendra la relève de la gestion du serveur.
        if (!transitioningToGameScene) {
            System.out.println("HostingScene: Nettoyage car pas de transition vers GameScene.");
            if (hostClient != null) {
                hostClient.disconnect();
                hostClient = null;
            }
            if (gameServerManager != null) {
                gameServerManager.stopServer();
                gameServerManager = null;
            }
        } else {
            System.out.println("HostingScene: Transition vers GameScene, gameServerManager et hostClient ne sont pas nettoyés ici.");
            // gameServerManager sera nullifié dans le SwingWorker de lancement du jeu si la transition réussit.
            // hostClient sera utilisé par GameScene.
        }
        // transitioningToGameScene devrait être réinitialisé si la scène est réutilisée.
        // this.transitioningToGameScene = false; // Déjà fait dans init()
        System.out.println("HostingScene: Fin de dispose.");
    }

    // Implémentation de GameStateUpdateListener (pour le client de l'hôte lui-même)

    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        // Normalement, HostingScene ne devrait pas recevoir beaucoup de mises à jour d'état de jeu,
        // car elle passe rapidement à GameScene une fois le jeu lancé.
        // Ceci est surtout pour les messages initiaux ou les erreurs de connexion du client hôte.
        if (hostClient != null && hostClient.getGameInstance() == newGameState) { // Vérifie si c'est bien le jeu du client hôte
             System.out.println("HostingScene (listener): État du jeu reçu pour client hôte (ID: " + hostClient.getMyPlayerId() + ")");
        }
    }

    @Override
    public void onGameMessage(String messageType, String messageContent) {
        SwingUtilities.invokeLater(() -> { // Assure l'exécution sur l'EDT.
            System.out.println("HostingScene (listener): Message pour client hôte - Type: " + messageType + ", Contenu: " + messageContent);
            // Affiche les erreurs critiques du client hôte.
            if ("ERROR".equalsIgnoreCase(messageType) || "DISCONNECTED".equalsIgnoreCase(messageType)) {
                // Si le client de l'hôte lui-même se déconnecte ou a une erreur, c'est un problème.
                statusMessage = "Client Hôte: " + messageType + " - " + messageContent;
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                        "Problème avec la connexion interne de l'hôte: " + messageContent,
                        "Erreur Client Hôte", JOptionPane.ERROR_MESSAGE);
                // Pourrait envisager d'arrêter le serveur ici ou de réinitialiser la scène.
                // serverSuccessfullyStarted = false; // Indiquer que la configuration est maintenant invalide.
                // hostClientConnected = false;
                // startGameButton.setEnabled(false);
                repaintPanel();
            }
        });
    }
}