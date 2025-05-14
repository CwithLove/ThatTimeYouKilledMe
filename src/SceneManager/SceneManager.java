package SceneManager;

import java.awt.*;
import javax.swing.*;

public class SceneManager {
    // Scène actuelle en cours d'utilisation
    private Scene currentScene;
    // Panneau de jeu utilisé pour afficher les scènes
    private JPanel gamePanel;
    
    // Constructeur qui initialise le gestionnaire de scènes avec un panneau de jeu
    public SceneManager(JPanel gamePanel) {
        this.gamePanel = gamePanel;
    }
    
    // Définit une nouvelle scène et libère les ressources de l'ancienne scène si elle existe
    public void setScene(Scene scene) {
        if (currentScene != null) {
            currentScene.dispose(); // Libère les ressources de la scène actuelle
        }
        currentScene = scene; // Définit la nouvelle scène
        currentScene.init(); // Initialise la nouvelle scène
    }

    // Met à jour la scène actuelle (appelée à chaque cycle de jeu)
    public void update() {
        if (currentScene != null) {
            currentScene.update();
        }
    }

    // Rendu graphique de la scène actuelle
    public void render(Graphics g, int width, int height) {
        if (currentScene != null) {
            currentScene.render(g, width, height);
        }
    }

    // Retourne le panneau de jeu associé au gestionnaire de scènes
    public JPanel getPanel() {
        return gamePanel;
    }

    // Retourne la scène actuelle
    public Scene getCurrentScene() {
        return currentScene;
    }
}
