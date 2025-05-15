package Network;

import Modele.Jeu;
import Modele.Piece;
import Modele.Plateau;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

public class GameStateParser {

    /**
     * Analyse une chaîne représentant l'état du jeu et met à jour l'objet Jeu
     * fourni.
     *
     * @param jeuToUpdate L'objet Jeu à mettre à jour.
     * @param gameStateString La chaîne représentant l'état du jeu provenant du
     * serveur.
     */
    public static void parseAndUpdateJeu(Jeu jeuToUpdate, String gameStateString) {
        try {
            if (jeuToUpdate == null || gameStateString == null || gameStateString.isEmpty()) {
                System.err.println("GameStateParser : jeuToUpdate ou gameStateString invalide.");
                return;
            }

            // Format attendu de la chaîne : CP:1;C1:4;C2:4;GS:IN_PROGRESS;W:0;P:________;PR:________;F:________
            String[] parts = gameStateString.split(";");
            Map<String, String> gameStateMap = new HashMap<>();
            for (String part : parts) {
                if (part.startsWith("JC:")) {
                    int joueurId = Integer.parseInt(part.substring(3));
                    if (joueurId == 1) {
                        jeuToUpdate.setJoueurCourant(jeuToUpdate.getJoueur1());
                        System.out.println("GameStateParser: Joueur courant défini sur Joueur 1");
                    } else if (joueurId == 2) {
                        jeuToUpdate.setJoueurCourant(jeuToUpdate.getJoueur2());
                        System.out.println("GameStateParser: Joueur courant défini sur Joueur 2");
                    }
                } else if (part.startsWith("P:")) {
                    updatePlateau(jeuToUpdate.getPast(), part.substring(2), jeuToUpdate);
                } else if (part.startsWith("PR:")) {
                    updatePlateau(jeuToUpdate.getPresent(), part.substring(3), jeuToUpdate);
                } else if (part.startsWith("F:")) {
                    updatePlateau(jeuToUpdate.getFuture(), part.substring(2), jeuToUpdate);
                }
            }

            // Mettre à jour le joueur courant
            // Supposons que Joueur 1 et Joueur 2 dans jeuToUpdate ont été initialisés avec les bons ID (1 et 2)
            int currentPlayerId = Integer.parseInt(gameStateMap.getOrDefault("CP", "0"));
            if (currentPlayerId == jeuToUpdate.getJoueur1().getId()) {
                jeuToUpdate.setJoueurCourant(jeuToUpdate.getJoueur1());
            } else if (currentPlayerId == jeuToUpdate.getJoueur2().getId()) {
                jeuToUpdate.setJoueurCourant(jeuToUpdate.getJoueur2());
            } else if (currentPlayerId != 0) { // Loguer une erreur uniquement si l'ID est différent de 0 et ne correspond pas
                System.err.println("GameStateParser : ID du joueur courant (" + currentPlayerId + ") invalide dans la chaîne d'état.");
            }

            // Mettre à jour le nombre de clones
            jeuToUpdate.getJoueur1().setNbClones(Integer.parseInt(gameStateMap.getOrDefault("C1", String.valueOf(jeuToUpdate.getJoueur1().getNbClones()))));
            jeuToUpdate.getJoueur2().setNbClones(Integer.parseInt(gameStateMap.getOrDefault("C2", String.valueOf(jeuToUpdate.getJoueur2().getNbClones()))));

            // Les informations sur l'état du jeu (GS) et le gagnant (W) peuvent être traitées séparément
            // via un listener si le serveur envoie un code GAGNE/PERDU.

            System.out.println("GameStateParser: Analyse de l'état du jeu terminée, ID du joueur courant : " + 
                (jeuToUpdate.getJoueurCourant() != null ? jeuToUpdate.getJoueurCourant().getId() : "non défini"));
        } catch (Exception e) {
            System.err.println("GameStateParser: Erreur d'analyse - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Met à jour un Plateau spécifique à partir d'une chaîne de 16 caractères.
     *
     * @param mainGameInstance Instance principale de Jeu pour obtenir les
     * références des Joueurs.
     * @param plateauToUpdate Plateau à mettre à jour.
     * @param boardString Chaîne de 16 caractères représentant le plateau.
     */
    private static void updatePlateau(Plateau plateau, String data, Jeu jeu) {
        int size = plateau.getSize();
        // Vérifier que la longueur des données est correcte (16 pour un plateau 4x4)
        if (data.length() != size * size) {
            System.err.println("GameStateParser: Longueur des données du plateau incorrecte - " + 
                              plateau.getType() + " nécessite " + (size * size) + 
                              " caractères, mais reçu " + data.length());
            return;
        }

        // Vider le plateau actuel
        plateau.clearPieces();
        
        // Reconstruire le plateau à partir des données
        int index = 0;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                char c = data.charAt(index++);
                if (c == '1') {
                    Piece piece = new Piece(jeu.getJoueur1(), new Point(i, j));
                    plateau.setPiece(piece, i, j);
                } else if (c == '2') {
                    Piece piece = new Piece(jeu.getJoueur2(), new Point(i, j));
                    plateau.setPiece(piece, i, j);
                }
            }
        }
        
        // Mettre à jour le nombre de pièces sur le plateau
        plateau.updatePieceCount();
        
        System.out.println("GameStateParser: Plateau " + plateau.getType() + " mis à jour avec succès");
    }
}
