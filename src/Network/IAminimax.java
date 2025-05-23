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

    private Jeu jeu;
    private final int PROFONDEUR_MAX = 6;
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
        if (diff >= PROFONDEUR_MAX) {
            this.mode = "HARD";
        } else if (diff >= (int) (PROFONDEUR_MAX * 0.5)) {
            this.mode = "MEDIUM";
        } else {
            this.mode = "EASY";
        }
        System.out.println("REMPLISSAGE...");
        for (int i = 0; i < 9; i++) {
            poids.add(r.nextInt(15));
            System.out.println(i + " : " + poids.get(i));
        }
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
        ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> tours = getTourPossible(joueur, gameState);
        /*
        for (int i = 0; i < tours.size(); i++) {
            System.out.println("DEBUG TOURS POSSIBLE : "+tours.get(i).getSecond()+", "+tours.get(i).getTroisieme()+", "+tours.get(i).getQuatrieme());
        }*/

        //Minimax de profondeur [difficulte] (max 3, 6 avec heuristique) avec elagage
        for (IAFields<Piece, String, String, Plateau.TypePlateau> tour : tours) {
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
            int score = alphabeta(this.difficulte - 1, alpha, beta, false, jeuClone);
            ////System.out.println("Pour le coup :"+coup+", on a le score (pas encore a jour) :"+score);
            lst_coup.add(new Couple<>(tour, score));

            if (score > best) {
                best = score;
                best_coup = tour;
                //System.out.println("BESTCOUP "+best_coup);
            }
            alpha = Math.max(alpha, score);

        }

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

            if (!lst_coup.isEmpty()) {
                //System.out.println("DEBUG -> "+best_coup);
                //System.out.println("DEBUG -> "+best_coup.getPremier().getPosition().getX()+","+best_coup.getPremier().getPosition().getY()+","+best_coup.getSecond()+","+best_coup.getTroisieme()+","+best_coup.getQuatrieme());
                Couple<IAFields<Piece, String, String, Plateau.TypePlateau>, Integer> unMeilleurCoup = lst_coup.get(r.nextInt(lst_coup.size()));
                best_coup = unMeilleurCoup.getPremier();
            }
        }

        //System.out.println("DEBUG -> "+best_coup);
        if (best_coup == null) {
            //System.out.println("IAMinimax: best coup null");
            return null;
        }
        System.out.println("CONFIGURATIONS H:");
        for (int i = 0; i < 9; i++) {
            System.out.println(i + " : " + poids.get(i));
        }

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
            // int score = heuristique(clone);
            // int score = heuristique(clone, clone.getJoueur2());
            int score = heuristique(clone, tourIA);
            //memoisation.put(hash, new Memoisation(score, profondeur));
            return score;
        }

        ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> tours = null;
        tours = getTourPossible(clone.getJoueurCourant(), clone);

        if (tours.isEmpty()) {
            int val = tourIA ? -1000000 + profondeur : 1000000 - profondeur;
            //memoisation.put(hash, new Memoisation(val, profondeur));
            return val;
        }

        //trierToursParHeuristique(tours, tourIA, clone);

        int best = tourIA ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (IAFields<Piece, String, String, Plateau.TypePlateau> tour : tours) {
            if (tour.getPremier() != null) {
                Jeu jeuClone = new Jeu(clone);
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
                if (tourIA) {
                    best = Math.max(best, alphabeta(profondeur - 1, alpha, beta, false, jeuClone));
                    alpha = Math.max(alpha, best);
                } else {
                    best = Math.min(best, alphabeta(profondeur - 1, alpha, beta, true, jeuClone));
                    beta = Math.min(beta, best);
                }
            }

            if (beta <= alpha) {
                break;
            }
        }
        //memoisation.put(hash, new Memoisation(best, profondeur));
        return best;
    }

    private String getHash(Jeu clone) {
        return clone.getGameStateAsString();
    }

    private void trierToursParHeuristique(ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> tours, boolean tourIA, Jeu jeu) {
        int n = tours.size();
        int[] evaluations = new int[n];

        // Calculer l'évaluation de chaque coup une seule fois
        for (int i = 0; i < n; i++) {
            evaluations[i] = evaluerTour(tours.get(i), jeu, tourIA);
        }

        // Tri simple par insertion basé sur evaluations, tri décroissant si tourIA, croissant sinon
        for (int i = 1; i < n; i++) {
            IAFields<Piece, String, String, Plateau.TypePlateau> currentTour = tours.get(i);
            int currentEval = evaluations[i];
            int j = i - 1;

            while (j >= 0 && ((tourIA && evaluations[j] < currentEval) || (!tourIA && evaluations[j] > currentEval))) {
                tours.set(j + 1, tours.get(j));
                evaluations[j + 1] = evaluations[j];
                j--;
            }
            tours.set(j + 1, currentTour);
            evaluations[j + 1] = currentEval;
        }
    }

    private int evaluerTour(IAFields<Piece, String, String, Plateau.TypePlateau> tour, Jeu jeuOriginal, boolean tourIA) {
        if (tour.getPremier() == null) {
            return Integer.MIN_VALUE;
        }

        // Cloner une seule fois le jeu ici
        Jeu jeu = new Jeu(jeuOriginal);

        int x = (int) tour.getPremier().getPosition().getX();
        int y = (int) tour.getPremier().getPosition().getY();
        Piece piece = jeu.getPlateauCourant().getPiece(x, y);
        if (piece == null) {
            return Integer.MIN_VALUE;
        }

        Coup coup1 = Coup.stringToCoup(piece, jeu.getPlateauCourant(), tour.getSecond());
        if (coup1 != null) {
            jeu.appliquerCoup(coup1);
        }

        Coup coup2 = Coup.stringToCoup(piece, jeu.getPlateauCourant(), tour.getTroisieme());
        if (coup2 != null) {
            jeu.appliquerCoup(coup2);
        }

        jeu.choisirPlateau(tour.getQuatrieme());
        jeu.joueurSuivant();
        
        return heuristique(jeu, tourIA);
        // return heuristique(jeu, jeu.getJoueur2());
    }

    // heuristique
    private int heuristique(Jeu jeu, boolean tourIA) {
        Plateau passe = jeu.getPast();
        Plateau present = jeu.getPresent();
        Plateau futur = jeu.getFuture();
        Plateau plateauCourant = jeu.getPlateauCourant();
        Joueur joueur = jeu.getJoueurCourant();
        Joueur opponent = null;
        if (joueur.getId() == 1) {
            opponent = jeu.getJoueur2();
        } else {
            opponent = jeu.getJoueur1();
        }
        if (!tourIA){
            joueur = opponent;
            opponent = jeu.getJoueurCourant();
        }
        int score = 0;
        if (this.mode.equals("HARD")){
            // heuristique 1: nb Pieces restantes, +poids pour chaque piece restante (heuristique efficace)
            score += scorePiecesRestantes(joueur, passe, present, futur, poids.get(0));
            // heuritique 2: position sur plateau, +poids au milieu, 0 sur les bords, -poids dans les coins (heuristique peu efficace)
            score += scorePositionPlateau(joueur, plateauCourant, poids.get(1));
            // heuristique3: nombre de pieces restantes dans l'inventaire (currentPlayer - opp.Player)*poids (heuristique efficace)
            score += scorePionsInventaire(joueur, opponent, poids.get(2));
            // heurisitque 4: presence plateau, +2 pour chaque plateau ou l'ia se situe, -2 si elle n'est que sur 1 plateau (heuristique moyenne)
            score += presencePlateau(joueur, passe, present, futur, poids.get(3));
            // heuristique 5: nombre de pièces par rapport à l'adversaire, (own piece - opponent piece)*poids (heuristique très efficace)
            score += piecesContreAdversaire(joueur, passe, present, futur, poids.get(4));
            //heuristique 6: plus de pièces que l'adversaire sur chaque plateau
            score += scoreInitiative(joueur, jeu, poids.get(5));
            //heuristique 7: triangulation temporelle, nb de pieces sur chaque plateau, version améliorée de l'heuristique 4
            score +=scoreTriangulation(joueur,jeu,poids.get(6));
            //heuristique 8: menace
            score += scorePiecesMenacees(joueur, plateauCourant, poids.get(7));
            //heuristique 9: malus choix d'un plateau sans pion

            //heuristique 10: eliminer dans une temporalite
            score += scoreExtinction(jeu,opponent,poids.get(8));
        } else {
            // heuristique 1: nb Pieces restantes, +poids pour chaque piece restante (heuristique efficace) (nbpions*poids)
            score += scorePiecesRestantes(joueur, passe, present, futur, 9); //bien, 8/10
            //System.out.println("H1 : "+score);

            // heuritique 2: position sur plateau, +poids au milieu, 0 sur les bords, -poids dans les coins (heuristique peu efficace)
            score += scorePositionPlateau(joueur, plateauCourant, 11); //A chier, 1/10

            // heuristique3: nombre de pieces restantes dans l'inventaire (currentPlayer - opp.Player)*poids (heuristique efficace)
            score += scorePionsInventaire(joueur, opponent, 9); //nul seul, exploitable avec H1 < H3
            //System.out.println("H2 : "+score);

            // heurisitque 4: presence plateau, +2 pour chaque plateau ou l'ia se situe, -2 si elle n'est que sur 1 plateau (heuristique moyenne)
            score += presencePlateau(joueur, passe, present, futur, 5); //bof, 2/10

            // heuristique 5: nombre de pièces par rapport à l'adversaire, (own piece - opponent piece)*poids (heuristique très efficace)
            score += piecesContreAdversaire(joueur, passe, present, futur, 5); //pue, 0/10

            //heuristique 6: plus de pièces que l'adversaire sur chaque plateau (table de 3)
            score += scoreInitiative(joueur, jeu, 13); //bien, 6/10

            //heuristique 7: triangulation temporelle, nb de pieces sur chaque plateau, version améliorée de l'heuristique 4
            //score +=scoreTriangulation(joueur,jeu,1);

            //heuristique 8: menace
            score += scorePiecesMenacees(joueur, plateauCourant, 6); //j'aime bien, 6.5/10

            //heuristique 9: malus choix d'un plateau sans pion (POUBELLE)

            //heuristique 10: eliminer dans une temporalite
            score += scoreExtinction(jeu,opponent,9);

            //heuristique spe: empeche le suicide, l'autre interdit de choisir un plateau sans pion
            score += scoreSurvie(jeu,joueur,10);
            score += scorePlateauAvecPion(jeu,joueur,10);
        }
        return score;
    }
    // heuristique 1: nb Pieces restantes, +2 pour chaque piece restante
    private int scorePiecesRestantes(Joueur joueur, Plateau passe, Plateau present, Plateau futur, int poids) {
        int score = 0;
        if (joueur.getNom().equals("Blanc")) {
            score += (passe.getNbBlancs() + present.getNbBlancs() + futur.getNbBlancs()) * poids;
        } else if (joueur.getNom().equals("Noir")) {
            score += (passe.getNbNoirs() + present.getNbNoirs() + futur.getNbNoirs()) * poids;
        }
        return score;
    }
    // heuritique 2: position sur plateau, +2 au milieu, 0 sur les bords, -2 dans les coins
    private int scorePositionPlateau(Joueur joueur, Plateau plateauCourant, int poids) {
        int score = 0;
        for (int i = 0; i < plateauCourant.getSize(); i++) {
            for (int j = 0; j < plateauCourant.getSize(); j++) {
                if (plateauCourant.getPiece(i, j) != null) {
                    if ((i == 0 || i == 3) && (j == 0 || j == 3) && plateauCourant.getPiece(i, j).getOwner().equals(joueur)) {
                        // coins
                        score -= poids;
                    } else if (((i == 0 || i == 3) || (j == 0 || j == 3)) && plateauCourant.getPiece(i, j).getOwner().equals(joueur)) {
                        // bords
                        score += 0;
                    } else if (plateauCourant.getPiece(i, j).getOwner().equals(joueur)) {
                        // centre
                        score += poids;
                    }
                }
            }
        }
        return score;
    }
    // heuristique3: nombre de pieces restantes dans l'inventaire (currentPlayer - opp.Player)*poids (heuristique efficace)
    private int scorePionsInventaire(Joueur joueur, Joueur opponent, int poids) {
        return (joueur.getNbClones() - opponent.getNbClones()) * poids;
    }
    // heurisitque 4: presence plateau, +2 pour chaque plateau ou l'ia se situe, -2 si elle n'est que sur 1 plateau
    private int presencePlateau(Joueur joueur, Plateau passe, Plateau present, Plateau futur, int poids) {
        int score = 0;
        int nbPlateau = 0;
        if (joueur.existePion(passe)) {
            nbPlateau++;
        }
        if (joueur.existePion(present)) {
            nbPlateau++;
        }
        if (joueur.existePion(futur)) {
            nbPlateau++;
        }
        switch (nbPlateau) {
            case 1:
                score = -5*poids;
                break;
            case 2:
                score = poids;
                break;
            case 3:
                score = 2 * poids;
                break;
            default:
                break;
        }
        return score;
    }
    // heuristique 5: nombre de pièces par rapport à l'adversaire, (own piece - opponent piece)*poids
    private int piecesContreAdversaire(Joueur joueur, Plateau passe, Plateau present, Plateau futur, int poids) {
        int score = 0;
        if (joueur.getNom().equals("Blanc")) {
            score += ((passe.getNbBlancs() - passe.getNbNoirs()) + (present.getNbBlancs() - present.getNbNoirs()) + (futur.getNbBlancs() - futur.getNbNoirs()))  * poids;
        } else if (joueur.getNom().equals("Noir")) {
            score += ((passe.getNbNoirs() - passe.getNbBlancs()) + (present.getNbNoirs() - present.getNbBlancs()) + (futur.getNbNoirs() - futur.getNbBlancs())) * poids;
        }
        return score;
    }
    //heuristique 6: plus de pièces que l'adversaire sur chaque plateau
    private int scoreInitiative(Joueur joueur, Jeu jeu, int poids) {
        int score = 0;
        if (joueur.getId() == 1) {
            if (jeu.getPast().getNbBlancs() > jeu.getPast().getNbNoirs()) {
                score++;
            }
            if (jeu.getPresent().getNbBlancs() > jeu.getPresent().getNbNoirs()) {
                score++;
            }
            if (jeu.getFuture().getNbBlancs() > jeu.getFuture().getNbNoirs()) {
                score++;
            }
        } else {
            if (jeu.getPast().getNbBlancs() < jeu.getPast().getNbNoirs()) {
                score++;
            }
            if (jeu.getPresent().getNbBlancs() < jeu.getPresent().getNbNoirs()) {
                score++;
            }
            if (jeu.getFuture().getNbBlancs() < jeu.getFuture().getNbNoirs()) {
                score++;
            }
        }
        return score*poids;
    }
    //heuristique 7: triangulation temporelle, nb de pieces sur chaque plateau, version améliorée de l'heuristique 4
    private int scoreTriangulation(Joueur joueur, Jeu jeu, int poids) {
        int score = 0;
        Plateau passe = jeu.getPast();
        Plateau present = jeu.getPresent();
        Plateau futur = jeu.getFuture();
        int taille = passe.getSize();
        for (int i = 0; i < taille; i++) {
            for (int j = 0; j < taille; j++) {
                int nbPlateaux = 0;
                if (passe.getPiece(i, j) != null && passe.getPiece(i, j).getOwner().equals(joueur)) {
                    nbPlateaux++;
                }
                if (present.getPiece(i, j) != null && present.getPiece(i, j).getOwner().equals(joueur)) {
                    nbPlateaux++;
                }
                if (futur.getPiece(i, j) != null && futur.getPiece(i, j).getOwner().equals(joueur)) {
                    nbPlateaux++;
                }
                if (nbPlateaux >= 2) {
                    score += nbPlateaux*poids;
                }
            }
        }
        return score;
    }
    //heuristique 8: menace, pièces menacées
    private int scorePiecesMenacees(Joueur joueur, Plateau plateauCourant, int poids) {
        int score = 0;
        int taille = plateauCourant.getSize();
        for (int i = 0; i < taille; i++) {
            for (int j = 0; j < taille; j++) {
                Piece piece = plateauCourant.getPiece(i, j);
                if (piece != null && !piece.getOwner().equals(joueur)) {
                    //verifiaction si une piece du joueur est adjacente
                    boolean menacee = false;
                    for (int dx = -1; dx <= 1 && !menacee; dx++) {
                        for (int dy = -1; dy <= 1 && !menacee; dy++) {
                            if (dx != 0 && dy != 0) {
                                int x = i + dx;
                                int y = j + dy;
                                if (x >= 0 && x < taille && y >= 0 && y < taille) {
                                    Piece adj = plateauCourant.getPiece(x, y);
                                    menacee = true;
                                }
                            }
                        }
                    }
                    if (menacee) {
                        score += poids;
                    }
                }
            }
        }
        return score;
    }
    //heuristique 9: malus choix d'un plateau sans pion
    private int scoreChoixPlateau(Jeu jeu, int poids){
        if (!jeu.getJoueurCourant().existePion(jeu.getPlateauCourant())){
            return (-2*poids);
        }
        return 0;
    }
    //heuristique 10: extinction sur une temporalite
    private int scoreExtinction(Jeu jeu, Joueur opponent, int poids){
        int score = 0;
        if (!opponent.existePion(jeu.getPast())){
            score += poids;
        }
        if (!opponent.existePion(jeu.getPresent())){
            score += poids;
        }
        if (!opponent.existePion(jeu.getFuture())){
            score += poids;
        }
        return score;
    }

    //heuristique spe: survie
    private int scoreSurvie(Jeu jeu, Joueur joueur, int poids){
        int score = 0;
        int nbPlateau = 0;
        if (joueur.existePion(jeu.getPast())){
            nbPlateau++;
        }
        if (joueur.existePion(jeu.getPresent())){
            nbPlateau++;
        }
        if (joueur.existePion(jeu.getFuture())){
            nbPlateau++;
        }
        if (nbPlateau <= 1){
            score-= 100*poids;
        }
        return score;
    }

    private int scorePlateauAvecPion(Jeu jeu, Joueur joueur, int poids){
        int score = 0;
        if (!joueur.existePion(jeu.getPlateauCourant())){
            score -= 10*poids;
        }
        return score;
    }
    // heuristique proposee par Chu
    // private int heuristique(Jeu jeu, Joueur joueur) {
    //     int wMateriel = 1;
    //     int wPositionnel = 1;
    //     int wMobilite = 1;
    //     int wRisque = 1;
    //     int wProchainPlateau = 1;

    //     return wMateriel * hMateriel(jeu, joueur)
    //             + wPositionnel * hPositionnel(jeu, joueur)
    //             + wMobilite * hMobilite(jeu, joueur)
    //             + wRisque * hRisque(jeu, joueur)
    //             + wProchainPlateau * hProchainPlateau(jeu, joueur);
    // }

    // // h1 materiel:
    // private int hMateriel(Jeu jeu, Joueur joueur) {
    //     int score = 0;
    //     // Evaluer chaque piece sur le plateau
    //     for (int row = 0; row < jeu.getPlateauCourant().getSize(); row++) {
    //         for (int col = 0; col < jeu.getPlateauCourant().getSize(); col++) {
    //             Piece piece = jeu.getPlateauCourant().getPiece(row, col);
    //             if (piece != null && piece.getOwner().equals(joueur)) {
    //                 score += 75; // +1 pour chaque piece du AI
    //             } else if (piece != null && !piece.getOwner().equals(joueur)) {
    //                 score -= 75; // -1 pour chaque piece de l'adversaire
    //             }
    //         }
    //     }

    //     // Evaluer les pieces restantes dans l'inventaire (clones)
    //     Joueur AI = joueur.getId() == 1 ? jeu.getJoueur1() : jeu.getJoueur2();
    //     Joueur adversaire = joueur.getId() == 1 ? jeu.getJoueur2() : jeu.getJoueur1();

    //     score += AI.getNbClones() * 50; // +5 pour chaque clone du AI
    //     score -= adversaire.getNbClones() * 50; // -5 pour chaque clone de l'adversaire
    //     return score;
    // }

    // // h2 positionnel:
    // private int hPositionnel(Jeu jeu, Joueur joueur) {
    //     int score = 0;

    //     score = jeu.gameOver(joueur) == joueur.getId() ?   100000 : score; // +10000 si le joueur gagne
    //     score = (jeu.gameOver(joueur) != joueur.getId() && jeu.gameOver(joueur) != 0) ? -100000 : score; // -10000 si le joueur perd

    //     // Evaluer l'existence de IA sur les plateaux
    //     int numBlancs = 0, numNoirs = 0;
    //     if (jeu.getPast().getNbBlancs() > 0) {
    //         numBlancs+=2;
    //     }
    //     if (jeu.getPresent().getNbBlancs() > 0) {
    //         numBlancs+=2;
    //     }
    //     if (jeu.getFuture().getNbBlancs() > 0) {
    //         numBlancs++;
    //     }
    //     if (jeu.getPast().getNbNoirs() > 0) {
    //         numNoirs++;
    //     }
    //     if (jeu.getPresent().getNbNoirs() > 0) {
    //         numNoirs++;
    //     }
    //     if (jeu.getFuture().getNbNoirs() > 0) {
    //         numNoirs++;
    //     }

    //     int AI = joueur.getId() == 1 ? numBlancs : numNoirs;
    //     int adversaire = joueur.getId() == 1 ? numNoirs : numBlancs;

    //     score += AI * 100; // +50 pour chaque plateau ou l'IA se situe
    //     score -= adversaire * 100; // -50 pour chaque plateau ou l'adversaire se situe

    //     // Evaluer la position des pieces sur chaque plateau
    //     for (Plateau plateau : new Plateau[]{jeu.getPast(), jeu.getPresent(), jeu.getFuture()}) {
    //         int size = plateau.getSize();
    //         int mid = size / 2;
    //         // On considère les 4 cases centrales
    //         for (int i = mid - 1; i <= mid; i++) {
    //             for (int j = mid - 1; j <= mid; j++) {
    //                 Piece piece = plateau.getPiece(i, j);
    //                 if (piece != null) {
    //                     if (piece.getOwner().equals(joueur)) {
    //                         score += 85; // +85 pour chaque piece du AI dans le centre
    //                     } else {
    //                         score -= 85; // -85 pour chaque piece de l'adversaire dans le centre
    //                     }
    //                 }
    //             }
    //         }

    //         // Evaluer les positions au coin
    //         // Coins : (0,0), (0,size-1), (size-1,0), (size-1,size-1)
    //         int[][] coins = {{0, 0}, {0, size - 1}, {size - 1, 0}, {size - 1, size - 1}};
    //         for (int[] coin : coins) {
    //             Piece coinPiece = plateau.getPiece(coin[0], coin[1]);
    //             if (coinPiece != null) {
    //                 Joueur owner = coinPiece.getOwner();
    //                 // Calculer la distance de Manhattan minimale avec un pion adverse
    //                 int minDist = Integer.MAX_VALUE;
    //                 for (int x = 0; x < size; x++) {
    //                     for (int y = 0; y < size; y++) {
    //                         Piece other = plateau.getPiece(x, y);
    //                         if (other != null && !other.getOwner().equals(owner)) {
    //                             int dist = Math.abs(coin[0] - x) + Math.abs(coin[1] - y);
    //                             if (dist < minDist) {
    //                                 minDist = dist;
    //                             }
    //                         }
    //                     }
    //                 }
    //                 if (owner.equals(joueur)) {
    //                     score += 75;
    //                     if (minDist <= 2) {
    //                         score -= 75;
    //                     }
    //                 } else {
    //                     score -= 75;
    //                     if (minDist <= 2) {
    //                         score += 75;
    //                     }
    //                 }
    //             }
    //         }
    //     }

    //     return score;
    // }

    // // h3 mobilite:
    // private int hMobilite(Jeu jeu, Joueur joueur) {
    //     // Calculer la mobilité sur tous les plateaux pour le joueur donné
    //     int score = 0;
    //     for (Plateau plateau : new Plateau[]{jeu.getPast(), jeu.getPresent(), jeu.getFuture()}) {
    //         ArrayList<Piece> pieces = new ArrayList<>();
    //         for (int i = 0; i < plateau.getSize(); i++) {
    //             for (int j = 0; j < plateau.getSize(); j++) {
    //                 Piece piece = plateau.getPiece(i, j);
    //                 if (piece != null && piece.getOwner().equals(joueur)) {
    //                     pieces.add(piece);
    //                 }
    //             }
    //         }
    //         for (Piece piece : pieces) {
    //             ArrayList<Coup> coups = jeu.getCoupPossibles(plateau, piece);
    //             if (coups != null) {
    //                 score += coups.size() * 25;
    //             }
    //         }
    //     }
    //     return score;
    // }

    // // h4 risque: Calculer le risque de perdre des pièces (paradox)
    // private int hRisque(Jeu jeu, Joueur joueur) {
    //     int score = 0;
    //     for (Plateau plateau : new Plateau[]{jeu.getPast(), jeu.getPresent(), jeu.getFuture()}) {
    //         ArrayList<Point> ownPositions = new ArrayList<>();
    //         ArrayList<Point> oppPositions = new ArrayList<>();
    //         for (int i = 0; i < plateau.getSize(); i++) {
    //             for (int j = 0; j < plateau.getSize(); j++) {
    //                 Piece piece = plateau.getPiece(i, j);
    //                 if (piece != null) {
    //                     if (piece.getOwner().equals(joueur)) {
    //                         ownPositions.add(new Point(i, j));
    //                     } else {
    //                         oppPositions.add(new Point(i, j));
    //                     }
    //                 }
    //             }
    //         }
    //         // Vérifier s'il y a au moins 2 pions du joueur sur ce plateau
    //         if (ownPositions.size() < 2) {
    //             continue;
    //         }
    //         // Pour chaque paire de pions du joueur
    //         for (int i = 0; i < ownPositions.size(); i++) {
    //             for (int j = i + 1; j < ownPositions.size(); j++) {
    //                 int distOwn = Math.abs(ownPositions.get(i).x - ownPositions.get(j).x)
    //                         + Math.abs(ownPositions.get(i).y - ownPositions.get(j).y);
    //                 // Distance min avec un pion adverse pour chaque pion du joueur
    //                 int minDistOppI = Integer.MAX_VALUE;
    //                 int minDistOppJ = Integer.MAX_VALUE;
    //                 for (Point opp : oppPositions) {
    //                     int distI = Math.abs(ownPositions.get(i).x - opp.x)
    //                             + Math.abs(ownPositions.get(i).y - opp.y);
    //                     int distJ = Math.abs(ownPositions.get(j).x - opp.x)
    //                             + Math.abs(ownPositions.get(j).y - opp.y);
    //                     if (distI < minDistOppI) {
    //                         minDistOppI = distI;
    //                     }
    //                     if (distJ < minDistOppJ) {
    //                         minDistOppJ = distJ;
    //                     }
    //                 }
    //                 // Cas 1 : distOwn == 2 et minDistOpp == 1
    //                 if (distOwn == 2 && (minDistOppI == 1 || minDistOppJ == 1)) {
    //                     score -= 80;
    //                 }
    //                 // Cas 2 : distOwn == 1 et minDistOpp == 2
    //                 if (distOwn == 1 && (minDistOppI == 2 || minDistOppJ == 2)) {
    //                     score -= 80;
    //                 }
    //             }
    //         }
    //         // Inverse : pour chaque paire de pions adverses
    //         for (int i = 0; i < oppPositions.size(); i++) {
    //             for (int j = i + 1; j < oppPositions.size(); j++) {
    //                 int distOpp = Math.abs(oppPositions.get(i).x - oppPositions.get(j).x)
    //                         + Math.abs(oppPositions.get(i).y - oppPositions.get(j).y);
    //                 int minDistOwnI = Integer.MAX_VALUE;
    //                 int minDistOwnJ = Integer.MAX_VALUE;
    //                 for (Point own : ownPositions) {
    //                     int distI = Math.abs(oppPositions.get(i).x - own.x)
    //                             + Math.abs(oppPositions.get(i).y - own.y);
    //                     int distJ = Math.abs(oppPositions.get(j).x - own.x)
    //                             + Math.abs(oppPositions.get(j).y - own.y);
    //                     if (distI < minDistOwnI) {
    //                         minDistOwnI = distI;
    //                     }
    //                     if (distJ < minDistOwnJ) {
    //                         minDistOwnJ = distJ;
    //                     }
    //                 }
    //                 if (distOpp == 2 && (minDistOwnI == 1 || minDistOwnJ == 1)) {
    //                     score += 80;
    //                 }
    //                 if (distOpp == 1 && (minDistOwnI == 2 || minDistOwnJ == 2)) {
    //                     score += 80;
    //                 }
    //             }
    //         }
    //     }
    //     return score;
    // }

    // // h5 plt prochain
    // private int hProchainPlateau(Jeu jeu, Joueur joueur) {
    //     int score = 0;
    //     Plateau.TypePlateau prochainPlateau = joueur.getProchainPlateau();

    //     // Si joueur est 1, vérifier sur prochainPlateau s'il existe des pions, sinon -50
    //     if (joueur.getId() == 1) {
    //         Plateau plateau = null;
    //         if (prochainPlateau == Plateau.TypePlateau.PAST) {
    //             plateau = jeu.getPast();
    //         } else if (prochainPlateau == Plateau.TypePlateau.PRESENT) {
    //             plateau = jeu.getPresent();
    //         } else if (prochainPlateau == Plateau.TypePlateau.FUTURE) {
    //             plateau = jeu.getFuture();
    //         }
    //         boolean pionExiste = false;
    //         for (int i = 0; i < plateau.getSize(); i++) {
    //             for (int j = 0; j < plateau.getSize(); j++) {
    //                 Piece piece = plateau.getPiece(i, j);
    //                 if (piece != null && piece.getOwner().equals(joueur)) {
    //                     pionExiste = true;
    //                     break;
    //                 }
    //             }
    //             if (pionExiste) {
    //                 break;
    //             }
    //         }
    //         if (!pionExiste) {
    //             score -= 100;
    //         }
    //     }

    //     // Calculer la mobilité sur chaque plateau
    //     int maxMobilite = Integer.MIN_VALUE;
    //     int minMobilite = Integer.MAX_VALUE;
    //     Plateau.TypePlateau maxPlateau = null;
    //     Plateau.TypePlateau minPlateau = null;
    //     for (Plateau.TypePlateau type : Plateau.TypePlateau.values()) {
    //         Plateau plateau = null;
    //         if (type == Plateau.TypePlateau.PAST) {
    //             plateau = jeu.getPast();
    //         } else if (type == Plateau.TypePlateau.PRESENT) {
    //             plateau = jeu.getPresent();
    //         } else if (type == Plateau.TypePlateau.FUTURE) {
    //             plateau = jeu.getFuture();
    //         }

    //         int mobilite = 0;
    //         for (int i = 0; i < plateau.getSize(); i++) {
    //             for (int j = 0; j < plateau.getSize(); j++) {
    //                 Piece piece = plateau.getPiece(i, j);
    //                 if (piece != null && piece.getOwner().equals(joueur)) {
    //                     ArrayList<Coup> coups = jeu.getCoupPossibles(plateau, piece);
    //                     if (coups != null) {
    //                         mobilite += coups.size();
    //                     }
    //                 }
    //             }
    //         }
    //         if (mobilite > maxMobilite) {
    //             maxMobilite = mobilite;
    //             maxPlateau = type;
    //         }
    //         if (mobilite < minMobilite) {
    //             minMobilite = mobilite;
    //             minPlateau = type;
    //         }
    //     }

    //     // Si prochainPlateau a la plus grande mobilité alors +50, si la plus faible -50
    //     if (prochainPlateau == maxPlateau) {
    //         score += 200;
    //     }
    //     if (prochainPlateau == minPlateau) {
    //         score -= 200;
    //     }
    //     return score;
    // }

    private ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> getTourPossible(Joueur joueur, Jeu clone) {
        if (clone.gameOver(joueur) != 0) {
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
