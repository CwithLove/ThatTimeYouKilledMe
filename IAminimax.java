import java.awt.*;
import java.util.ArrayList;

public class IAminimax {
    private Jeu jeu;
    private final int PROFONDEUR_MAX = 3;
    private int difficulte = 1;



    //builder de l'ia
    IAminimax(int difficulte, Jeu jeu) {
        if (difficulte > PROFONDEUR_MAX){
            difficulte = PROFONDEUR_MAX;
        }
        this.difficulte = difficulte;
        this.jeu = jeu;
    }

    public IAFields<Piece,String,String,Plateau.TypePlateau> coupIA(Joueur joueur, Plateau plateauCourant, Plateau passe, Plateau present, Plateau futur){
        IAFields<Piece,String,String,Plateau.TypePlateau> best_coup = null;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;

        int best = Integer.MIN_VALUE;

        //Minimax de profondeur [difficulte] (max 3, 6 avec heuristique) avec elagage
        for (IAFields<Piece,String,String,Plateau.TypePlateau> coup : getCoupsPossible(joueur,plateauCourant,passe,present,futur)){

            //application des coups
            Plateau plateauClone = new Plateau(plateauCourant);
            Plateau passeClone = new Plateau(passe);
            Plateau presentClone = new Plateau(present);
            Plateau futurClone = new Plateau(futur);

            int x = (int)coup.getPremier().getPosition().getX();
            int y = (int)coup.getPremier().getPosition().getX();
            Piece pieceClone = plateauClone.getPiece(x, y);
            if (pieceClone == null){
                continue;
            }


            Coup coup1 = Coup.stringToCoup(pieceClone,plateauClone,coup.getSecond());
            if (coup1 == null){
                continue;
            }
            jeu.appliquerCoup(coup1,joueur,passeClone,presentClone,futurClone);

            Point positionCoup2 = pieceClone.getPosition();
            Piece pieceClone2 = plateauClone.getPiece((int) positionCoup2.getX(), (int) positionCoup2.getY());
            Coup coup2 = Coup.stringToCoup(pieceClone2,plateauClone,coup.getTroisieme());
            if (coup2 == null){
                continue;
            }
            jeu.appliquerCoup(coup2,joueur,passeClone,presentClone,futurClone);


            int score = alphabeta(this.difficulte-1,alpha,beta,false,joueur,plateauClone,passeClone,presentClone,futurClone);
            //System.out.println("Pour le coup :"+coup+", on a le score (pas encore a jour) :"+score);

            if (score > best){
                best = score;
                best_coup = coup;
            }
            alpha = Math.max(alpha, score);
        }



        //System.out.println("Le meilleur coup est:"+best_coup.getPremier().getPosition().getX()+","+best_coup.getPremier().getPosition().getY()+","+best_coup.getSecond()+","+best_coup.getTroisieme()+","+best_coup.getQuatrieme());
        return best_coup;
    }


    private int alphabeta(int profondeur, int alpha, int beta, boolean tourIA, Joueur joueur, Plateau plateau, Plateau passe, Plateau present, Plateau futur){
        if (profondeur <= 0){
            return heuristique(joueur,plateau,passe,present,futur);
        }

        ArrayList<IAFields<Piece,String,String,Plateau.TypePlateau>> coups = new ArrayList<>();
        if (coups.isEmpty()){
            return tourIA ? -1000 + profondeur : 1000 - profondeur;
        }

        int best = tourIA ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (IAFields<Piece,String,String,Plateau.TypePlateau> coup : coups){
            Plateau plateauClone = new Plateau(plateau);
            Plateau passeClone = new Plateau(passe);
            Plateau presentClone = new Plateau(present);
            Plateau futurClone = new Plateau(futur);

            int x = (int)coup.getPremier().getPosition().getX();
            int y = (int)coup.getPremier().getPosition().getX();
            Piece pieceClone = plateauClone.getPiece(x, y);
            if (pieceClone == null){
                continue;
            }


            Coup coup1 = Coup.stringToCoup(pieceClone,plateauClone,coup.getSecond());
            if (coup1 == null){
                continue;
            }
            jeu.appliquerCoup(coup1,joueur,passeClone,presentClone,futurClone);

            Point positionCoup2 = pieceClone.getPosition();
            Piece pieceClone2 = plateauClone.getPiece((int) positionCoup2.getX(), (int) positionCoup2.getY());
            Coup coup2 = Coup.stringToCoup(pieceClone2,plateauClone,coup.getTroisieme());
            if (coup2 == null){
                continue;
            }
            jeu.appliquerCoup(coup2,joueur,passeClone,presentClone,futurClone);

            if (tourIA){
                best = Math.max(best, alphabeta(profondeur - 1, alpha, beta, false,joueur,plateauClone,passeClone,presentClone,futurClone));
                alpha = Math.max(alpha, best);
            } else {
                best = Math.min(best, alphabeta(profondeur - 1, alpha, beta, true,joueur,plateauClone,passeClone,presentClone,futurClone));
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












    private ArrayList<IAFields<Piece,String,String,Plateau.TypePlateau>> getCoupsPossible(Joueur joueur, Plateau plateauCourant,Plateau passe,Plateau present,Plateau futur){
        ArrayList<IAFields<Piece,String,String,Plateau.TypePlateau>> listeCoups = new ArrayList<>();
        IAFields<Piece,String,String,Plateau.TypePlateau> coup = null;
        int posx = 0;
        int posy = 0;


        for (Piece piece : listePieces(joueur,plateauCourant)){
            //sauvegarde de la position de la piece
            posx = (int)piece.getPosition().getX();
            posy = (int)piece.getPosition().getY();
            for (String coup1 : listeCoups(joueur, piece ,plateauCourant,passe,present,futur)){
                //copie des plateaux pour simuler le 1er mouvement, afin de calculer le second
                Plateau plateauClone = new Plateau(plateauCourant);
                Plateau passeClone = new Plateau(passe);
                Plateau presentClone = new Plateau(present);
                Plateau futurClone = new Plateau(futur);

                //traduction en coup et application
                Coup coupEnCours = Coup.stringToCoup(piece,plateauClone,coup1);
                if (coupEnCours != null){
                    jeu.appliquerCoup(coupEnCours,joueur,passeClone,presentClone,futurClone);
                    for (String coup2 : listeCoups(joueur, piece ,plateauClone,passeClone,presentClone,futurClone)){
                        for (Plateau.TypePlateau plateau : PlateauValide(plateauCourant.getType())){
                            coup = new IAFields<>(piece,coup1,coup2,plateau);
                            //System.out.println("DEBUG "+coup+", "+posx+" "+posy);
                            listeCoups.add(coup);
                        }
                    }
                }
                piece.setPosition(new Point(posx,posy));
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

    private ArrayList<String> listeCoups(Joueur joueur, Piece piece, Plateau plateauCourant, Plateau passe, Plateau present, Plateau futur){
        ArrayList<String> listeCoups = new ArrayList<>();
        //System.out.println("DEBUG :"+piece.getPosition().getX()+" "+piece.getPosition().getY());

        //cas jump et clone
        switch(plateauCourant.getType()) {
            case FUTURE:
                Piece targetPiece = present.getPiece((int) piece.getPosition().getX(), (int) piece.getPosition().getY());
                if (targetPiece == null && joueur.getNbClones() > 0) {
                    listeCoups.add("CLONE");
                }
                break;
            case PAST:
                targetPiece = present.getPiece((int) piece.getPosition().getX(), (int) piece.getPosition().getY());
                if (targetPiece == null) {
                    listeCoups.add("JUMP");
                }
                break;
            case PRESENT:
                targetPiece = futur.getPiece((int) piece.getPosition().getX(), (int) piece.getPosition().getY());
                if (targetPiece == null) {
                    listeCoups.add("JUMP");
                }
                targetPiece = passe.getPiece((int) piece.getPosition().getX(), (int) piece.getPosition().getY());
                if (targetPiece == null && joueur.getNbClones() > 0) {
                    listeCoups.add("CLONE");
                }
                break;
            default:
                System.out.println("Erreur dans choisir listeCoups");
                break;
        }



        //Cas move
        String move = null;
        for (int i = -1; i <= 1; i++){
            for (int j = -1; j <= 1; j++){
                if (i != j) {
                    move = coupsMove(piece, plateauCourant, i, j);
                    if (move != null) {
                        listeCoups.add(move);
                    }
                }
            }
        }


        return listeCoups;

    }

    private String coupsMove(Piece piece, Plateau plateauCourant, int x, int y){
        String listeCoups = null;
        int newX = piece.getPosition().x + x;
        int newY = piece.getPosition().y + y;
        if (newX >= 0 && newX < plateauCourant.getSize() && newY >= 0 && newY < plateauCourant.getSize()) {
            Piece sidePiece = plateauCourant.getPiece(newX, newY);
            if (sidePiece == null || !sidePiece.getOwner().equals(piece.getOwner())) {
                if (x == -1 && y == 0){
                    listeCoups = "UP";
                } else if (x == 1 && y == 0){
                    listeCoups = "DOWN";
                } else if (x == 0 && y == -1){
                    listeCoups = "LEFT";
                } else if (x == 0 && y == 1){
                    listeCoups = "RIGHT";
                }
                //ici il reste les x = 1, y = -1 et x = -1, y = 1
            }
        }
        return listeCoups;
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