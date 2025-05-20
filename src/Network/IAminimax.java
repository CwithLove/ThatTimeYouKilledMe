package Network;

import Modele.Coup;
import Modele.IAFields;
import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;
import Modele.Couple;
import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

public class IAminimax {
    private Jeu jeu;
    private final int PROFONDEUR_MAX = 3;
    private int difficulte = 1;
    private String mode = "";
    private Random r = new Random();

    //builder de l'ia
    public IAminimax(int difficulte, Jeu jeu) {
        if (difficulte > PROFONDEUR_MAX){
            difficulte = PROFONDEUR_MAX;
        }
        this.difficulte = difficulte;
        this.jeu = jeu;
        if (difficulte >= PROFONDEUR_MAX){
            this.mode = "HARD";
        } else if (difficulte >= (int)(PROFONDEUR_MAX*0.5)){
            this.mode = "MEDIUM";
        } else {
            this.mode = "EASY";
        }
    }

    public IAFields<Piece,String,String,Plateau.TypePlateau> coupIA(Jeu gameState){
        if (this.jeu == null && gameState != null){
            this.jeu = gameState;
        }
        ArrayList<Couple<IAFields<Piece,String,String,Plateau.TypePlateau>,Integer>> lst_coup = new ArrayList<>();
        IAFields<Piece,String,String,Plateau.TypePlateau> best_coup = null;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        int best = Integer.MIN_VALUE;
        Joueur joueur = gameState.getJoueurCourant();

        //Minimax de profondeur [difficulte] (max 3, 6 avec heuristique) avec elagage
        for (IAFields<Piece,String,String,Plateau.TypePlateau> coup : getCoupsPossible(joueur, gameState)){
            Jeu jeuClone = new Jeu(gameState);
            if (coup.getPremier() != null) {
                int x = (int) coup.getPremier().getPosition().getX();
                int y = (int) coup.getPremier().getPosition().getX();
                Piece pieceCourante = jeuClone.getPlateauCourant().getPiece(x, y);
                if (pieceCourante == null) {
                    System.out.println("IAMinimax: Piece courante null");
                    continue;
                }

                Coup coup1 = Coup.stringToCoup(pieceCourante, jeuClone.getPlateauCourant(), coup.getSecond());
                if (coup1 == null) {
                    continue;
                }
                jeuClone.appliquerCoup(coup1);

                Coup coup2 = Coup.stringToCoup(pieceCourante, jeuClone.getPlateauCourant(), coup.getTroisieme());
                if (coup2 == null) {
                    continue;
                }
                jeuClone.appliquerCoup(coup2);
            }


            int score = alphabeta(this.difficulte-1, alpha, beta, false, joueur, jeuClone);
            //System.out.println("Pour le coup :"+coup+", on a le score (pas encore a jour) :"+score);
            lst_coup.add(new Couple<>(coup,score));

            if (score > best){
                best = score;
                best_coup = coup;
                System.out.println("BESTCOUP "+best_coup);
            }
            alpha = Math.max(alpha, score);
        }



        //System.out.println("Le meilleur coup est:"+best_coup.getPremier().getPosition().getX()+","+best_coup.getPremier().getPosition().getY()+","+best_coup.getSecond()+","+best_coup.getTroisieme()+","+best_coup.getQuatrieme());
        if (lst_coup.size() > 1){
            int seuil = 0;
            if (this.mode.equals("HARD")){
                seuil = (int)(best*0.95);
            } else if (this.mode.equals("MEDIUM")){
                seuil = (int) (best*0.5);
            }

            for (int i=0; i<lst_coup.size(); i++){
                if (lst_coup.get(i).getSecond() < seuil){
                    lst_coup.remove(i);
                    i--;
                }
                //System.out.println("COUUUP: "+lst_coup.get(i).getSecond());
            }
            Couple<IAFields<Piece,String,String,Plateau.TypePlateau>,Integer> unMeilleurCoup = lst_coup.get(r.nextInt(lst_coup.size()));
            best_coup = unMeilleurCoup.getPremier();
        }

        System.out.println("DEBUG -> "+best_coup);
        if (best_coup == null){
            System.out.println("A CORRIGER -> SI LE 1ER ELEMENT EST NULL, ON SKIP L'APPLICATION DES COUPS, MAIS ON APPLIQUE LE CHANGEMENT DE PLATEAU");
            System.out.println("A CORRIGER -> CERTAINES PIECES SONT NULLES, ET ON A DES SITUATIONS OU PAR EXEMPLE ON EST EN 0 3, ET ON PEUT FAIRE LEFT -> UP");
        }
        return best_coup;
    }


    private int alphabeta(int profondeur, int alpha, int beta, boolean tourIA, Joueur joueur, Jeu clone){
        
        Plateau passe = jeu.getPast();
        Plateau present = jeu.getPresent();
        Plateau futur = jeu.getFuture();
        if (profondeur <= 0){
            return heuristique(joueur, clone.getPlateauCourant(), passe, present, futur);
        }

        ArrayList<IAFields<Piece,String,String,Plateau.TypePlateau>> coups = null;
        if (tourIA) {
            if (joueur.getId() == 1){
                coups = getCoupsPossible(jeu.getJoueur2(), clone);
            } else {
                coups = getCoupsPossible(jeu.getJoueur1(), clone);
            }
        } else {
            coups = getCoupsPossible(joueur, clone);
        }

        if (coups.isEmpty()){
            return tourIA ? -1000 + profondeur : 1000 - profondeur;
        }

        int best = tourIA ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (IAFields<Piece,String,String,Plateau.TypePlateau> coup : coups){
            Jeu jeuClone = new Jeu(clone);
            int x = (int)coup.getPremier().getPosition().getX();
            int y = (int)coup.getPremier().getPosition().getX();
            Piece pieceCourant = jeuClone.getPlateauCourant().getPiece(x, y);
            if (pieceCourant == null){
                continue;
            }

            Coup coup1 = Coup.stringToCoup(pieceCourant, jeuClone.getPlateauCourant(), coup.getSecond());
            if (coup1 == null){
                continue;
            }
            jeuClone.appliquerCoup(coup1);

            Coup coup2 = Coup.stringToCoup(pieceCourant, jeuClone.getPlateauCourant(),coup.getTroisieme());
            if (coup2 == null){
                continue;
            }
            jeuClone.appliquerCoup(coup2);

            if (tourIA){
                best = Math.max(best, alphabeta(profondeur - 1, alpha, beta, false, joueur, jeuClone));
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, alphabeta(profondeur - 1, alpha, beta, true, joueur, jeuClone));
                beta = Math.min(beta, best);
            }

            if (beta <= alpha) {
                break;
            }
        }
        return best;
    }


    //heuristique
    private int heuristique(Joueur joueur,Plateau plateauCourant, Plateau passe, Plateau present, Plateau futur){
        int score = 0;
        //heuristique 1: nb Pieces restantes, +2 pour chaque piece restante +1 pour celles dans l'inventaire
        score += scorePiecesRestantes(joueur,passe,present,futur);
        //heuritique 2: position sur plateau, +2 au milieu, 0 sur les bords, -2 dans les coins
        score += scorePositionPlateau(joueur, plateauCourant);
        //heuristique3: distance adversaire, +1 pour chaque case à distance du plus proche ennemi
        //heurisitque 4: presence plateau, +2 pour chaque plateau ou l'ia se situe, -2 si elle n'est que sur 1 plateau
        score += presencePlateau(joueur,passe,present,futur);
        //heuristique 5: nombre de pièces par rapport à l'adversaire, (own piece - opponent piece)*10
        score += piecesContreAdversaire(joueur,passe,present,futur,10);
        return score;
    }

    //heuristique 1: nb Pieces restantes, +2 pour chaque piece restante +1 pour celles dans l'inventaire
    private int scorePiecesRestantes(Joueur joueur, Plateau passe, Plateau present, Plateau futur){
        int score = 0;
        score += joueur.getNbClones();
        if (joueur.getNom().equals("Blanc")){
            score += (passe.getNbBlancs() + present.getNbBlancs() + futur.getNbBlancs())*2;
        }
        else if (joueur.getNom().equals("Noir")){
            score += (passe.getNbNoirs() + present.getNbNoirs() + futur.getNbNoirs())*2;
        }
        return score;
    }

    //heuritique 2: position sur plateau, +2 au milieu, 0 sur les bords, -2 dans les coins
    private int scorePositionPlateau(Joueur joueur, Plateau plateauCourant){
        int score = 0;
        Piece tmp = null;
        for (int i = 0; i < plateauCourant.getSize(); i++){
            for (int j = 0; j < plateauCourant.getSize(); j++){
                if (plateauCourant.getPiece(i,j) != null){
                    //Les bords
                    if ((i == 0 || i == 3) && (j == 0 || j == 3) && plateauCourant.getPiece(i,j).getOwner().equals(joueur)){
                        score -= 2;
                    } //les cotes
                    else if ((i == 0 || i == 3) || (j == 0 || j == 3) && plateauCourant.getPiece(i,j).getOwner().equals(joueur)){
                        score += 0; //cette condition est la dans le cas ou il faut modifier le score pour une piece au bord
                    } //le centre (ce qu'il erste
                    else {
                        score += 2;
                    }
                }
            }
        }
        return score;
    }

    //heurisitque 4: presence plateau, +2 pour chaque plateau ou l'ia se situe, -2 si elle n'est que sur 1 plateau
    private int presencePlateau(Joueur joueur, Plateau passe, Plateau present, Plateau futur){
        int score = 0;
        int nbPlateau = 0;
        if (joueur.existePion(passe)){
            nbPlateau++;
        }
        if (joueur.existePion(present)){
            nbPlateau++;
        }
        if (joueur.existePion(futur)){
            nbPlateau++;
        }
        switch(nbPlateau){
            case 1:
                score = -2;
                break;
            case 2:
                score = 2;
                break;
            case 3:
                score = 4;
                break;
            default:
                break;
        }
        return score;
    }

    //heuristique 5: nombre de pièces par rapport à l'adversaire, (own piece - opponent piece)*10
    private int piecesContreAdversaire(Joueur joueur, Plateau passe,Plateau present,Plateau futur, int poids){
        int score = 0;
        if (joueur.getNom().equals("Blanc")){
            score += (passe.getNbBlancs()- passe.getNbNoirs())*poids + (present.getNbBlancs()- present.getNbNoirs())*poids + (futur.getNbBlancs() - futur.getNbNoirs())*poids;
        }
        else if (joueur.getNom().equals("Noir")){
            score += (passe.getNbNoirs()- passe.getNbBlancs())*poids + (present.getNbNoirs()- present.getNbBlancs())*poids + (futur.getNbNoirs() - futur.getNbBlancs())*poids;
        }
        return score;
    }















    private ArrayList<IAFields<Piece,String,String,Plateau.TypePlateau>> getCoupsPossible(Joueur joueur, Jeu clone){
        ArrayList<IAFields<Piece,String,String,Plateau.TypePlateau>> listeCoups = new ArrayList<>();
        IAFields<Piece,String,String,Plateau.TypePlateau> coup;
        ArrayList<Piece> pieces = listePieces(joueur, clone.getPlateauCourant());
        if (pieces != null){
            for (Piece piece : pieces){
                //sauvegarde de la position de la piece
                int posx = (int)piece.getPosition().getX();
                int posy = (int)piece.getPosition().getY();
                for (Coup coup1 : clone.getCoupPossibles(clone.getPlateauCourant(), piece)){
                    Jeu jeuClone1 = new Jeu(clone);

                    //traduction en coup et application
                    jeuClone1.appliquerCoup(coup1);

                    for (Coup coup2 : jeuClone1.getCoupPossibles(clone.getPlateauCourant(), piece)){
                        for (Plateau.TypePlateau plateau : PlateauValide(joueur.getProchainPlateau())){
                            coup = new IAFields<>(piece, coup1.getTypeCoup().name(), coup2.getTypeCoup().name(), plateau);
                            System.out.println("DEBUG "+coup+", "+posx+" "+posy);
                            listeCoups.add(coup);
                        }
                    }
                }
                piece.setPosition(new Point(posx,posy));
            }
        } else {
            for (Plateau.TypePlateau plateau : PlateauValide(joueur.getProchainPlateau())){
                coup = new IAFields<>(null,null,null, plateau);
                listeCoups.add(coup);
            }
        }

        return listeCoups;
    }

    private ArrayList<Piece> listePieces(Joueur joueur, Plateau plateauCourant){
        ArrayList<Piece> listePieces = new ArrayList<>();
        Piece tmp = null;
        for (int i = 0; i < plateauCourant.getSize(); i++){
            for (int j = 0; j < plateauCourant.getSize(); j++){
                if (plateauCourant.getPiece(i,j) != null){
                    if (plateauCourant.getPiece(i,j).getOwner().equals(joueur)){
                        tmp = plateauCourant.getPiece(i,j);
                        listePieces.add(tmp);
                    }
                }
            }
        }
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