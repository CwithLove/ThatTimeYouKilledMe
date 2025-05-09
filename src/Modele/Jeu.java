package Modele;

import java.awt.Point;

public class Jeu {
    public final static int TAILLE = 4;
    private Plateau past; // plateau past
    private Plateau present; // plateau present
    private Plateau future; // plateau future
    private Joueur joueur1; // joueur 1
    private Joueur joueur2; // joueur 2
    private Joueur joueurCourant; // nombre de tours
    private Piece pieceCourante; // piece courante


    // Constructeur de la classe Jeu
    public Jeu() {
        // Initialiser les joueurs 
        joueur1 = new Joueur("Blanc", 1, 4, Plateau.TypePlateau.PAST);
        joueur2 = new Joueur("Noir", 2, 4, Plateau.TypePlateau.FUTURE);
        
        // Initialiser les plateaux
        past = new Plateau(Plateau.TypePlateau.PAST, joueur1, joueur2); 
        present = new Plateau(Plateau.TypePlateau.PRESENT, joueur1, joueur2);
        future = new Plateau(Plateau.TypePlateau.FUTURE, joueur1, joueur2);

        // Le blanc commence
        joueurCourant = joueur1;
    }


    private void deplacerPiece(Piece pieceActuel, Plateau plt, Coup coup) {
        Point srcPosition = pieceActuel.getPosition();
        Point direction = coup.getDirection();
        
        int destLig = srcPosition.x + direction.x;
        int destCol = srcPosition.y + direction.y;

        // Verifier si la position de destination est en dehors des limites du plateau
        boolean isOutOfBounds = (destLig < 0 || destLig >= TAILLE || destCol < 0 || destCol >= TAILLE);

        Piece pieceAtDestination = null;
        if (!isOutOfBounds) {
            pieceAtDestination = plt.getPiece(destLig, destCol);
        }

        // Premier cas: La piece actuelle se déplace vers une case vide ou en dehors du plateau
        if (pieceAtDestination == null) {
            // Etre poussé hors du plateau
            if (isOutOfBounds) {
                plt.removePiece(srcPosition.x, srcPosition.y);
                // Diminue le nombre de pièces du joueur
                if (pieceActuel.getOwner().equals(joueur1)) {
                    plt.decBlancs();
                } else if (pieceActuel.getOwner().equals(joueur2)) {
                    plt.decNoirs();
                }
                // Mettre à jour la position de la pièce actuelle (pour surveillance)
                pieceActuel.setPosition(new Point(destLig, destCol));
            } else {
                // Déplacer la pièce actuelle vers la case vide
                plt.setPiece(pieceActuel, destLig, destCol);
                pieceActuel.setPosition(new Point(destLig, destCol));
                plt.removePiece(srcPosition.x, srcPosition.y);
            }
        }
        // Deuxième cas: La pièce actuelle se déplace vers une case occupée par une pièce adverse
        else if (!plt.estPareilPion(pieceAtDestination, pieceActuel)) {
            // Appel récursif pour pousser la pièce adverse
            deplacerPiece(pieceAtDestination, plt, coup);
            
            // Mettre à jour la position de la pièce actuelle
            plt.setPiece(pieceActuel, destLig, destCol);
            pieceActuel.setPosition(new Point(destLig, destCol));
            plt.removePiece(srcPosition.x, srcPosition.y);
        }
        // Troisième cas: La pièce actuelle se déplace vers une case occupée par une pièce de son propre joueur
        else if (plt.estPareilPion(pieceAtDestination, pieceActuel)) {
            // Paradoxe => Les deux pièces sont supprimées
            plt.removePiece(destLig, destCol); // Supprimer la pièce destination
            plt.removePiece(srcPosition.x, srcPosition.y); // Supprimer la pièce actuelle

            // Diminuer le nombre de pièces du joueur
            if (pieceActuel.getOwner().equals(joueur1)) {
                plt.decBlancs();
            } else if (pieceActuel.getOwner().equals(joueur2)) {
                plt.decNoirs();
            }
            
            if (pieceAtDestination.getOwner().equals(joueur1)) {
                plt.decBlancs();
            } else if (pieceAtDestination.getOwner().equals(joueur2)) {
                plt.decNoirs();
            }
        }
    }

    public void appliquerCoup(Coup coup, Joueur player, Plateau pastAC, Plateau presentAC, Plateau futureAC) {
        // Prendre la pièce à jouer
        Piece pieceCourant = coup.getPiece(); 

        // Vérifier si la pièce courante est nulle
        if (pieceCourant == null) {
            System.err.println("Lỗi: Quân cờ để thực hiện Coup không tồn tại.");
            return;
        }

        // Prendre le plateau courant
        Plateau pltCourant = coup.getPltCourant(); 
        // Position depart de la pièce courante
        Point pieceDepart = pieceCourant.getPosition(); 

        switch (coup.getTypeCoup()) {
            case MOVE:
                deplacerPiece(pieceCourant, pltCourant, coup);
                break;

            case CLONE:
                // Action CLONE : Créer une copie de la pièce sur un plateau "plus passé".
                // La copie apparaîtra sur PAST (si CLONE depuis PRESENT) ou PRESENT (si CLONE depuis FUTURE).
                // La pièce originale peut rester ou être supprimée en fonction du nombre de clones restants.

                Piece pieceClone = new Piece(player, pieceDepart);
                // Diminuer le nombre de clone => si le joueur a encore des clones
                boolean cloneSuccessful = player.declone();

                Plateau plateauPourCloner = null; // Plateau ou la pièce sera clonée
                Plateau plateauOriginalDePiece = pltCourant; // Plateau d'origine de la pièce courante

                if (plateauOriginalDePiece.getType() == Plateau.TypePlateau.PRESENT) {
                    plateauPourCloner = pastAC; // CLONE de PRESENT à PAST
                    player.setProchainPlateau(Plateau.TypePlateau.PAST); // Zone de destination suivante du joueur
                } else if (plateauOriginalDePiece.getType() == Plateau.TypePlateau.FUTURE) {
                    plateauPourCloner = presentAC; // CLONE de FUTURE à PRESENT
                    player.setProchainPlateau(Plateau.TypePlateau.PRESENT);
                } else {
                    System.err.println("Erreur: Impossible de CLONE depuis " + plateauOriginalDePiece.getType());
                    return;
                }

                // Verifier Paradoxe a la la position de destination du CLONE
                // Un paradoxe se produit si la pièce à cette position sur le plateau cible est de la même couleur que la pièce en cours de CLONE
                Piece pieceExistantADestination = plateauPourCloner.getPiece(pieceDepart.x, pieceDepart.y);
                if (plateauPourCloner.paradoxe(pieceExistantADestination, pieceCourant, 0, 0)) {
                    System.out.println("Paradoxe!");
                    
                    // Supprimer la pièce sur le plateau cible du CLONE (si elle existe)
                    if (pieceExistantADestination != null) {
                        if (pieceExistantADestination.getOwner().equals(joueur1)) plateauPourCloner.decBlancs();
                        else plateauPourCloner.decNoirs();
                        plateauPourCloner.removePiece(pieceDepart.x, pieceDepart.y);
                    }
                    // Supprimer la pièce d'origine de son plateau actuel (car elle "cause" le paradoxe)
                    if (plateauOriginalDePiece.getPiece(pieceDepart.x, pieceDepart.y) != null) {
                        if (pieceCourant.getOwner().equals(joueur1)) plateauOriginalDePiece.decBlancs();
                        else plateauOriginalDePiece.decNoirs();
                        plateauOriginalDePiece.removePiece(pieceDepart.x, pieceDepart.y);
                    }
                } else {
                    // Pas de paradoxe: Effectuer l'action CLONE
                    // Si la pièce de l'adversaire est à la position cible, la supprimer
                    if (pieceExistantADestination != null) {
                        if (pieceExistantADestination.getOwner().equals(joueur1)) plateauPourCloner.decBlancs();
                        else plateauPourCloner.decNoirs();
                        plateauPourCloner.removePiece(pieceDepart.x, pieceDepart.y); // Assurer la suppression si la pièce adverse est présente
                    }
                    
                    // Placer la pièce clonée sur le plateau cible
                    plateauPourCloner.setPiece(pieceCourant, pieceDepart.x, pieceDepart.y);
                    pieceCourant.setPosition(pieceDepart); // Mettre à jour la position de la pièce clonée
                    if (pieceCourant.getOwner().equals(joueur1)) plateauPourCloner.incBlancs();
                    else plateauPourCloner.incNoirs();

                    // Gérer la pièce originale sur son plateau actuel :
                    // Si cloneSuccessful (le joueur a encore des clones), laisser une copie de la pièce originale.
                    // Sinon, supprimer la pièce originale car le joueur a utilisé son dernier clone.
                    if (cloneSuccessful) {
                        plateauOriginalDePiece.setPiece(pieceClone, pieceDepart.x, pieceDepart.y);
                        // Mettre à jour la position de la pièce clonée
                    } else {
                        plateauOriginalDePiece.removePiece(pieceDepart.x, pieceDepart.y);
                        if (pieceCourant.getOwner().equals(joueur1)) plateauOriginalDePiece.decBlancs();
                        else plateauOriginalDePiece.decNoirs();
                    }
                }
                break;

            case JUMP:
                // Action JUMP : Déplacer une pièce vers un plateau "plus futur".
                // La pièce se déplacera vers PRESENT (si JUMP depuis PAST) ou FUTURE (si JUMP depuis PRESENT).

                Plateau plateauPourJump = null; // Plateau où la pièce sera déplacée
                Plateau plateauOriginalJump = pltCourant; // Plateau d'origine de la pièce courante

                if (plateauOriginalJump.getType() == Plateau.TypePlateau.PAST) {
                    plateauPourJump = presentAC; // JUMP từ PAST sang PRESENT
                    player.setProchainPlateau(Plateau.TypePlateau.PRESENT); // Khu vực đích tiếp theo của người chơi
                } else if (plateauOriginalJump.getType() == Plateau.TypePlateau.PRESENT) {
                    plateauPourJump = futureAC; // JUMP từ PRESENT sang FUTURE
                    player.setProchainPlateau(Plateau.TypePlateau.FUTURE); // Khu vực đích tiếp theo của người chơi
                } else {
                    System.err.println("Erreur: Impossible de JUMP depuis " + plateauOriginalJump.getType());
                    return;
                }

                // Vérifier Paradoxe à la position de destination du JUMP
                Piece pieceExistantAJumpDestination = plateauPourJump.getPiece(pieceDepart.x, pieceDepart.y);
                if (plateauPourJump.paradoxe(pieceExistantAJumpDestination, pieceCourant, 0, 0)) {
                    System.out.println("Paradoxe!");
                    // Supprimer la pièce à la position cible du JUMP (si elle existe)
                    if (pieceExistantAJumpDestination != null) {
                        if (pieceExistantAJumpDestination.getOwner().equals(joueur1)) plateauPourJump.decBlancs();
                        else plateauPourJump.decNoirs();
                        plateauPourJump.removePiece(pieceDepart.x, pieceDepart.y);
                    }
                    // Supprimer la pièce d'origine de son plateau actuel (car elle "cause" le paradoxe)
                    if (plateauOriginalJump.getPiece(pieceDepart.x, pieceDepart.y) != null) {
                        if (pieceCourant.getOwner().equals(joueur1)) plateauOriginalJump.decBlancs();
                        else plateauOriginalJump.decNoirs();
                        plateauOriginalJump.removePiece(pieceDepart.x, pieceDepart.y);
                    }
                } else {
                    // Pas de paradoxe : Effectuer l'action JUMP
                    // Si une pièce adverse est à la position cible, la supprimer
                    if (pieceExistantAJumpDestination != null) {
                        if (pieceExistantAJumpDestination.getOwner().equals(joueur1)) plateauPourJump.decBlancs();
                        else pieceExistantAJumpDestination.getOwner().equals(joueur2);
                        plateauPourJump.removePiece(pieceDepart.x, pieceDepart.y); // Assurer la suppression si la pièce adverse est présente
                    }
                    
                    // Placer la pièce courante sur le plateau cible
                    plateauPourJump.setPiece(pieceCourant, pieceDepart.x, pieceDepart.y);
                    pieceCourant.setPosition(pieceDepart); // Mettre a jour la position de la pièce courante
                    if (pieceCourant.getOwner().equals(joueur1)) plateauPourJump.incBlancs();
                    else plateauPourJump.incNoirs();

                    // Gérer la pièce originale sur son plateau actuel :
                    plateauOriginalJump.removePiece(pieceDepart.x, pieceDepart.y);
                    if (pieceCourant.getOwner().equals(joueur1)) plateauOriginalJump.decBlancs();
                    else plateauOriginalJump.decNoirs();
                }
                break;

            default:
                System.err.println("Coup non valide: " + coup.getTypeCoup());
                return; // Ne devrait pas se produire si estCoupValide a correctement vérifié
        }
        // Passer le tour après avoir effectué le COUP (après tous les cas)
        // REMARQUE IMPORTANTE : Dans un environnement multijoueur, la logique de changement de tour sera contrôlée par le GameServer.
        // Ce code est uniquement pour maintenir la cohérence du modèle lorsqu'il est exécuté de manière autonome.
        // setJoueurCourant(getJoueurCourant().equals(getJoueur1()) ? getJoueur2() : getJoueur1());
    }

    public boolean estCoupValide(Coup coup) {
        // Vérifier si le coup est valide
        if (coup == null || coup.getPiece() == null || coup.getPltCourant() == null) {
            System.err.println("Coup, Piece ou Plateau dans Coup est null.");
            return false;
        }

        Piece piece = coup.getPiece();
        Plateau currentPlateau = coup.getPltCourant();
        Joueur currentPlayer = getJoueurCourant(); // Récupérer le joueur courant

        // Vérifier si le joueur courant est le propriétaire de la pièce
        if (!piece.getOwner().equals(currentPlayer)) {
            System.out.println("Impossible de déplacer une pièce de l'adversaire.");
            return false;
        }

        // Vérifier si la pièce sélectionnée n'existe plus sur le plateau spécifié dans le coup
        // La meilleure façon est d'utiliser la position et le type de plateau pour récupérer la pièce depuis Jeu
        Piece actualPieceOnBoard = getPlateauByType(currentPlateau.getType()).getPiece(piece.getPosition().x, piece.getPosition().y);
        if (actualPieceOnBoard == null || actualPieceOnBoard != piece) {
            System.out.println("La pièce sélectionnée n'est plus à sa position initiale ou n'existe pas.");
            return false;
        }

        switch (coup.getTypeCoup()) {
            case MOVE:
            Point src = piece.getPosition();
            Point dir = coup.getDirection();
            int destLig = src.x + dir.x;
            int destCol = src.y + dir.y;

            // Si la destination est en dehors du plateau
            if (destLig < 0 || destLig >= TAILLE || destCol < 0 || destCol >= TAILLE) {
                // C'est le cas où la pièce est poussée hors du plateau. Toujours valide si la pièce appartient au joueur.
                // La logique de `deplacerPiece` gérera la réduction du nombre de pièces.
                return true; 
            } else {
                // Déplacement à l'intérieur du plateau
                Piece targetPiece = currentPlateau.getPiece(destLig, destCol);
                if (targetPiece != null && targetPiece.getOwner().equals(currentPlayer)) {
                System.out.println("Impossible de déplacer vers une case occupée par une de vos pièces.");
                return false; // Impossible de déplacer vers une case occupée par une de vos pièces
                }
                return true;
            }

            case JUMP:
            // Logique pour JUMP :
            // 1. Impossible de JUMP depuis FUTURE
            if (currentPlateau.getType() == Plateau.TypePlateau.FUTURE) {
                System.out.println("Impossible de JUMP depuis le plateau FUTURE.");
                return false;
            }
            // 2. La destination (PRESENT si depuis PAST, FUTURE si depuis PRESENT) ne doit pas contenir une pièce du joueur
            Plateau targetPlateau = null;
            if (currentPlateau.getType() == Plateau.TypePlateau.PAST) {
                targetPlateau = present;
            } else if (currentPlateau.getType() == Plateau.TypePlateau.PRESENT) {
                targetPlateau = future;
            }

            if (targetPlateau != null) {
                Piece targetPiece = targetPlateau.getPiece(piece.getPosition().x, piece.getPosition().y);
                if (targetPiece != null && targetPiece.getOwner().equals(currentPlayer)) {
                System.out.println("Impossible de JUMP vers une case occupée par une de vos pièces sur le plateau cible.");
                return false;
                }
            }
            return true;

            case CLONE:
            // Logique pour CLONE :
            // 1. Impossible de CLONE depuis PAST
            if (currentPlateau.getType() == Plateau.TypePlateau.PAST) {
                System.out.println("Impossible de CLONE depuis le plateau PAST.");
                return false;
            }
            // 2. Le joueur doit avoir encore des clones disponibles
            if (currentPlayer.getNbClones() <= 0) {
                System.out.println("Vous n'avez plus de clones disponibles.");
                return false;
            }
            // 3. La destination (PAST si depuis PRESENT, PRESENT si depuis FUTURE) ne doit pas contenir une pièce du joueur
            Plateau cloneTargetPlateau = null;
            if (currentPlateau.getType() == Plateau.TypePlateau.PRESENT) {
                cloneTargetPlateau = past;
            } else if (currentPlateau.getType() == Plateau.TypePlateau.FUTURE) {
                cloneTargetPlateau = present;
            }

            if (cloneTargetPlateau != null) {
                Piece targetPiece = cloneTargetPlateau.getPiece(piece.getPosition().x, piece.getPosition().y);
                if (targetPiece != null && targetPiece.getOwner().equals(currentPlayer)) {
                System.out.println("Impossible de CLONE vers une case occupée par une de vos pièces sur le plateau cible.");
                return false;
                }
            }
            return true;

            default:
            return false; // Type de coup non défini
        }
    }

    
    public int gameOver(Joueur joueur) {
        // Determiner le joueur adverse
        Joueur opponentJoueur = (joueur.equals(joueur1)) ? joueur2 : joueur1;

        int boardsWithOpponentPieces = 0;

        // Verifier le plateau passé
        if ((opponentJoueur.equals(joueur1) && past.getNbBlancs() > 0) ||
            (opponentJoueur.equals(joueur2) && past.getNbNoirs() > 0)) {
            boardsWithOpponentPieces++;
        }

        // Verifier le plateau présent
        if ((opponentJoueur.equals(joueur1) && present.getNbBlancs() > 0) ||
            (opponentJoueur.equals(joueur2) && present.getNbNoirs() > 0)) {
            boardsWithOpponentPieces++;
        }

        // Verifier le plateau future
        if ((opponentJoueur.equals(joueur1) && future.getNbBlancs() > 0) ||
            (opponentJoueur.equals(joueur2) && future.getNbNoirs() > 0)) {
            boardsWithOpponentPieces++;
        }

        // Si l'adversaire n'a au maximum qu'une pièce sur un plateau, le joueur courant gagne
        if (boardsWithOpponentPieces <= 1) {
            return joueur.getId(); // Current player wins
        }

        return 0; // Game continues
    }


    // Tous les getters et setters
    public Plateau getPast() {
        return past;
    }

    public Plateau getPresent() {
        return present;
    }

    public Plateau getFuture() {
        return future;
    }

    public Joueur getJoueur1() {
        return joueur1;
    }

    public Joueur getJoueur2() {
        return joueur2;
    }

    public Joueur getJoueurCourant() {
        return joueurCourant;
    }

    public Piece getPieceCourante() {
        return pieceCourante;
    }

    public void setPieceCourante(Piece pieceCourante) {
        this.pieceCourante = pieceCourante;
    }

    public Plateau getPlateauByType(Plateau.TypePlateau type) {
        switch (type) {
            case PAST: return past;
            case PRESENT: return present;
            case FUTURE: return future;
            default: return null; // This should never happen
        }
    }

    // Extraire l'état du jeu sous forme de chaîne
    public String getGameStateAsString() {
        StringBuilder gameState = new StringBuilder();

        // Current Player
        gameState.append("CP:").append(joueurCourant.getId()).append(";");

        // Clone Counts
        gameState.append("C1:").append(joueur1.getNbClones()).append(";");
        gameState.append("C2:").append(joueur2.getNbClones()).append(";");
 
        // Game Status and Winner
        // Verifier si le jeu est terminé
        int winnerId = 0;
        if (gameOver(joueur1) == joueur1.getId()) { // Nếu J1 thắng
            winnerId = joueur1.getId();
        } else if (gameOver(joueur2) == joueur2.getId()) { // Nếu J2 thắng
            winnerId = joueur2.getId();
        }

        if (winnerId == 0) {
            gameState.append("GS:IN_PROGRESS;W:0;");
        } else {
            gameState.append("GS:").append(winnerId == 1 ? "PLAYER1_WON" : "PLAYER2_WON").append(";");
            gameState.append("W:").append(winnerId).append(";");
        }
        
        // Board Data for Past, Present, Future
        gameState.append("P:").append(getPlateauString(past)).append(";");
        gameState.append("PR:").append(getPlateauString(present)).append(";");
        gameState.append("F:").append(getPlateauString(future));

        return gameState.toString();
    }

    // Phương thức helper để chuyển đổi một Plateau thành chuỗi 16 ký tự
    private String getPlateauString(Plateau plateau) {
        StringBuilder boardString = new StringBuilder();
        for (int row = 0; row < TAILLE; row++) {
            for (int col = 0; col < TAILLE; col++) {
                Piece piece = plateau.getPiece(row, col);
                if (piece == null) {
                    boardString.append("_");
                } else if (piece.getOwner().equals(joueur1)) {
                    boardString.append("1");
                } else if (piece.getOwner().equals(joueur2)) {
                    boardString.append("2");
                }
            }
        }
        return boardString.toString();
    }

    public void printGamePlay() {
        System.out.println("Next Area J1 : " + joueur1.getProchainPlateau().toString());
        System.out.println("Next Area J2 : " + joueur2.getProchainPlateau().toString());
        for (int i = 0; i < TAILLE; i++) {
            for (int j = 0; j < TAILLE*3 + 2; j++) {
                if (j < TAILLE) {
                    if (past.getPiece(i, j) != null) {
                        if (past.getPiece(i, j).getOwner().equals(joueur1)) {
                            System.out.print("[B]");
                        } else {
                            System.out.print("[N]");
                        }
                    } else {
                        System.out.print("[ ]");
                    }
                }

                if (j == TAILLE || j == TAILLE * 2 + 1) {
                    System.out.print("  ");
                }

                if (j > TAILLE && j < TAILLE * 2 + 1) {
                    Piece p = present.getPiece(i, j - TAILLE - 1);
                    if (p != null) {
                        if (p.getOwner().equals(joueur1)) {
                            System.out.print("[B]");
                        } else {
                            System.out.print("[N]");
                        }
                    } else {
                        System.out.print("[ ]");
                    }
                }

                if (j > TAILLE * 2 + 1) {
                    Piece p = future.getPiece(i, j - TAILLE * 2 - 2);
                    if (p != null) {
                        if (p.getOwner().equals(joueur1)) {
                            System.out.print("[B]");
                        } else {
                            System.out.print("[N]");
                        }
                    } else {
                        System.out.print("[ ]");
                    }
                }
            }
            System.out.println();
        }
    }

}   

