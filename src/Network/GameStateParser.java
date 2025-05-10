package Network;

import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

public class GameStateParser {

    /**
     * Analyse une chaîne représentant l'état du jeu et met à jour l'objet Jeu fourni.
     * @param jeuToUpdate L'objet Jeu à mettre à jour.
     * @param gameStateString La chaîne représentant l'état du jeu provenant du serveur.
     */
    public static void parseAndUpdateJeu(Jeu jeuToUpdate, String gameStateString) {
        if (jeuToUpdate == null || gameStateString == null || gameStateString.isEmpty()) {
            System.err.println("GameStateParser : jeuToUpdate ou gameStateString invalide.");
            return;
        }

        // Format attendu de la chaîne : CP:1;C1:4;C2:4;GS:IN_PROGRESS;W:0;P:________;PR:________;F:________
        String[] parts = gameStateString.split(";");
        Map<String, String> gameStateMap = new HashMap<>();
        for (String part : parts) {
            String[] keyValue = part.split(":", 2); // Séparer la clé et la valeur
            if (keyValue.length == 2) {
                gameStateMap.put(keyValue[0], keyValue[1]);
            } else {
                System.err.println("GameStateParser : Partie de l'état invalide : " + part);
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

        // Mettre à jour l'état des Plateaux
        updatePlateauFromString(jeuToUpdate, jeuToUpdate.getPast(), gameStateMap.get("P"));
        updatePlateauFromString(jeuToUpdate, jeuToUpdate.getPresent(), gameStateMap.get("PR"));
        updatePlateauFromString(jeuToUpdate, jeuToUpdate.getFuture(), gameStateMap.get("F"));

        // Les informations sur l'état du jeu (GS) et le gagnant (W) peuvent être traitées séparément
        // via un listener si le serveur envoie un code GAGNE/PERDU.
    }

    /**
     * Met à jour un Plateau spécifique à partir d'une chaîne de 16 caractères.
     * @param mainGameInstance Instance principale de Jeu pour obtenir les références des Joueurs.
     * @param plateauToUpdate Plateau à mettre à jour.
     * @param boardString Chaîne de 16 caractères représentant le plateau.
     */
    private static void updatePlateauFromString(Jeu mainGameInstance, Plateau plateauToUpdate, String boardString) {
        if (plateauToUpdate == null || boardString == null || boardString.length() != Jeu.TAILLE * Jeu.TAILLE) {
            System.err.println("GameStateParser : Chaîne du plateau invalide ou taille incorrecte pour le plateau " +
                               (plateauToUpdate != null ? plateauToUpdate.getType() : "NULL"));
            return;
        }

        // Supprimer toutes les pièces existantes et réinitialiser les compteurs
        plateauToUpdate.resetCounts(); // Assurer la réinitialisation des compteurs de pièces sur le plateau
        for (int r = 0; r < Jeu.TAILLE; r++) {
            for (int c = 0; c < Jeu.TAILLE; c++) {
                plateauToUpdate.removePiece(r, c); // Nettoyer le plateau
            }
        }

        // Placer les nouvelles pièces en fonction de la chaîne
        for (int i = 0; i < Jeu.TAILLE; i++) {
            for (int j = 0; j < Jeu.TAILLE; j++) {
                char pieceChar = boardString.charAt(i * Jeu.TAILLE + j);
                Piece newPiece = null;
                Point piecePosition = new Point(i, j);

                if (pieceChar == '1') { // Pièce du Joueur 1
                    newPiece = new Piece(mainGameInstance.getJoueur1(), piecePosition);
                } else if (pieceChar == '2') { // Pièce du Joueur 2
                    newPiece = new Piece(mainGameInstance.getJoueur2(), piecePosition);
                }

                if (newPiece != null) {
                    plateauToUpdate.setPiece(newPiece, i, j); // Placer la nouvelle pièce
                    // Mettre à jour le compteur de pièces sur le Plateau
                    if (newPiece.getOwner().equals(mainGameInstance.getJoueur1())) {
                        plateauToUpdate.incBlancs();
                    } else if (newPiece.getOwner().equals(mainGameInstance.getJoueur2())) {
                        plateauToUpdate.incNoirs();
                    }
                }
            }
        }
    }
}
