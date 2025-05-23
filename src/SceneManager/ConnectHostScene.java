package SceneManager;

import Modele.Jeu;
import Network.GameClient;
import Network.GameStateUpdateListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * ConnectHostScene représente la scène où l'utilisateur peut entrer l'adresse IP
 * d'un hôte pour rejoindre une partie multijoueur.
 * Cette scène sert uniquement à vérifier la connexion au serveur.
 * Après une connexion réussie, elle passe à ClientLobbyScene.
 */
public class ConnectHostScene implements Scene, GameStateUpdateListener {
    private SceneManager sceneManager;
    private Button connectButton;
    private Button backButton;
    private JTextField ipAddressField;
    private String statusMessage = "Entrez l'adresse IP de l'hôte.";

    private MouseAdapter mouseAdapterInternal;
    private long startTime;
    private float alpha = 0f;
    private boolean fadeComplete = false;
    
    private GameClient gameClient;
    private boolean isConnecting = false;
    
    // Timer pour vérifier périodiquement l'état de la connexion
    private javax.swing.Timer connectionCheckTimer;
    private static final int CONNECTION_CHECK_INTERVAL = 5000; // Vérification toutes les 5 secondes

    /**
     * Constructeur de la scène ConnectHostScene.
     * @param sceneManager Le gestionnaire de scènes.
     */
    public ConnectHostScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.ipAddressField = new JTextField("127.0.0.1");
        
        // Initialisation du timer de vérification de connexion
        connectionCheckTimer = new javax.swing.Timer(CONNECTION_CHECK_INTERVAL, e -> checkConnectionStatus());

        // Initialisation du bouton "Se connecter"
        connectButton = new Button(300, 300, 200, 50, "Se connecter", () -> {
            if (isConnecting) {
                statusMessage = "Connexion en cours, veuillez patienter...";
                repaintPanel();
                return;
            }
            
            String ip = ipAddressField.getText().trim();
            if (ip.isEmpty()) {
                statusMessage = "L'adresse IP ne peut pas être vide.";
                JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur de Saisie", JOptionPane.WARNING_MESSAGE);
                repaintPanel();
                return;
            }
            
            // Désactiver le bouton pendant la tentative de connexion
            connectButton.setEnabled(false);
            isConnecting = true;
            statusMessage = "Tentative de connexion à " + ip + "...";
            repaintPanel();
            
            // Créer un SwingWorker pour gérer la connexion en arrière-plan
            SwingWorker<Void, String> connectionWorker = new SwingWorker<Void, String>() {
                @Override
                protected Void doInBackground() throws Exception {
                    try {
                        publish("Initialisation de la connexion...");
                        gameClient = new GameClient(ip, ConnectHostScene.this);
                        gameClient.connect();
                        publish("Connexion établie! Chargement du lobby...");
                        return null;
                    } catch (Exception e) {
                        publish("Erreur de connexion: " + e.getMessage());
                        throw e;
                    }
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
                        get(); // Récupérer les exceptions éventuelles
                        
                        // Si la connexion a réussi, passer à ClientLobbyScene
                        if (gameClient != null && gameClient.isConnected()) {
                            // Démarrer le timer pour vérifier périodiquement l'état de la connexion
                            connectionCheckTimer.start();
                            transitionToClientLobbyScene();
                        }
                    } catch (Exception e) {
                        // Gérer l'échec de la connexion
                        isConnecting = false;
                        statusMessage = "Échec de la connexion: " + e.getMessage();
                        connectButton.setEnabled(true);
                        System.err.println("ConnectHostScene: Erreur de connexion - " + e.getMessage());
                        e.printStackTrace();
                        repaintPanel();
                        
                        if (gameClient != null) {
                            gameClient.disconnect();
                            gameClient = null;
                        }
                    }
                }
            };
            
            connectionWorker.execute();
        });

        // Initialisation du bouton "Retour"
        backButton = new Button(50, 400, 150, 40, "Retour", () -> {
            // Arrêter le timer de vérification
            if (connectionCheckTimer.isRunning()) {
                connectionCheckTimer.stop();
            }
            
            if (gameClient != null) {
                gameClient.disconnect();
                gameClient = null;
            }
            sceneManager.setScene(new MultiplayerScene(sceneManager));
        });
    }
    
    /**
     * Vérifie périodiquement l'état de la connexion
     * Si le client est déconnecté, affiche un message et revient à l'écran précédent
     */
    private void checkConnectionStatus() {
        if (gameClient != null && !gameClient.isConnected()) {
            // Arrêter le timer
            connectionCheckTimer.stop();
            
            System.out.println("ConnectHostScene: Détection de déconnexion lors de la vérification périodique");
            
            // Afficher un message et revenir à l'écran de connexion
            SwingUtilities.invokeLater(() -> {
                statusMessage = "Connexion au serveur perdue.";
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "La connexion au serveur a été perdue. Veuillez réessayer.",
                    "Déconnexion", JOptionPane.WARNING_MESSAGE);
                
                // Réinitialiser l'état
                isConnecting = false;
                connectButton.setEnabled(true);
                
                // Si nous sommes toujours dans ConnectHostScene (pas encore passé à ClientLobbyScene)
                if (sceneManager.getCurrentScene() instanceof ConnectHostScene) {
                    repaintPanel();
                } else {
                    // Si nous sommes déjà passés à une autre scène, il faut y revenir
                    sceneManager.setScene(new ConnectHostScene(sceneManager));
                }
            });
        }
    }

    @Override
    public void init() {
        startTime = System.currentTimeMillis();
        alpha = 0f;
        fadeComplete = false;
        statusMessage = "Entrez l'adresse IP de l'hôte.";
        isConnecting = false;
        
        // Arrêter le timer s'il est en cours
        if (connectionCheckTimer.isRunning()) {
            connectionCheckTimer.stop();
        }
        
        if (gameClient != null) {
            gameClient.disconnect();
            gameClient = null;
        }

        if (sceneManager.getPanel() != null) {
            int panelWidth = sceneManager.getPanel().getWidth();
            int panelHeight = sceneManager.getPanel().getHeight();
            
            int fieldWidth = Math.max(200, panelWidth / 3);
            int fieldHeight = 35;
            ipAddressField.setBounds(panelWidth / 2 - fieldWidth / 2, panelHeight / 4, fieldWidth, fieldHeight);
            ipAddressField.setFont(new Font("Arial", Font.PLAIN, 18));
            ipAddressField.setHorizontalAlignment(JTextField.CENTER);
            sceneManager.getPanel().setLayout(null);
            sceneManager.getPanel().add(ipAddressField);
            ipAddressField.setVisible(true);
            SwingUtilities.invokeLater(() -> ipAddressField.requestFocusInWindow());
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
                
                if (connectButton.contains(mousePoint) && connectButton.isEnabled()) 
                    connectButton.onClick();
                else if (backButton.contains(mousePoint)) 
                    backButton.onClick();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!fadeComplete) return;
                Point mousePoint = e.getPoint();
                
                if (connectButton.isEnabled() && connectButton.contains(mousePoint)) 
                    connectButton.setClicked(true);
                else if (backButton.contains(mousePoint)) 
                    backButton.setClicked(true);
                repaintPanel();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                connectButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel();
            }
            
            @Override
            public void mouseMoved(MouseEvent e) {
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
            if (alpha >= 1f) {
                alpha = 1f;
                fadeComplete = true;
            }
            repaintPanel();
        }
    }

    @Override
    public void render(Graphics g, int width, int height) {
        // Dessine l'arrière-plan
        g.setColor(new Color(45, 60, 75));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Titre
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 18;
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String titleText = "Rejoindre une Partie";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int textWidth = titleMetrics.stringWidth(titleText);
        g2d.drawString(titleText, (width - textWidth) / 2, height / 7);
        
        // Instructions
        int infoFontSize = Math.min(width, height) / 28;
        g2d.setFont(new Font("Arial", Font.PLAIN, infoFontSize));
        g2d.setColor(Color.LIGHT_GRAY);
        String ipInstructionText = "Entrez l'adresse IP de l'hôte:";
        FontMetrics ipMetrics = g2d.getFontMetrics();
        int ipTextWidth = ipMetrics.stringWidth(ipInstructionText);
        g2d.drawString(ipInstructionText, (width - ipTextWidth) / 2, height / 4 - 30);
        
        // Affiche le message d'état
        if (statusMessage != null && !statusMessage.isEmpty()) {
            g2d.setFont(new Font("Arial", Font.ITALIC, Math.min(width,height)/35));
            FontMetrics statusMetrics = g2d.getFontMetrics();
            int statusWidth = statusMetrics.stringWidth(statusMessage);
            g2d.setColor(isConnecting ? Color.YELLOW : Color.ORANGE);
            g2d.drawString(statusMessage, (width - statusWidth) / 2, height * 3 / 5);
        }
        
        // Boutons
        int btnWidth = width / 4;
        int btnHeight = height / 12;
        int btnFontSize = Math.min(width, height) / 30;
        Font commonBtnFont = new Font("Arial", Font.BOLD, btnFontSize);

        connectButton.setSize(btnWidth, btnHeight);
        connectButton.setLocation(width / 2 - btnWidth / 2, height / 3 + btnHeight);
        connectButton.setFont(commonBtnFont);

        backButton.setSize(btnWidth * 3/4, btnHeight * 3/4);
        backButton.setLocation(50, height - (btnHeight*3/4) - 30);
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

    // 创建一个显式的方法来处理成功连接后转到ClientLobbyScene的逻辑
    private void transitionToClientLobbyScene() {
        System.out.println("ConnectHostScene: Préparation de la transition vers ClientLobbyScene");
        
        // 确保在转换到ClientLobbyScene之前标记为不需断开连接
        final GameClient clientToTransfer = this.gameClient;
        this.gameClient = null; // 将引用设为null，这样dispose方法就不会断开它
        
        System.out.println("ConnectHostScene: Connexion réussie, passage au lobby");
        SwingUtilities.invokeLater(() -> {
            // 创建新的ClientLobbyScene并传递连接
            ClientLobbyScene lobbyScene = new ClientLobbyScene(sceneManager, clientToTransfer);
            sceneManager.setScene(lobbyScene);
        });
    }

    @Override
    public void dispose() {
        clearMouseListeners();
        
        // Arrêter le timer de vérification
        if (connectionCheckTimer != null && connectionCheckTimer.isRunning()) {
            connectionCheckTimer.stop();
        }
        
        if (sceneManager.getPanel() != null && ipAddressField != null) {
            ipAddressField.setVisible(false);
            sceneManager.getPanel().remove(ipAddressField);
            sceneManager.getPanel().revalidate();
            sceneManager.getPanel().repaint();
        }
        
        // Déconnecter le client seulement si on ne passe pas à ClientLobbyScene
        // Sinon, ClientLobbyScene prendra la responsabilité de gameClient
        if (gameClient != null && (sceneManager.getCurrentScene() == null || 
                !sceneManager.getCurrentScene().getClass().getSimpleName().equals("ClientLobbyScene"))) {
            System.out.println("ConnectHostScene: Déconnexion du client (transition vers une scène autre que ClientLobbyScene)");
            gameClient.disconnect();
            gameClient = null;
        } else if (gameClient == null) {
            System.out.println("ConnectHostScene: Client déjà transféré à ClientLobbyScene ou null");
        }
        
        System.out.println("ConnectHostScene disposée.");
    }
    
    // Implémentation de GameStateUpdateListener
    
    @Override
    public void onGameStateUpdate(Jeu newGameState) {
        // On ne fait rien pendant la connexion initiale
    }
    
    @Override
    public void onGameMessage(String messageType, String messageContent) {
        SwingUtilities.invokeLater(() -> {
            System.out.println("ConnectHostScene: Message reçu - Type: " + messageType + ", Contenu: " + messageContent);
            
            // Traitement des messages d'erreur ou de déconnexion
            if ("ERROR".equals(messageType) || "DISCONNECTED".equals(messageType)) {
                // Arrêter le timer de vérification
                if (connectionCheckTimer.isRunning()) {
                    connectionCheckTimer.stop();
                }
                
                statusMessage = "Erreur: " + messageContent;
                isConnecting = false;
                
                // Afficher un dialogue d'erreur
                JOptionPane.showMessageDialog(sceneManager.getPanel(),
                    "Erreur de connexion: " + messageContent,
                    "Erreur Réseau", JOptionPane.ERROR_MESSAGE);
                
                if (gameClient != null) {
                    gameClient.disconnect();
                    gameClient = null;
                }
                connectButton.setEnabled(true);
                
                // Si nous sommes déjà passés à une autre scène, revenir à ConnectHostScene
                if (!(sceneManager.getCurrentScene() instanceof ConnectHostScene)) {
                    sceneManager.setScene(new ConnectHostScene(sceneManager));
                } else {
                    repaintPanel();
                }
            }
        });
    }
}