package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * ConnectHostScene représente la scène où l'utilisateur peut entrer l'adresse IP
 * d'un hôte pour rejoindre une partie multijoueur.
 */
public class ConnectHostScene implements Scene {
    private SceneManager sceneManager; // Gestionnaire de scènes pour naviguer entre les scènes
    private Button connectButton; // Bouton pour tenter la connexion
    private Button backButton; // Bouton pour retourner à la scène précédente
    private JTextField ipAddressField; // Champ de texte pour saisir l'adresse IP
    private String statusMessage = "Entrez l'adresse IP de l'hôte."; // Message d'état affiché à l'utilisateur

    private MouseAdapter mouseAdapterInternal; // Adaptateur interne pour gérer les événements de la souris
    private long startTime; // Heure de début pour l'animation de fondu
    private float alpha = 0f; // Valeur alpha pour l'effet de fondu (transparence)
    private boolean fadeComplete = false; // Indique si l'animation de fondu est terminée


    /**
     * Constructeur de la scène ConnectHostScene.
     * @param sceneManager Le gestionnaire de scènes.
     */
    public ConnectHostScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        this.ipAddressField = new JTextField("127.0.0.1"); // Adresse IP par défaut (localhost)

        // Initialisation du bouton "Se connecter"
        connectButton = new Button(300, 300, 200, 50, "Se connecter", () -> {
            String ip = ipAddressField.getText().trim(); // Récupère et nettoie l'IP saisie
            if (ip.isEmpty()) {
                statusMessage = "L'adresse IP ne peut pas être vide.";
                // Affiche un message d'erreur si l'IP est vide
                JOptionPane.showMessageDialog(sceneManager.getPanel(), statusMessage, "Erreur de Saisie", JOptionPane.WARNING_MESSAGE);
                repaintPanel(); // Redessine le panneau pour afficher le message d'erreur (si intégré au rendu)
                return; // Arrête le processus si l'IP est vide
            }
            System.out.println("ConnectHostScene: Passage à GameScene avec IP: " + ip);
            // Crée et affiche la scène de jeu. GameScene gérera la tentative de connexion.
            GameScene gameScene = new GameScene(sceneManager, ip);
            sceneManager.setScene(gameScene);
        });

        // Initialisation du bouton "Retour"
        backButton = new Button(50, 400, 150, 40, "Retour", () -> {
            // Retourne à la scène multijoueur
            sceneManager.setScene(new MultiplayerScene(sceneManager));
        });
    }

    /**
     * Initialise la scène. Appelée lorsque la scène devient active.
     * Configure les composants, les écouteurs d'événements et l'animation de fondu.
     */
    @Override
    public void init() {
        startTime = System.currentTimeMillis(); // Réinitialise l'heure de début pour l'animation
        alpha = 0f; // Réinitialise l'alpha pour le fondu
        fadeComplete = false; // Indique que le fondu n'est pas terminé
        statusMessage = "Entrez l'adresse IP de l'hôte."; // Message initial

        if (sceneManager.getPanel() != null) {
            int panelWidth = sceneManager.getPanel().getWidth();
            int panelHeight = sceneManager.getPanel().getHeight();
            // Définit la taille et la position du champ de saisie de l'IP
            int fieldWidth = Math.max(200, panelWidth / 3); // Augmente légèrement la largeur
            int fieldHeight = 35; // Augmente légèrement la hauteur
            ipAddressField.setBounds(panelWidth / 2 - fieldWidth / 2, panelHeight / 2 - 60, fieldWidth, fieldHeight);
            ipAddressField.setFont(new Font("Arial", Font.PLAIN, 18)); // Augmente la taille de la police
            ipAddressField.setHorizontalAlignment(JTextField.CENTER); // Centre le texte dans le champ
            sceneManager.getPanel().setLayout(null); // Utilise un layout null pour positionner manuellement les composants
            sceneManager.getPanel().add(ipAddressField); // Ajoute le champ IP au panneau
            ipAddressField.setVisible(true); // Rend le champ IP visible
            // Demande le focus pour le champ IP après que le panneau soit affiché et validé
            SwingUtilities.invokeLater(() -> ipAddressField.requestFocusInWindow());
        }

        setupMouseListeners(); // Configure les écouteurs d'événements de la souris
        repaintPanel(); // Redessine le panneau
    }

    /**
     * Configure les écouteurs d'événements de la souris pour le panneau.
     * Crée un MouseAdapter pour gérer les clics, pressions, relâchements et mouvements de la souris.
     */
    private void setupMouseListeners() {
        clearMouseListeners(); // Supprime les anciens écouteurs pour éviter les doublons
        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!fadeComplete) return; // Ignore les clics pendant le fondu
                Point mousePoint = e.getPoint(); // Récupère les coordonnées du clic
                // Si le clic est sur le champ IP, lui donne le focus
                if (ipAddressField != null && ipAddressField.getBounds().contains(mousePoint)) {
                    ipAddressField.requestFocusInWindow();
                    return;
                }
                // Vérifie si le clic est sur l'un des boutons
                if (connectButton.contains(mousePoint)) connectButton.onClick();
                else if (backButton.contains(mousePoint)) backButton.onClick();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (!fadeComplete) return; // Ignore pendant le fondu
                Point mousePoint = e.getPoint();
                // Change l'état des boutons s'ils sont pressés
                if (connectButton.contains(mousePoint)) connectButton.setClicked(true);
                else if (backButton.contains(mousePoint)) backButton.setClicked(true);
                repaintPanel(); // Redessine pour montrer l'état "pressé"
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Réinitialise l'état "pressé" des boutons
                connectButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel(); // Redessine pour montrer l'état normal
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
        // Ajoute l'adaptateur au panneau du SceneManager
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal);
        }
    }

    /**
     * Supprime les écouteurs d'événements de la souris du panneau.
     * Important pour éviter les fuites de mémoire ou les comportements inattendus
     * lors du changement de scène.
     */
    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
        }
    }


    /**
     * Met à jour l'état de la scène. Principalement utilisé ici pour l'animation de fondu.
     */
    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime; // Temps écoulé depuis le début du fondu
            alpha = Math.min(1f, elapsed / 1000f); // Calcule l'alpha (0 à 1 en 1 seconde)
            if (alpha >= 1f) {
                alpha = 1f; // S'assure que alpha ne dépasse pas 1
                fadeComplete = true; // Marque le fondu comme terminé
            }
            repaintPanel(); // Redessine pour mettre à jour l'effet de fondu
        }
        // La mise à jour du survol a été gérée dans mouseMoved
    }

    /**
     * Dessine la scène.
     * @param g L'objet Graphics pour dessiner.
     * @param width La largeur de la zone de dessin.
     * @param height La hauteur de la zone de dessin.
     */
    @Override
    public void render(Graphics g, int width, int height) {
        // Dessine l'arrière-plan
        g.setColor(new Color(45, 60, 75));
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create(); // Crée une copie de Graphics pour ne pas affecter l'original
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); // Active l'anticrénelage
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)); // Applique l'effet de fondu

        // Dessine le titre
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.min(width, height) / 18; // Taille de police adaptative
        g2d.setFont(new Font("Arial", Font.BOLD, titleFontSize));
        String titleText = "Rejoindre une Partie";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int textWidth = titleMetrics.stringWidth(titleText);
        g2d.drawString(titleText, (width - textWidth) / 2, height / 5); // Centre le titre

        // Affiche le message d'état (par exemple, pour les erreurs)
        if (statusMessage != null && !statusMessage.isEmpty()) {
            g2d.setFont(new Font("Arial", Font.ITALIC, Math.min(width,height)/35)); // Police plus petite pour le message d'état
            FontMetrics statusMetrics = g2d.getFontMetrics();
            int statusWidth = statusMetrics.stringWidth(statusMessage);
            // Positionne le message d'état au-dessus du champ IP
            g2d.drawString(statusMessage, (width - statusWidth) / 2, height / 2 - 90);
        }

        // ipAddressField est dessiné par Swing

        // Définit la taille et la police communes pour les boutons
        int btnWidth = width / 4;
        int btnHeight = height / 12;
        int btnFontSize = Math.min(width, height) / 30; // Taille de police adaptative
        Font commonBtnFont = new Font("Arial", Font.BOLD, btnFontSize);

        // Configure et positionne le bouton "Se connecter"
        connectButton.setSize(btnWidth, btnHeight);
        connectButton.setLocation(width / 2 - btnWidth / 2, height / 2 + 30); // Sous le champ IP
        connectButton.setFont(commonBtnFont);

        // Configure et positionne le bouton "Retour"
        backButton.setSize(btnWidth * 3/4, btnHeight * 3/4); // Légèrement plus petit
        backButton.setLocation(50, height - (btnHeight*3/4) - 30 ); // En bas à gauche
        backButton.setFont(new Font("Arial", Font.PLAIN, btnFontSize * 3/4)); // Police légèrement plus petite pour "Retour"

        // Dessine les boutons
        connectButton.render(g2d);
        backButton.render(g2d);

        g2d.dispose(); // Libère les ressources de la copie de Graphics2D
    }

    /**
     * Demande au panneau de se redessiner.
     * Utile après des changements qui affectent l'affichage.
     */
    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }

    /**
     * Nettoie les ressources utilisées par la scène avant de la quitter.
     * Supprime les écouteurs d'événements et les composants du panneau.
     */
    @Override
    public void dispose() {
        clearMouseListeners(); // Supprime les écouteurs de la souris
        if (sceneManager.getPanel() != null && ipAddressField != null) {
            ipAddressField.setVisible(false); // Cacher avant de supprimer
            sceneManager.getPanel().remove(ipAddressField); // Supprime le champ IP du panneau
            // Nécessaire après la suppression du composant
            sceneManager.getPanel().revalidate();
            sceneManager.getPanel().repaint(); // Redessine pour refléter la suppression
        }
        System.out.println("ConnectHostScene disposée."); // Message de confirmation
    }
}