package SceneManager;

import java.awt.Graphics;

/**
 * Interface représentant une scène dans le gestionnaire de scènes.
 * Toute classe implémentant cette interface doit définir le cycle de vie complet d'une scène.
 */
public interface Scene {

    /**
     * Méthode appelée lors de l'initialisation de la scène.
     * Utilisée pour charger les ressources et configurer l'état initial.
     */
    void init();

    /**
     * Méthode appelée à chaque mise à jour de la logique de la scène.
     * Utilisée pour gérer les événements, les mouvements, les états, etc.
     */
    void update();

    /**
     * Méthode appelée pour dessiner la scène à l'écran.
     *
     * @param g      l'objet Graphics utilisé pour le rendu
     * @param width  la largeur de la surface de rendu
     * @param height la hauteur de la surface de rendu
     */
    void render(Graphics g, int width, int height);

    /**
     * Méthode appelée pour libérer les ressources lorsque la scène est supprimée ou remplacée.
     */
    void dispose();
}
