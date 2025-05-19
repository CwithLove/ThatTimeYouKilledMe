package SceneManager;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * MultiplayerScene est la scène qui offre les options pour le mode multijoueur,
 * permettant à l'utilisateur de choisir entre héberger une partie ou rejoindre une partie existante.
 */
public class MultiplayerScene implements Scene {

    private MouseAdapter mouseAdapterInternal;  // Adaptateur pour gérer les événements de la souris.
    private SceneManager sceneManager;          // Gestionnaire de scènes pour naviguer entre les scènes.
    private Button hostButton;                  // Bouton pour devenir hôte.
    private Button connectButton;               // Bouton pour se connecter à un hôte.
    private Button backButton;                  // Bouton pour retourner au menu principal.
    private long startTime;                     // Temps de début pour l'animation de fondu.
    private float alpha = 0f;                   // Niveau d'opacité pour l'effet de fondu.
    private boolean fadeComplete = false;       // Indique si l'animation de fondu est terminée.

    /**
     * Constructeur de MultiplayerScene.
     * Initialise les boutons et leurs actions respectives.
     *
     * @param sceneManager Le gestionnaire de scènes principal de l'application.
     */
    public MultiplayerScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;

        // Initialisation des boutons. Leurs positions et tailles réelles
        // seront définies dynamiquement dans la méthode render().
        hostButton = new Button(0, 0, 0, 0, "Devenir Hôte", () -> {
            // Change la scène pour HostingScene lorsque le bouton "Devenir Hôte" est cliqué.
            sceneManager.setScene(new HostingScene(sceneManager));
        });

        connectButton = new Button(0, 0, 0, 0, "Rejoindre Hôte", () -> {
            // Change la scène pour ConnectHostScene lorsque le bouton "Rejoindre Hôte" est cliqué.
            sceneManager.setScene(new ConnectHostScene(sceneManager));
        });

        backButton = new Button(0, 0, 0, 0, "Retour au Menu", () -> {
            // Retourne à la scène du menu principal.
            sceneManager.setScene(new MenuScene(sceneManager));
        });
    }

    /**
     * Initialise la scène. Appelée une fois lorsque la scène devient active.
     * Réinitialise les variables pour l'animation de fondu et configure les écouteurs d'événements.
     */
    @Override
    public void init() {
        startTime = System.currentTimeMillis(); // Enregistre le temps de début pour le fondu.
        alpha = 0f;                             // Opacité initiale à transparent.
        fadeComplete = false;                   // Le fondu n'est pas encore terminé.
        setupMouseListeners();                  // Configure les écouteurs de souris.
        repaintPanel();                         // Demande un rafraîchissement initial du panneau.
    }

    /**
     * Configure les écouteurs d'événements de la souris pour cette scène.
     * S'assure de nettoyer les écouteurs précédents pour éviter les doublons ou fuites.
     */
    private void setupMouseListeners() {
        clearMouseListeners(); // Nettoie les écouteurs existants.

        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) { // Traite les clics seulement si l'animation de fondu est terminée.
                    Point mousePoint = e.getPoint();
                    // Vérifie si le clic est sur l'un des boutons et exécute l'action correspondante.
                    if (hostButton.isEnabled() && hostButton.contains(mousePoint)) hostButton.onClick();
                    else if (connectButton.isEnabled() && connectButton.contains(mousePoint)) connectButton.onClick();
                    else if (backButton.isEnabled() && backButton.contains(mousePoint)) backButton.onClick();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    // Met à jour l'état "pressé" des boutons.
                    if (hostButton.isEnabled() && hostButton.contains(mousePoint)) hostButton.setClicked(true);
                    else if (connectButton.isEnabled() && connectButton.contains(mousePoint)) connectButton.setClicked(true);
                    else if (backButton.isEnabled() && backButton.contains(mousePoint)) backButton.setClicked(true);
                    repaintPanel(); // Rafraîchit pour montrer l'état visuel "pressé".
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Réinitialise l'état "pressé" de tous les boutons lorsque le clic est relâché.
                hostButton.setClicked(false);
                connectButton.setClicked(false);
                backButton.setClicked(false);
                repaintPanel(); // Rafraîchit pour montrer l'état normal des boutons.
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    // Met à jour l'état de survol (hover) des boutons.
                    hostButton.update(mousePoint);
                    connectButton.update(mousePoint);
                    backButton.update(mousePoint);
                    repaintPanel(); // Rafraîchit pour montrer l'effet de survol.
                }
            }
        };

        // Ajoute l'adaptateur au panneau du gestionnaire de scènes.
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal); // MouseAdapter gère aussi mouseMoved.
        }
    }

    /**
     * Enlève les écouteurs d'événements de la souris du panneau.
     * Crucial pour éviter les fuites de mémoire et les comportements inattendus lors du changement de scène.
     */
    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
            // mouseAdapterInternal = null; // Optionnel, un nouveau sera créé dans setupMouseListeners
        }
    }

    /**
     * Met à jour la logique de la scène.
     * Principalement utilisé ici pour gérer l'animation de fondu.
     */
    @Override
    public void update() {
        if (!fadeComplete) {
            long elapsed = System.currentTimeMillis() - startTime;
            alpha = Math.min(1.0f, elapsed / 1000.0f); // Animation de fondu sur 1 seconde.
            if (alpha >= 1.0f) {
                fadeComplete = true; // L'animation est terminée.
            }
        }
    }

    /**
     * Dessine le contenu de la scène (arrière-plan, titre, boutons).
     *
     * @param g L'objet Graphics sur lequel dessiner.
     * @param width La largeur actuelle de la zone de dessin.
     * @param height La hauteur actuelle de la zone de dessin.
     */
    @Override
    public void render(Graphics g, int width, int height) {
        // Dessine l'arrière-plan.
        g.setColor(new Color(50, 70, 90)); // Couleur de fond spécifique à cette scène.
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create(); // Crée une copie pour des transformations locales.
        // Active l'anticrénelage pour un rendu plus lisse.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Applique l'effet de fondu.
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Dessine le titre de la scène.
        g2d.setColor(Color.WHITE);
        int titleFontSize = Math.max(22, Math.min(width, height) / 15); // Taille de police dynamique.
        g2d.setFont(new Font("SansSerif", Font.BOLD, titleFontSize));
        String title = "Mode Multijoueur";
        FontMetrics titleMetrics = g2d.getFontMetrics();
        int titleTextWidth = titleMetrics.stringWidth(title);
        g2d.drawString(title, (width - titleTextWidth) / 2, height / 5); // Centre le titre.

        // Configuration dynamique des dimensions et positions des boutons.
        int buttonWidth = Math.max(280, width / 2 - 80); // Ajustement de la largeur.
        int buttonHeight = Math.max(55, height / 9);    // Ajustement de la hauteur.
        Font buttonFont = new Font("Arial", Font.BOLD, Math.max(18, Math.min(width, height) / 25)); // Police plus grande.
        int buttonSpacing = buttonHeight * 3/4; // Espacement ajusté.

        // Positionne les boutons "Devenir Hôte" et "Rejoindre Hôte" au centre.
        hostButton.setSize(buttonWidth, buttonHeight);
        hostButton.setLocation(width / 2 - buttonWidth / 2, height / 2 - buttonHeight - buttonSpacing / 2);
        hostButton.setFont(buttonFont);
        hostButton.setNormalColor(new Color(70, 130, 180)); // Couleur "Steel Blue".

        connectButton.setSize(buttonWidth, buttonHeight);
        connectButton.setLocation(width / 2 - buttonWidth / 2, height / 2 + buttonSpacing / 2);
        connectButton.setFont(buttonFont);
        connectButton.setNormalColor(new Color(60, 179, 113)); // Couleur "Medium Sea Green".

        // Positionne le bouton "Retour" en bas à gauche.
        int backButtonWidth = Math.max(140, width / 5);
        int backButtonHeight = Math.max(45, height / 14);
        Font backButtonFont = new Font("Arial", Font.PLAIN, Math.max(14, Math.min(width, height) / 32));
        backButton.setSize(backButtonWidth, backButtonHeight);
        backButton.setLocation(40, height - backButtonHeight - 25); // Ajustement de la position.
        backButton.setFont(backButtonFont);
        backButton.setNormalColor(new Color(200, 100, 100)); // Couleur "Indian Red" clair.

        // Dessine les boutons.
        hostButton.render(g2d);
        connectButton.render(g2d);
        backButton.render(g2d);

        g2d.dispose(); // Libère les ressources du contexte graphique copié.
    }

    /**
     * Demande au panneau de se redessiner.
     * Utile après des changements d'état qui affectent l'affichage.
     */
    private void repaintPanel() {
        if (sceneManager != null && sceneManager.getPanel() != null) {
            sceneManager.getPanel().repaint();
        }
    }

    /**
     * Libère les ressources utilisées par la scène lorsqu'elle n'est plus active.
     * Principalement, enlève les écouteurs d'événements.
     */
    @Override
    public void dispose() {
        clearMouseListeners(); // Enlève les écouteurs de souris associés à cette scène.
        System.out.println("MultiplayerScene disposée.");
    }
}