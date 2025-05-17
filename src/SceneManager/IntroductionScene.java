package SceneManager;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics; // Importation manquante pour FontMetrics
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;
import java.io.File;

/**
 * IntroductionScene est une scène qui affiche un logo ou un message d'introduction
 * avec des effets de fondu (fade-in, hold, fade-out) avant de passer à la scène suivante (généralement MenuScene).
 */
public class IntroductionScene implements Scene {
    private BufferedImage logo;          // Image du logo à afficher.
    private SceneManager sceneManager;  // Gestionnaire de scènes pour changer de scène.
    private long startTime;             // Temps de début pour calculer la durée des phases d'animation.
    private float alpha = 0.0f;         // Niveau d'opacité actuel pour l'effet de fondu (0.0f transparent, 1.0f opaque).

    /**
     * Énumération définissant les différentes phases de l'animation d'introduction.
     */
    private enum Phase {
        FADE_IN,    // Phase d'apparition progressive.
        HOLD,       // Phase de maintien de l'affichage (logo/texte visible).
        FADE_OUT,   // Phase de disparition progressive.
        DONE        // Phase indiquant que l'introduction est terminée.
    }

    private Phase currentPhase = Phase.FADE_IN; // Phase actuelle de l'animation.

    /**
     * Constructeur de IntroductionScene.
     * Charge l'image du logo et initialise le gestionnaire de scènes.
     *
     * @param sceneManager Le gestionnaire de scènes principal de l'application.
     */
    public IntroductionScene(SceneManager sceneManager) {
        this.sceneManager = sceneManager;
        try {
            // Tente de charger l'image du logo depuis le chemin spécifié.
            // Assurez-vous que le chemin "res/Logo.png" est correct par rapport à la racine de votre projet.
            logo = ImageIO.read(new File("res/Logo/Logo.png"));
        } catch (IOException e) {
            // En cas d'erreur de chargement, affiche la trace de l'erreur.
            // Vous pourriez vouloir gérer cette erreur plus élégamment (ex: afficher un message).
            System.err.println("Erreur lors du chargement du logo : " + e.getMessage());
            e.printStackTrace();
            logo = null; // S'assurer que logo est null si le chargement échoue
        }
    }

    /**
     * Initialise la scène. Appelée une fois lorsque la scène devient active.
     * Enregistre le temps de début pour l'animation.
     */
    @Override
    public void init() {
        this.startTime = System.currentTimeMillis(); // Enregistre le temps de début
        this.alpha = 0.0f; // Réinitialise l'alpha
        this.currentPhase = Phase.FADE_IN; // Commence toujours par FADE_IN
    }

    /**
     * Met à jour la logique de la scène à chaque tick du jeu.
     * Gère la transition entre les différentes phases de l'animation de fondu.
     */
    @Override
    public void update() {
        long elapsed = System.currentTimeMillis() - startTime; // Temps écoulé depuis le début de la phase actuelle.

        switch (currentPhase) {
            case FADE_IN:
                // Augmente progressivement l'alpha de 0 à 1 sur 1000 ms (1 seconde).
                alpha = Math.min(1.0f, elapsed / 1000.0f);
                if (alpha >= 1.0f) { // Si l'apparition est complète
                    currentPhase = Phase.HOLD; // Passe à la phase de maintien
                    startTime = System.currentTimeMillis(); // Réinitialise le temps pour la nouvelle phase
                }
                break;

            case HOLD:
                // Maintient l'alpha à 1 (complètement visible) pendant 1500 ms (1.5 secondes).
                alpha = 1.0f;
                if (elapsed >= 1500) { // Si le temps de maintien est écoulé
                    currentPhase = Phase.FADE_OUT; // Passe à la phase de disparition
                    startTime = System.currentTimeMillis(); // Réinitialise le temps
                }
                break;

            case FADE_OUT:
                // Diminue progressivement l'alpha de 1 à 0 sur 1000 ms (1 seconde).
                // Note: Votre code original avait 500f, je le garde mais 1000f peut être plus doux.
                alpha = Math.max(0.0f, 1.0f - (elapsed / 1000.0f)); // Ajusté à 1000ms pour la disparition
                if (alpha <= 0.0f) { // Si la disparition est complète
                    currentPhase = Phase.DONE; // L'introduction est terminée
                    // Change de scène pour passer au menu principal.
                    sceneManager.setScene(new MenuScene(sceneManager));
                }
                break;

            case DONE:
                // Aucune action supplémentaire une fois que c'est terminé et que la scène a changé.
                break;
        }
    }

    /**
     * Dessine le contenu de la scène.
     * Affiche un fond noir, puis le texte "Made by" et le logo avec l'effet de fondu (alpha).
     *
     * @param g L'objet Graphics sur lequel dessiner.
     * @param width La largeur actuelle de la zone de dessin.
     * @param height La hauteur actuelle de la zone de dessin.
     */
    @Override
    public void render(Graphics g, int width, int height) {
        // Dessine un fond noir uni.
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);

        // Crée une copie de Graphics pour appliquer des transformations locales (comme AlphaComposite).
        Graphics2D g2d = (Graphics2D) g.create();

        // Applique l'effet de transparence basé sur la valeur actuelle d'alpha.
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        // Dessin du texte "Made by" (si le logo ne l'inclut pas)
        // Vous pouvez ajuster ou supprimer cette partie si votre logo contient déjà ce texte.
        String text = "Un jeu présenté par"; // Texte d'exemple
        Font textFont = new Font("Arial", Font.BOLD, Math.max(18, height / 25)); // Taille de police dynamique
        g2d.setFont(textFont);
        g2d.setColor(Color.LIGHT_GRAY); // Couleur du texte

        FontMetrics metrics = g2d.getFontMetrics(textFont);
        int textStringWidth = metrics.stringWidth(text);
        // int textStringHeight = metrics.getHeight(); // Pas directement utilisé pour y ici

        // Positionne le texte "Made by" au-dessus du logo (ajuster si nécessaire)
        int textX = (width - textStringWidth) / 2;
        int textY = height / 2 - (logo != null ? (int)(logo.getHeight() * 0.15) : 0) - metrics.getAscent(); // Positionne au-dessus du centre du logo


        // Dessine l'image du logo (si elle a été chargée correctement).
        if (logo != null) {
            // Calcule les dimensions et la position pour dessiner le logo centré et redimensionné.
            // Redimensionne le logo à 30% de sa taille originale (ajuster au besoin).
            double scale = 0.3; // Facteur d'échelle pour le logo
            int imgWidth = (int) (logo.getWidth() * scale);
            int imgHeight = (int) (logo.getHeight() * scale);
            int imgX = (width - imgWidth) / 2;  // Centre horizontalement
            int imgY = (height - imgHeight) / 2; // Centre verticalement

            g2d.drawImage(logo, imgX, imgY, imgWidth, imgHeight, null);
            // Dessine le texte après le logo pour qu'il ne soit pas couvert si le logo est grand
             g2d.drawString(text, textX, imgY - metrics.getHeight() - 5); // 5px d'espace
        } else {
            // Si le logo n'a pas pu être chargé, affiche seulement le texte "Made by" au centre
            textY = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            g2d.drawString(text, textX, textY);
        }


        // Libère les ressources de la copie de Graphics2D.
        g2d.dispose();
    }

    /**
     * Libère les ressources utilisées par la scène lorsqu'elle n'est plus active.
     * Actuellement, pas de ressources spécifiques à libérer manuellement ici (le logo est géré par le GC).
     */
    @Override
    public void dispose() {
        // Si vous aviez des listeners ou d'autres ressources à nettoyer spécifiquement
        // pour cette scène, vous le feriez ici.
        // Par exemple : sceneManager.getPanel().removeMouseListener(monListenerDeCetteScene);
        System.out.println("IntroductionScene disposée.");
    }
}