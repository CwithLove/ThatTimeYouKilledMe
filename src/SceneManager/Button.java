package SceneManager;

import java.awt.*;

/**
 * La classe Button représente un bouton interactif dans l'interface utilisateur.
 * Elle gère l'affichage du bouton, les changements d'apparence lors du survol
 * ou du clic, et exécute une action (callback) lorsqu'il est cliqué.
 */
public class Button {

    private Rectangle rect;             // Rectangle définissant la position et la taille du bouton.
    private String text;                // Texte affiché sur le bouton.
    private Font font;                  // Police de caractères pour le texte du bouton.
    public Color normalColor;           // Couleur du bouton à l'état normal. (public pour personnalisation externe si besoin)
    public Color hoverColor;            // Couleur du bouton lorsque la souris le survole. (public)
    public Color clickColor;            // Couleur du bouton lorsqu'il est cliqué et maintenu. (public)
    private boolean isHovered = false;  // État : vrai si la souris survole actuellement le bouton.
    private boolean isClicked = false;  // État : vrai si le bouton est actuellement cliqué et maintenu.
    private Runnable onClick;           // Action (Callback) à exécuter lorsque le bouton est cliqué et relâché.
    private boolean enabled = true;     // État : vrai si le bouton est activé et interactif.

    /**
     * Constructeur pour créer un objet Button.
     *
     * @param x Coordonnée x du coin supérieur gauche du bouton.
     * @param y Coordonnée y du coin supérieur gauche du bouton.
     * @param width Largeur du bouton.
     * @param height Hauteur du bouton.
     * @param text Texte à afficher sur le bouton.
     * @param onClick Action (Runnable) à exécuter lorsque le bouton est cliqué.
     */
    public Button(int x, int y, int width, int height, String text, Runnable onClick) {
        this.rect = new Rectangle(x, y, width, height);
        this.text = text;
        this.font = new Font("Arial", Font.BOLD, 16); // Police par défaut
        // Couleurs par défaut
        this.normalColor = new Color(100, 100, 200); // Bleu clair
        this.hoverColor = new Color(130, 130, 230);  // Bleu plus lumineux
        this.clickColor = new Color(70, 70, 150);   // Bleu plus foncé
        this.onClick = onClick;
    }

    /**
     * Met à jour l'état de survol (hover) du bouton en fonction de la position de la souris.
     * Si le bouton est désactivé (enabled = false), il ne sera jamais considéré comme survolé.
     *
     * @param mousePos Position actuelle du curseur de la souris.
     */
    public void update(Point mousePos) {
        if (!enabled) {
            isHovered = false; // Si désactivé, ne peut pas être survolé
            return;
        }
        // Vérifie si la position de la souris est contenue dans le rectangle du bouton
        if (mousePos != null) { // Ajout d'une vérification de nullité pour mousePos
             isHovered = rect.contains(mousePos);
        } else {
            isHovered = false; // Si pas de position de souris (ex: souris hors de la fenêtre), pas de survol
        }
    }

    /**
     * Dessine le bouton sur l'objet Graphics2D fourni.
     * La couleur du bouton change en fonction des états enabled, isClicked, et isHovered.
     * Le texte est centré sur le bouton.
     *
     * @param g2d L'objet Graphics2D sur lequel dessiner.
     */
    public void render(Graphics2D g2d) {
        // Sauvegarde la couleur et la police originales pour les restaurer après le dessin
        Color originalColor = g2d.getColor();
        Font originalFont = g2d.getFont();
        Stroke originalStroke = g2d.getStroke(); // Sauvegarde le trait original

        // Choisit la couleur de fond du bouton
        if (enabled) {
            if (isClicked) {
                g2d.setColor(clickColor);
            } else if (isHovered) {
                g2d.setColor(hoverColor);
            } else {
                g2d.setColor(normalColor);
            }
        } else {
            // Couleur lorsque le bouton est désactivé (ex: gris)
            g2d.setColor(new Color(120, 120, 120, 150)); // Gris, légèrement transparent
        }
        g2d.fill(rect); // Dessine le rectangle de fond du bouton

        // Dessine une bordure pour le bouton (optionnel, peut être ajouté)
        if (enabled) {
            g2d.setColor(normalColor.darker()); // Bordure un peu plus foncée que la couleur normale
        } else {
            g2d.setColor(new Color(80,80,80)); // Bordure gris foncé pour un bouton désactivé
        }
        g2d.setStroke(new BasicStroke(1.5f)); // Épaisseur de la bordure
        g2d.drawRect(rect.x, rect.y, rect.width, rect.height); // Dessine la bordure

        // Définit la police et la couleur pour le texte
        g2d.setFont(font);
        FontMetrics metrics = g2d.getFontMetrics(font); // Récupère les informations de la police pour centrer le texte

        // Calcule la position pour centrer le texte dans le bouton
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight(); // Hauteur totale de la ligne de texte
        int textAscent = metrics.getAscent(); // Hauteur de la ligne de base au sommet du caractère le plus haut

        // Coordonnée x pour centrer le texte horizontalement
        int textX = rect.x + (rect.width - textWidth) / 2;
        // Coordonnée y pour centrer le texte verticalement
        // (rect.height - textHeight) / 2 est l'espace au-dessus et en dessous du texte
        // + textAscent pour positionner correctement la ligne de base du texte
        int textY = rect.y + ((rect.height - textHeight) / 2) + textAscent;

        // Choisit la couleur du texte
        if (enabled) {
            g2d.setColor(Color.WHITE); // Texte blanc pour un bouton activé
        } else {
            g2d.setColor(new Color(180, 180, 180)); // Texte gris clair pour un bouton désactivé
        }

        g2d.drawString(text, textX, textY); // Dessine le texte

        // Restaure la couleur, la police et le trait originaux
        g2d.setColor(originalColor);
        g2d.setFont(originalFont);
        g2d.setStroke(originalStroke);
    }

    /**
     * Exécute l'action (Runnable) assignée au bouton lorsqu'il est cliqué.
     * S'exécute seulement si le bouton est activé (enabled = true) et si une action onClick est définie.
     */
    public void onClick() {
        if (enabled && onClick != null) {
            onClick.run();
        }
    }

    /**
     * Vérifie si un point (typiquement la position de la souris) est contenu dans les limites du bouton.
     *
     * @param p Le point à vérifier.
     * @return vrai si le point est dans le bouton, faux sinon.
     */
    public boolean contains(Point p) {
        if (p == null) return false; // Ajout d'une vérification de nullité pour Point
        return rect.contains(p);
    }

    /**
     * Définit l'état "cliqué et maintenu" (pressed) du bouton.
     *
     * @param clicked vrai si le bouton est cliqué et maintenu, faux sinon.
     */
    public void setClicked(boolean clicked) {
        if (!enabled && clicked) return; // Ne peut pas être mis à l'état cliqué si le bouton est désactivé
        this.isClicked = clicked;
    }

    /**
     * Définit la nouvelle position du bouton.
     *
     * @param x La nouvelle coordonnée x.
     * @param y La nouvelle coordonnée y.
     */
    public void setLocation(int x, int y) {
        rect.setLocation(x, y);
    }

    /**
     * Définit la nouvelle taille du bouton.
     *
     * @param width La nouvelle largeur.
     * @param height La nouvelle hauteur.
     */
    public void setSize(int width, int height) {
        rect.setSize(width, height);
    }

    /**
     * Définit la nouvelle police de caractères pour le texte du bouton.
     *
     * @param font La nouvelle police.
     */
    public void setFont(Font font) {
        this.font = font;
    }

    /**
     * Définit la couleur de fond du bouton à l'état normal.
     * @param color La nouvelle couleur.
     */
    public void setNormalColor(Color color) {
        this.normalColor = color;
    }

    /**
     * Définit la couleur de fond du bouton lorsque la souris le survole.
     * @param color La nouvelle couleur.
     */
    public void setHoverColor(Color color) {
        this.hoverColor = color;
    }

    /**
     * Définit la couleur de fond du bouton lorsqu'il est cliqué et maintenu.
     * @param color La nouvelle couleur.
     */
    public void setClickColor(Color color) {
        this.clickColor = color;
    }

    /**
     * Récupère la coordonnée x du bouton.
     * @return La coordonnée x.
     */
    public int getX() {
        return rect.x;
    }

    /**
     * Récupère la coordonnée y du bouton.
     * @return La coordonnée y.
     */
    public int getY() {
        return rect.y;
    }

    /**
     * Récupère la largeur du bouton.
     * @return La largeur.
     */
    public int getWidth() {
        return rect.width;
    }

    /**
     * Récupère la hauteur du bouton.
     * @return La hauteur.
     */
    public int getHeight() {
        return rect.height;
    }

    /**
     * Définit l'état d'activation (enabled) du bouton.
     * Si un bouton est désactivé, il ne répondra pas aux événements de la souris et
     * pourra être dessiné différemment pour indiquer son état.
     *
     * @param enabled vrai pour activer le bouton, faux pour le désactiver.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            // Lorsqu'il est désactivé, s'assurer qu'il n'est pas dans l'état survolé ou cliqué
            isHovered = false;
            isClicked = false;
        }
    }

    /**
     * Vérifie si le bouton est actuellement activé.
     *
     * @return vrai si le bouton est activé, faux sinon.
     */
    public boolean isEnabled() {
        return enabled;
    }

    public void setFontsize(int size) {
        this.font = this.font.deriveFont((float) size);
    }
    
}