package Network;

import Modele.Coup;
import Modele.Couple;
import Modele.IAFields;
import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class IAminimax {

    private static class Memoisation {

        int evaluation;
        int profondeur;

        Memoisation(int evaluation, int profondeur) {
            this.evaluation = evaluation;
            this.profondeur = profondeur;
        }
    }

    private boolean iaLost = false;
    private boolean iaWon = false;
    private int iaId;

    private Jeu jeu;
    private final int PROFONDEUR_MAX = 5;
    private int difficulte = 100;
    private String mode = "";
    private Random r = new Random();
    private static HashMap<String, Memoisation> memoisation = new HashMap<>();
    ArrayList<Integer> poids = new ArrayList<>();

    //builder de l'ia
    public IAminimax(int diff, Jeu jeu) {
        if (diff > PROFONDEUR_MAX) {
            diff = PROFONDEUR_MAX;
        }
        this.difficulte = diff;
        this.jeu = jeu;
        // if (diff >= PROFONDEUR_MAX) {
        //     this.mode = "HARD";
        // } else if (diff >= (int) (PROFONDEUR_MAX * 0.5)) {
        //     this.mode = "MEDIUM";
        // } else {
        //     this.mode = "EASY";
        // }
        this.mode = "HARD";
        System.out.println("REMPLISSAGE...");
        for (int i = 0; i < 9; i++) {
            if (i == 0) {
                poids.add(1);
            } else {
                poids.add(0);
            }
            // poids.add(r.nextInt(15));
            // System.out.println(i + " : " + poids.get(i));
        }

        // poids.add(8);
        // poids.add(2);                
        // poids.add(7);
        // poids.add(4);
        // poids.add(9);
        // poids.add(6);
        // poids.add(7);
        // poids.add(5);
        // poids.add(10);
    }

    public IAFields<Piece, String, String, Plateau.TypePlateau> coupIA(Jeu gameState) {
        if (this.jeu == null && gameState != null) {
            this.jeu = gameState;
        }
        ArrayList<Couple<IAFields<Piece, String, String, Plateau.TypePlateau>, Integer>> lst_coup = new ArrayList<>();
        IAFields<Piece, String, String, Plateau.TypePlateau> best_coup = null;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        int best = Integer.MIN_VALUE;
        Joueur joueur = gameState.getJoueurCourant();
        this.iaId = joueur.getId();
        ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> tours = getTourPossible(joueur, gameState);
        /*
        for (int i = 0; i < tours.size(); i++) {
            System.out.println("DEBUG TOURS POSSIBLE : "+tours.get(i).getSecond()+", "+tours.get(i).getTroisieme()+", "+tours.get(i).getQuatrieme());
        }*/

        //Minimax de profondeur [difficulte] (max 3, 6 avec heuristique) avec elagage
        // System.out.println("--------MiniMax--------");
        // System.out.println("Profondeur: " + this.difficulte);
        for (IAFields<Piece, String, String, Plateau.TypePlateau> tour : tours) {
            // System.out.println("Tour " + this.difficulte + ": " + tour.getPremier().getPosition() + ", " + tour.getSecond() + ", " + tour.getTroisieme() + ", " + tour.getQuatrieme());
            Jeu jeuClone = new Jeu(gameState);
            if (tour.getPremier() != null && tour.getSecond() != null && tour.getTroisieme() != null) {
                Jeu sim = new Jeu(jeuClone);
                int x = (int) tour.getPremier().getPosition().getX();
                int y = (int) tour.getPremier().getPosition().getY();
                Piece pieceCourante = sim.getPlateauCourant().getPiece(x, y);
                if (pieceCourante == null) {
                    continue;
                }

                Coup coup1 = Coup.stringToCoup(pieceCourante, sim.getPlateauCourant(), tour.getSecond());
                if (coup1 == null) {
                    continue;
                }
                sim.appliquerCoup(coup1);

                Coup coup2 = Coup.stringToCoup(pieceCourante, sim.getPlateauCourant(), tour.getTroisieme());
                if (coup2 == null) {
                    jeuClone = sim;
                    continue;
                }
                sim.appliquerCoup(coup2);
                jeuClone = sim;
            }

            jeuClone.choisirPlateau(tour.getQuatrieme());
            jeuClone.joueurSuivant();

            Jeu jeuClone2 = new Jeu(jeuClone);
            int score = alphabeta(this.difficulte - 1, alpha, beta, false, jeuClone2);
            ////System.out.println("Pour le coup :"+coup+", on a le score (pas encore a jour) :"+score);
            lst_coup.add(new Couple<>(tour, score));

            if (score > best) {
                best = score;
                best_coup = tour;
                //System.out.println("BESTCOUP "+best_coup);
            }
            alpha = Math.max(alpha, score);

        }
        // System.out.println("--------Fin MiniMax--------");
        // System.out.println("Meilleur score pour cette profondeur: " + best);
        // System.out.println("--------------------------------------");

        ////System.out.println("Le meilleur coup est:"+best_coup.getPremier().getPosition().getX()+","+best_coup.getPremier().getPosition().getY()+","+best_coup.getSecond()+","+best_coup.getTroisieme()+","+best_coup.getQuatrieme());
        if (lst_coup.size() > 1) {
            int seuil = 0;
            if (this.mode.equals("HARD")) {
                seuil = best; // (int) (best * 0.95);
            } else if (this.mode.equals("MEDIUM")) {
                seuil = best;
            } else {
                seuil = best;
            }

            for (int i = 0; i < lst_coup.size(); i++) {
                if (lst_coup.get(i).getSecond() < seuil) {
                    lst_coup.remove(i);
                    i--;
                }

            
            ////System.out.println("COUUUP: "+lst_coup.get(i).getSecond());
            }

            // Parmi les coups restants, on prend le meilleur 
            if (!lst_coup.isEmpty()) {
                System.out.println("Nombre de coups possibles: " + lst_coup.size());
                // Pour chaque tour possible, on prend le meilleur heuristique
                best_coup = null;
                int bestHeuristique = Integer.MIN_VALUE;
                for (int i = 0; i < lst_coup.size(); i++) {
                    Jeu simCoup = new Jeu(gameState);
                    IAFields<Piece, String, String, Plateau.TypePlateau> tour = lst_coup.get(i).getPremier();
                    if (tour.getPremier() != null) {
                        // System.out.print("Tour " + profondeur + ":" + tour.getPremier().getPosition() + ", " + tour.getSecond() + ", " + tour.getTroisieme() + ", " + tour.getQuatrieme() + "||");
                        int x = (int) tour.getPremier().getPosition().getX();
                        int y = (int) tour.getPremier().getPosition().getY();
                        Piece pieceCourant = simCoup.getPlateauCourant().getPiece(x, y);
                        if (pieceCourant == null) {
                            continue;
                        }

                        Coup coup1 = Coup.stringToCoup(pieceCourant, simCoup.getPlateauCourant(), tour.getSecond());
                        if (coup1 == null) {
                            continue;
                        }
                        simCoup.appliquerCoup(coup1);

                        Coup coup2 = Coup.stringToCoup(pieceCourant, simCoup.getPlateauCourant(), tour.getTroisieme());
                        if (coup2 != null) {
                            simCoup.appliquerCoup(coup2);
                        }

                        simCoup.choisirPlateau(tour.getQuatrieme());
                    } else {
                        simCoup.choisirPlateau(tour.getQuatrieme());
                    }
                    simCoup.joueurSuivant();

                    int heuristique = heuristique(simCoup, false);
                    if (heuristique > bestHeuristique) {
                        bestHeuristique = heuristique;
                        best_coup = tour;
                    }
                }
                // Couple<IAFields<Piece, String, String, Plateau.TypePlateau>, Integer> unMeilleurCoup = lst_coup.get(r.nextInt(lst_coup.size()));
                // best_coup = unMeilleurCoup.getPremier();
            }
        }

        if (best_coup == null) {
            return null;
        }
        // System.out.println("CONFIGURATIONS H:");
        // for (int i = 0; i < 9; i++) {
        //     System.out.println(i + " : " + poids.get(i));
        // }

        return best_coup;
    }

    private int alphabeta(int profondeur, int alpha, int beta, boolean tourIA, Jeu clone) {
        /*
        String hash = getHash(clone);

        Memoisation memoisation1 = memoisation.get(hash);

        if (memoisation1 != null && memoisation1.profondeur >= profondeur) {
            return memoisation1.evaluation;
        }*/

        if (profondeur <= 0) {
            int score = heuristique(clone, tourIA);
            // System.out.println("Heuristique score: " + score);
            //memoisation.put(hash, new Memoisation(score, profondeur));
            return score;
        }

        // System.out.println("\n--------alphaBeta--------");
        // System.out.println("Profondeur: " + profondeur + ", Tour " +  (tourIA ? "IA" : "Adversaire"));
        ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> tours = null;
        tours = getTourPossible(clone.getJoueurCourant(), clone);

        if (tours.isEmpty()) {
            if (iaLost) {
                // System.out.println("IA a perdu" + -1000000 + profondeur);
                return -1000000 + profondeur;
            } else if (iaWon) {
                // System.out.println("IA a gagné" + (1000000 - profondeur));
                return 1000000 - profondeur;
            }
            this.iaLost = false;
            this.iaWon = false;
        }

        //trierToursParHeuristique(tours, tourIA, clone);
        int best = tourIA ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (IAFields<Piece, String, String, Plateau.TypePlateau> tour : tours) {
            Jeu jeuClone = new Jeu(clone);
            if (tour.getPremier() != null) {
                // System.out.print("Tour " + profondeur + ":" + tour.getPremier().getPosition() + ", " + tour.getSecond() + ", " + tour.getTroisieme() + ", " + tour.getQuatrieme() + "||");
                int x = (int) tour.getPremier().getPosition().getX();
                int y = (int) tour.getPremier().getPosition().getY();
                Piece pieceCourant = jeuClone.getPlateauCourant().getPiece(x, y);
                if (pieceCourant == null) {
                    continue;
                }

                Coup coup1 = Coup.stringToCoup(pieceCourant, jeuClone.getPlateauCourant(), tour.getSecond());
                if (coup1 == null) {
                    continue;
                }
                jeuClone.appliquerCoup(coup1);

                Coup coup2 = Coup.stringToCoup(pieceCourant, jeuClone.getPlateauCourant(), tour.getTroisieme());
                if (coup2 != null) {
                    jeuClone.appliquerCoup(coup2);
                }

                jeuClone.choisirPlateau(tour.getQuatrieme());
                jeuClone.joueurSuivant();

                Jeu jeuClone2 = new Jeu(jeuClone);
                if (tourIA) {
                    best = Math.max(best, alphabeta(profondeur - 1, alpha, beta, false, jeuClone2));
                    alpha = Math.max(alpha, best);
                } else {
                    best = Math.min(best, alphabeta(profondeur - 1, alpha, beta, true, jeuClone2));
                    beta = Math.min(beta, best);
                }
            } else {
                jeuClone.choisirPlateau(tour.getQuatrieme());
                jeuClone.joueurSuivant();
                Jeu jeuClone2 = new Jeu(jeuClone);
                if (tourIA) {
                    best = Math.max(best, alphabeta(profondeur - 1, alpha, beta, false, jeuClone2));
                    alpha = Math.max(alpha, best);
                } else {
                    best = Math.min(best, alphabeta(profondeur - 1, alpha, beta, true, jeuClone2));
                    beta = Math.min(beta, best);
                }
            }

            if (beta <= alpha) {
                break;
            }
        }
        // System.out.println("Fin de la profondeur: " + profondeur + ", Meilleur score: " + best);
        // System.out.println("--------------------------------------");
        //memoisation.put(hash, new Memoisation(best, profondeur));
        return best;
    }

    // heuristique
    private int heuristique(Jeu jeu, boolean tourIA) {
        Joueur joueur = jeu.getJoueurCourant();
        Joueur opponent = null;
        if (joueur.getId() == 1) {
            opponent = jeu.getJoueur2();
        } else {
            opponent = jeu.getJoueur1();
        }
        if (!tourIA) {
            joueur = opponent;
            opponent = jeu.getJoueurCourant();
        }

        return 25 * hMateriel(jeu, joueur)
                + 40 * hControlePlateaux(jeu, joueur)
                + 3 * hPiecesAdjacentes(jeu, joueur)
                - 2 * hDiffPionEtClone(jeu, joueur);
    }

    // Pour chaque piece en plus de l'adversaire, on ajoute un point
    private int hMateriel(Jeu jeu, Joueur ia) {
        // System.out.println("DEBUG hMateriel " + ia.getNom());
        int score = 0;
        for (int i = 0; i < jeu.getPlateauCourant().getSize(); i++) {
            for (int j = 0; j < jeu.getPlateauCourant().getSize(); j++) {
                Piece piece = jeu.getPlateauCourant().getPiece(i, j);
                if (piece != null && piece.getOwner().equals(ia)) {
                    score += 1;
                } else if (piece != null && !piece.getOwner().equals(ia)) {
                    score -= 1;
                }
            }
        }
        // System.out.println("DEBUG hMateriel score: " + score);
        return score;
    }

    // Pour chaque plateau, on ajoute un point si l'ia controle le plateau, on en retire un si l'adversaire le controle
    private int hControlePlateaux(Jeu jeu, Joueur ia) {
        // System.out.println("DEBUG hControlePlateaux " + ia.getNom());
        int score = 0;
        if (ia.getId() == 1) {
            score += jeu.getPast().getNbBlancs() > 0 ? 1 : 0;
            score += jeu.getPresent().getNbBlancs() > 0 ? 1 : 0;
            score += jeu.getFuture().getNbBlancs() > 0 ? 1 : 0;
            // score -= jeu.getPast().getNbNoirs() > 0 ? 1 : 0;
            // score -= jeu.getPresent().getNbNoirs() > 0 ? 1 : 0;
            // score -= jeu.getFuture().getNbNoirs() > 0 ? 1 : 0;
        } else {
            score += jeu.getPast().getNbNoirs() > 0 ? 1 : 0;
            score += jeu.getPresent().getNbNoirs() > 0 ? 1 : 0;
            score += jeu.getFuture().getNbNoirs() > 0 ? 1 : 0;
            // score -= jeu.getPast().getNbBlancs() > 0 ? 1 : 0;
            // score -= jeu.getPresent().getNbBlancs() > 0 ? 1 : 0;
            // score -= jeu.getFuture().getNbBlancs() > 0 ? 1 : 0;
        }
        // System.out.println("DEBUG hControlePlateaux score: " + score);
        return score;
    }

    // Pour chaque piece adjacente a une piece de l'ia, on ajoute un point
    private int hPiecesAdjacentes(Jeu jeu, Joueur ia) {
        int score = 0;
        // System.out.println("DEBUG hPiecesAdjacentes " + ia.getNom());
        for (int i = 0; i < jeu.getPlateauCourant().getSize(); i++) {
            for (int j = 0; j < jeu.getPlateauCourant().getSize(); j++) {
                Piece piece = jeu.getPlateauCourant().getPiece(i, j);
                if (piece != null && piece.getOwner().equals(ia)) {
                    if (i > 0 && jeu.getPlateauCourant().getPiece(i - 1, j) != null && jeu.getPlateauCourant().getPiece(i - 1, j).getOwner().equals(ia)) {
                        score -= 1;
                    }
                    if (i < jeu.getPlateauCourant().getSize() - 1 && jeu.getPlateauCourant().getPiece(i + 1, j) != null && jeu.getPlateauCourant().getPiece(i + 1, j).getOwner().equals(ia)) {
                        score -= 1;
                    }
                    if (j > 0 && jeu.getPlateauCourant().getPiece(i, j - 1) != null && jeu.getPlateauCourant().getPiece(i, j - 1).getOwner().equals(ia)) {
                        score -= 1;
                    }
                    if (j < jeu.getPlateauCourant().getSize() - 1 && jeu.getPlateauCourant().getPiece(i, j + 1) != null && jeu.getPlateauCourant().getPiece(i, j + 1).getOwner().equals(ia)) {
                        score -= 1;
                    }
                }
            }
        }
        // System.out.println("DEBUG hPiecesAdjacentes score: " + score);
        return score;
    }

    // Pour chaque difference entre les pieces de l'ia sur le plateau et celles dans les NBclones, on enleve un point
    private int hDiffPionEtClone(Jeu jeu, Joueur ia) {
        // System.out.println("DEBUG hDiffPionEtClone " + ia.getNom());
        int score = 0;
        if (ia.getId() == 1) {
            score += jeu.getPresent().getNbBlancs() + jeu.getPast().getNbBlancs() + jeu.getFuture().getNbBlancs();
            score -= jeu.getJoueur1().getNbClones();
        } else {
            score += jeu.getPresent().getNbNoirs() + jeu.getPast().getNbNoirs() + jeu.getFuture().getNbNoirs();
            score -= jeu.getJoueur2().getNbClones();
        }
        // System.out.println("DEBUG hDiffPionEtClone score: " + score);
        return Math.abs(score);
    }

    private ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> getTourPossible(Joueur joueur, Jeu clone) {
        int gameState;
        if ((gameState = clone.gameOver(joueur)) != 0) {
            if (gameState == this.iaId) {
                this.iaWon = true;
            } else {
                this.iaLost = true;
            }
            return new ArrayList<>();
        }
        ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> listeCoups = new ArrayList<>();
        IAFields<Piece, String, String, Plateau.TypePlateau> coup;
        ArrayList<Piece> pieces = listePieces(clone);
        if (!pieces.isEmpty()) {
            for (Piece piece : pieces) {
                //sauvegarde de la position de la piece
                int posx = (int) piece.getPosition().getX();
                int posy = (int) piece.getPosition().getY();
                ////System.out.println("DEBUG Etat GAME clone: " + clone.getGameStateAsString());

                ArrayList<Coup> coups = clone.getCoupPossibles(clone.getPlateauCourant(), piece);
                if (coups == null) {
                    //System.out.println("IAMinimax: coups null");
                    continue;
                }

                for (Coup coup1 : coups) {
                    Jeu jeuClone1 = new Jeu(clone);
                    Piece p2 = jeuClone1.getPlateauCourant().getPiece((int) coup1.getPiece().getPosition().getX(), (int) coup1.getPiece().getPosition().getY());

                    Coup newCoup1 = new Coup(jeuClone1.getPlateauByType(clone.getPlateauCourant().getType()).getPiece(posx, posy), jeuClone1.getPlateauCourant(), coup1.getTypeCoup());
                    //traduction en coup et application
                    jeuClone1.appliquerCoup(newCoup1);

                    ArrayList<Coup> coups2 = jeuClone1.getCoupPossibles(jeuClone1.getPlateauCourant(), p2);

                    if (coups2.isEmpty()) {
                        //System.out.println("IAMinimax: coups 2 null");
                        for (Plateau.TypePlateau plateau : PlateauValide(joueur.getProchainPlateau())) {
                            coup = new IAFields<>(piece, coup1.getTypeCoup().name(), null, plateau);
                            listeCoups.add(coup);
                        }
                    } else {
                        for (Coup coup2 : coups2) { // ERREUR, si on jump ou clone, ça change le plateau courant
                            for (Plateau.TypePlateau plateau : PlateauValide(joueur.getProchainPlateau())) {
                                coup = new IAFields<>(piece, coup1.getTypeCoup().name(), coup2.getTypeCoup().name(), plateau);
                                ////System.out.println("DEBUG "+coup+", "+posx+" "+posy);
                                listeCoups.add(coup);
                            }
                        }
                    }

                }
                piece.setPosition(new Point(posx, posy));
            }
        } else {
            //System.out.println("IAMinimax: pieces null");
            for (Plateau.TypePlateau plateau : PlateauValide(joueur.getProchainPlateau())) {
                coup = new IAFields<>(null, null, null, plateau);
                listeCoups.add(coup);
            }
            //System.out.println("Liste des coups possibles: " + listeCoups);
        }

        return listeCoups;
    }

    private ArrayList<Piece> listePieces(Jeu jeu) {
        ArrayList<Piece> listePieces = new ArrayList<>();
        Piece tmp = null;
        for (int i = 0; i < jeu.getPlateauCourant().getSize(); i++) {
            for (int j = 0; j < jeu.getPlateauCourant().getSize(); j++) {
                if (jeu.getPlateauCourant().getPiece(i, j) != null) {
                    if (jeu.getPlateauCourant().getPiece(i, j).getOwner().equals(jeu.getJoueurCourant())) {
                        tmp = jeu.getPlateauCourant().getPiece(i, j);
                        listePieces.add(tmp);
                    }
                }
            }
        }
        ////System.out.println("LISTE DES PIECES DU JOUEUR "+joueur.getNom()+", au plateau "+plateauCourant.plateauToString()+" : "+listePieces.size());
        return listePieces;
    }

    private ArrayList<Plateau.TypePlateau> PlateauValide(Plateau.TypePlateau precedent) {
        ArrayList<Plateau.TypePlateau> candidats = new ArrayList<>();

        if (precedent != Plateau.TypePlateau.PAST) {
            candidats.add(Plateau.TypePlateau.PAST);
        }
        if (precedent != Plateau.TypePlateau.PRESENT) {
            candidats.add(Plateau.TypePlateau.PRESENT);
        }
        if (precedent != Plateau.TypePlateau.FUTURE) {
            candidats.add(Plateau.TypePlateau.FUTURE);
        }

        return candidats;
    }

}
