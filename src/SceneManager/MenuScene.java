package SceneManager;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints; // Importation nécessaire pour RenderingHints
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;


/**
 * MenuScene est la scène principale du menu du jeu.
 * Elle permet à l'utilisateur de choisir entre démarrer une partie solo,
 * accéder au menu multijoueur, ou quitter le jeu.
 * Elle gère un effet de fondu à l'apparition.
 */
public class MenuScene implements Scene {

    private SceneManager sceneManager;          // Gestionnaire de scènes pour changer de scène.
    private MouseAdapter mouseAdapterInternal;  // Adaptateur pour gérer les événements de la souris (clics, mouvements).
    private Button singleButton;                // Bouton pour le mode "Single Player".
    private Button multiButton;                 // Bouton pour le mode "Multi Player".
    private Button quitButton;                  // Bouton pour "Quitter".
    private long startTime;                     // Temps de début pour l'animation de fondu.
    private float alpha = 0f;                   // Niveau d'opacité actuel pour le fondu (0.0f à 1.0f).
    private boolean fadeComplete = false;       // Indique si l'animation de fondu est terminée.

    /**
     * Constructeur de MenuScene.
     * Initialise les boutons avec leurs actions respectives.
     *
     * @param sceneManager Le gestionnaire de scènes principal de l'application.
     */
    public MenuScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;

        // Initialisation des boutons. Leurs positions et tailles réelles
        // seront définies dynamiquement dans la méthode render().
        singleButton = new Button(0, 0, 0, 0, "Single Player (vs AI)", () -> {
            // 直接跳转到单人游戏大厅场景
            sceneManager.setScene(new SinglePlayerLobbyScene(sceneManager));
        });

        multiButton = new Button(0, 0, 0, 0, "Multi Player", () -> {
            // Passe à la scène de sélection du mode multijoueur.
            sceneManager.setScene(new MultiplayerScene(sceneManager));
        });

        quitButton = new Button(0, 0, 0, 0, "Quitter", () -> {
            // Quitte l'application.
            // Des actions de nettoyage supplémentaires (ex: arrêt de serveurs)
            // devraient être gérées dans les méthodes dispose() des scènes concernées.
            System.exit(0);
        });
    }

    /**
     * Initialise la scène. Appelée une fois lorsque la scène devient active.
     * Réinitialise les variables pour l'animation de fondu et configure les écouteurs d'événements.
     */
    @Override
    public void init() {
        startTime = System.currentTimeMillis(); // Temps de début pour le fondu
        alpha = 0f;                             // Opacité initiale
        fadeComplete = false;                   // Le fondu n'est pas encore terminé
        setupMouseListeners();                  // Configure les écouteurs de souris
        repaintPanel();                         // Demande un rafraîchissement initial du panneau
    }

    /**
     * Configure les écouteurs d'événements de la souris pour la scène.
     * S'assure de nettoyer les écouteurs précédents avant d'en ajouter de nouveaux.
     */
    private void setupMouseListeners() {
        clearMouseListeners(); // Enlève les écouteurs existants pour éviter les doublons

        mouseAdapterInternal = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (fadeComplete) { // Traite les clics seulement si le fondu est terminé
                    Point mousePoint = e.getPoint();
                    if (singleButton.isEnabled() && singleButton.contains(mousePoint)) singleButton.onClick();
                    else if (multiButton.isEnabled() && multiButton.contains(mousePoint)) multiButton.onClick();
                    else if (quitButton.isEnabled() && quitButton.contains(mousePoint)) quitButton.onClick();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    if (singleButton.isEnabled() && singleButton.contains(mousePoint)) singleButton.setClicked(true);
                    else if (multiButton.isEnabled() && multiButton.contains(mousePoint)) multiButton.setClicked(true);
                    else if (quitButton.isEnabled() && quitButton.contains(mousePoint)) quitButton.setClicked(true);
                    repaintPanel(); // Rafraîchit pour montrer l'état "pressé"
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // Réinitialise l'état "pressé" de tous les boutons
                singleButton.setClicked(false);
                multiButton.setClicked(false);
                quitButton.setClicked(false);
                repaintPanel(); // Rafraîchit pour montrer l'état normal
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (fadeComplete) {
                    Point mousePoint = e.getPoint();
                    // Met à jour l'état de survol (hover) des boutons
                    singleButton.update(mousePoint);
                    multiButton.update(mousePoint);
                    quitButton.update(mousePoint);
                    repaintPanel(); // Rafraîchit pour montrer l'effet de survol
                }
            }
        };

        // Ajoute l'adaptateur au panneau du gestionnaire de scènes (s'il existe)
        if (sceneManager.getPanel() != null) {
            sceneManager.getPanel().addMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().addMouseMotionListener(mouseAdapterInternal); // MouseAdapter gère aussi mouseMoved
        }
    }

    /**
     * Enlève les écouteurs d'événements de la souris du panneau.
     * Important pour éviter les fuites de mémoire ou les comportements inattendus
     * lorsque la scène est changée.
     */
    private void clearMouseListeners() {
        if (sceneManager.getPanel() != null && mouseAdapterInternal != null) {
            sceneManager.getPanel().removeMouseListener(mouseAdapterInternal);
            sceneManager.getPanel().removeMouseMotionListener(mouseAdapterInternal);
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
            alpha = Math.min(1.0f, elapsed / 1000.0f); // Fondu sur 1 seconde
            if (alpha >= 1.0f) {
                fadeComplete = true; // Animation de fondu terminée
            }
            // Il n'est généralement pas nécessaire d'appeler repaintPanel() ici,
            // car la boucle de jeu principale devrait appeler render() régulièrement.
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
        // Dessine l'arrière-plan
        g.setColor(new Color(50, 50, 100)); // Bleu foncé pour l'arrière-plan
        g.fillRect(0, 0, width, height);

        Graphics2D g2d = (Graphics2D) g.create(); // Crée une copie pour éviter de modifier l'objet Graphics original
        // Active l'anticrénelage pour un rendu plus lisse du texte et des formes
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Applique l'effet de fondu (transparence)
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Dessine le titre du jeu
        String title = "That Time You Killed Me";
        // Taille de police dynamique, s'adaptant à la taille de la fenêtre, avec une taille minimale.
        int titleFontSize = Math.max(24, Math.min(width, height) / 12);
        Font titleFont = new Font("Serif", Font.BOLD | Font.ITALIC, titleFontSize);
        g2d.setFont(titleFont);
        g2d.setColor(Color.ORANGE); // Couleur distinctive pour le titre
        FontMetrics titleMetrics = g2d.getFontMetrics(titleFont);
        int titleTextWidth = titleMetrics.stringWidth(title);
        // Centre le titre horizontalement et le positionne verticalement
        g2d.drawString(title, (width - titleTextWidth) / 2, height / 5);

        // Configure et positionne les boutons dynamiquement
        int buttonWidth = Math.max(220, width / 3); // Largeur minimale ou 1/3 de la largeur de l'écran
        int buttonHeight = Math.max(55, height / 11); // Hauteur minimale ou 1/11 de la hauteur de l'écran
        Font buttonFont = new Font("Arial", Font.BOLD, Math.max(16, Math.min(width, height) / 30)); // Taille de police dynamique pour les boutons
        int buttonSpacing = buttonHeight / 2; // Espacement entre les boutons

        // Positionnement vertical des boutons autour du centre de l'écran
        int centerY = height / 2;
        singleButton.setSize(buttonWidth, buttonHeight);
        singleButton.setLocation(width / 2 - buttonWidth / 2, centerY - buttonHeight - buttonSpacing);
        singleButton.setFont(buttonFont);

        multiButton.setSize(buttonWidth, buttonHeight);
        multiButton.setLocation(width / 2 - buttonWidth / 2, centerY);
        multiButton.setFont(buttonFont);

        quitButton.setSize(buttonWidth, buttonHeight);
        quitButton.setLocation(width / 2 - buttonWidth / 2, centerY + buttonHeight + buttonSpacing);
        quitButton.setFont(buttonFont);

        // Dessine les boutons
        singleButton.render(g2d);
        multiButton.render(g2d);
        quitButton.render(g2d);

        g2d.dispose(); // Libère les ressources du contexte graphique copié
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
     * Principalement, enlève les écouteurs d'événements pour éviter les fuites de mémoire.
     */
    @Override
    public void dispose() {
        clearMouseListeners(); // Enlève les écouteurs de souris associés à cette scène
        System.out.println("MenuScene disposée.");
    }
}