package SceneManager;

import java.awt.*;
import javax.swing.*;

/**
 * Gestionnaire de scènes permettant de contrôler l'affichage, la mise à jour,
 * et le cycle de vie des différentes scènes d'un jeu ou d'une application graphique.
 */
public class SceneManager {
    // Scène actuellement active
    private Scene currentScene;

    // Panneau graphique principal sur lequel les scènes sont rendues
    private JPanel gamePanel;

    /**
     * Constructeur du gestionnaire de scènes.
     *
     * @param gamePanel Le panneau Swing utilisé pour le rendu graphique.
     */
    public SceneManager(JPanel gamePanel) {
        this.gamePanel = gamePanel;
    }

    /**
     * Change la scène actuelle par une nouvelle scène.
     * Libère les ressources de l'ancienne scène si nécessaire.
     *
     * @param scene La nouvelle scène à afficher.
     */
    public void setScene(Scene scene) {
        if (currentScene != null) {
            currentScene.dispose(); // Nettoyage de l’ancienne scène
        }
        currentScene = scene;      // Affectation de la nouvelle scène
        currentScene.init();       // Initialisation de la nouvelle scène
    }

    /**
     * Met à jour la logique de la scène actuelle.
     * Cette méthode est appelée à chaque cycle du jeu.
     */
    public void update() {
        if (currentScene != null) {
            currentScene.update();
        }
    }

    /**
     * Effectue le rendu de la scène actuelle sur le contexte graphique fourni.
     *
     * @param g      Contexte graphique utilisé pour le dessin.
     * @param width  Largeur disponible pour le rendu.
     * @param height Hauteur disponible pour le rendu.
     */
    public void render(Graphics g, int width, int height) {
        if (currentScene != null) {
            currentScene.render(g, width, height);
        }
    }

    /**
     * Retourne le panneau de jeu utilisé par le gestionnaire de scènes.
     *
     * @return JPanel utilisé pour le rendu des scènes.
     */
    public JPanel getPanel() {
        return gamePanel;
    }

    /**
     * Retourne la scène actuellement active.
     *
     * @return Scène courante.
     */
    public Scene getCurrentScene() {
        return currentScene;
    }
}
