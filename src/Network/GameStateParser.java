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

            // Vérifier et traiter le format spécial : etapeCoup:n|game_state
            String processedGameState = gameStateString;
            if (gameStateString.startsWith("etapeCoup:")) {
                int separatorIndex = gameStateString.indexOf('|');
                if (separatorIndex > 0) {
                    String etapeCoupPart = gameStateString.substring(0, separatorIndex);
                    String etapeCoupValue = etapeCoupPart.substring("etapeCoup:".length());
                    try {
                        int etapeCoup = Integer.parseInt(etapeCoupValue);
                        jeuToUpdate.setEtapeCoup(etapeCoup);
                        System.out.println("GameStateParser: etapeCoup mis à jour à " + etapeCoup + " (format spécial)");
                    } catch (NumberFormatException e) {
                        System.err.println("GameStateParser: Valeur d'etapeCoup invalide: " + etapeCoupValue);
                    }
                    // Traiter la partie restante de l'état du jeu
                    processedGameState = gameStateString.substring(separatorIndex + 1);
                }
            }

            // Format attendu de la chaîne : etapeCoup:2;P1:PAST|PRESENT|FUTURE;P2:PAST|PRESENT|FUTURE;JC:1;C1:4;C2:4;P:...;PR:...;F:...
            String[] parts = processedGameState.split(";");
            Map<String, String> gameStateMap = new HashMap<>();
            for (String part : parts) {

                String[] keyValue = part.split(":", 2);
                if (keyValue.length == 2) {
                    gameStateMap.put(keyValue[0], keyValue[1]);
                }
                
                if (part.startsWith("etapeCoup:")) {
                    try {
                        int etapeCoup = Integer.parseInt(part.substring("etapeCoup:".length()));
                        jeuToUpdate.setEtapeCoup(etapeCoup);
                        System.out.println("GameStateParser: etapeCoup mis à jour à " + etapeCoup + " (format standard)");
                    } catch (NumberFormatException e) {
                        System.err.println("GameStateParser: Valeur d'etapeCoup invalide dans: " + part);
                    }
                } else if (part.startsWith("JC:")) {
                    int joueurId = Integer.parseInt(part.substring(3));
                    if (joueurId == 1) {
                        jeuToUpdate.setJoueurCourant(jeuToUpdate.getJoueur1());
                        System.out.println("GameStateParser: Joueur courant défini sur Joueur 1");
                    } else if (joueurId == 2) {
                        jeuToUpdate.setJoueurCourant(jeuToUpdate.getJoueur2());
                        System.out.println("GameStateParser: Joueur courant défini sur Joueur 2");
                    }
                }
                else if (part.startsWith("P1:")) {
                    jeuToUpdate.getJoueur1().setProchainPlateau(Plateau.TypePlateau.valueOf(part.substring(3)));
                } else if (part.startsWith("P2:")) {
                    jeuToUpdate.getJoueur2().setProchainPlateau(Plateau.TypePlateau.valueOf(part.substring(3)));
                } else if (part.startsWith("P:")) {
                    updatePlateau(jeuToUpdate.getPast(), part.substring(2), jeuToUpdate);
                } else if (part.startsWith("PR:")) {
                    updatePlateau(jeuToUpdate.getPresent(), part.substring(3), jeuToUpdate);
                } else if (part.startsWith("F:")) {
                    updatePlateau(jeuToUpdate.getFuture(), part.substring(2), jeuToUpdate);
                } else if (part.startsWith("PC:")) {
                    System.out.println(part);
                    String subParts[] = part.split(":", 3);

                    for (int i = 0; i < subParts.length; i++) {
                        System.out.println("subParts[" + i + "] = " + subParts[i]);
                    }
                    if (subParts.length == 2 && "null".equals(subParts[1])) {
                        jeuToUpdate.setPieceCourante(null);
                    } else if (subParts.length == 3) {
                        try {
                            Plateau.TypePlateau type = Plateau.TypePlateau.valueOf(subParts[1]);
                            int posXY = Integer.parseInt(subParts[2]);
                            int posX = posXY / 10; // Division entière pour obtenir la coordonnée X
                            int posY = posXY % 10; // Modulo pour obtenir la coordonnée Y
                            switch (type) {
                                case PAST:
                                    jeuToUpdate.setPieceCourante(jeuToUpdate.getPast().getPiece(posX, posY));
                                    break;
                                case PRESENT:
                                    jeuToUpdate.setPieceCourante(jeuToUpdate.getPresent().getPiece(posX, posY));
                                    break;
                                case FUTURE:
                                    jeuToUpdate.setPieceCourante(jeuToUpdate.getFuture().getPiece(posX, posY));
                                    break;
                            }

                        } catch (Exception e) {
                            System.err.println("GameStateParser: Erreur lors de la mise à jour du plateau courant: " + e.getMessage());
                        }
                    }
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
            jeuToUpdate.setPlateauCourant(jeuToUpdate.getJoueurCourant().getProchainPlateau());

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
