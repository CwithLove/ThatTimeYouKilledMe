import java.util.*;
import java.awt.Point;

public class IAminmax {
    private int difficulte = 1;
    private ArrayList<Couple<String,String>> listeCoup = new ArrayList<>();
    private Random r = new Random();

    IAminmax(int difficulte){
        this.difficulte = difficulte;
        this.listeCoup = listeCoup;
    }

    public IAFields<Couple<Integer,Integer>,String,String,String> coup(Joueur joueur, Plateau plateau, Plateau past, Plateau present, Plateau future){
        //Etape 1: Choisir une piece a jouer au hasard
        ArrayList<Couple<Integer,Integer>> pions = joueur.listePiecesJoueur(plateau);
        int choice = r.nextInt(pions.size());
        Couple<Integer,Integer> position = pions.get(choice);

        //Etape 2: choisir 2 coups à jouer
        ArrayList<String> move = new ArrayList<>();
        Plateau.TypePlateau simuleTemporalite = plateau.getType();
        for (int i = 0; i < 2; i++){
            String dir = null;
            List<String> choixPossibles = new ArrayList<>();

            if (simuleTemporalite != Plateau.TypePlateau.FUTURE) {
                choixPossibles.add("JUMP"); // JUMP possible
            }
            choixPossibles.add("MOVE"); // MOVE toujours possible
            if (simuleTemporalite != Plateau.TypePlateau.PAST) {
                choixPossibles.add("CLONE"); // CLONE possible
            }

            dir = choixPossibles.get(r.nextInt(choixPossibles.size()));

            if (dir.equals("JUMP")) {
                if (simuleTemporalite == Plateau.TypePlateau.PRESENT)
                    simuleTemporalite = Plateau.TypePlateau.FUTURE;
                else if (simuleTemporalite == Plateau.TypePlateau.PAST)
                    simuleTemporalite = Plateau.TypePlateau.PRESENT;
                else {
                    System.out.println("Erreur de temporalité (jump):" + simuleTemporalite);
                    i--; // réessayer ce tour
                    continue;
                }
            } else if (dir.equals("CLONE")) {
                if (simuleTemporalite == Plateau.TypePlateau.PRESENT)
                    simuleTemporalite = Plateau.TypePlateau.PAST;
                else if (simuleTemporalite == Plateau.TypePlateau.FUTURE)
                    simuleTemporalite = Plateau.TypePlateau.PRESENT;
                else {
                    System.out.println("Erreur de temporalité (clone):" + simuleTemporalite);
                    i--; // réessayer ce tour
                    continue;
                }
            } else if (dir.equals("MOVE")) {
                boolean validDirection = false;
                while (!validDirection) {
                    int direction = r.nextInt(4);
                    switch (direction) {
                        case 0:
                            if (position.getPremier() > 0) {
                                dir = "UP";
                                validDirection = true;
                            }
                            break;
                        case 1:
                            if (position.getPremier() < plateau.getSize() - 1) {
                                dir = "DOWN";
                                validDirection = true;
                            }
                            break;
                        case 2:
                            if (position.getSecond() > 0) {
                                dir = "LEFT";
                                validDirection = true;
                            }
                            break;
                        case 3:
                            if (position.getSecond() < plateau.getSize() - 1) {
                                dir = "RIGHT";
                                validDirection = true;
                            }
                            break;
                    }
                }
            }

            move.add(dir);
        }

        Couple<String, String> mouvement;
        if (move.size() >= 2) {
            mouvement = new Couple<>(move.get(0), move.get(1));
        } else {
            System.out.println("BUG Erreur de coup dans l'ia.");
            return null;
        }

        //Etape 3: Le nouveau plateau
        Plateau newplateau = choisirPlateauValide(joueur,plateau.getType(),past,present,future);
        String plateau2 = newplateau.plateauToString();


        IAFields<Couple<Integer,Integer>,String,String,String> coupActuel = new IAFields<>(position,mouvement.getPremier(),mouvement.getSecond(),plateau2);
        //System.out.println("Coup actuel: "+coupActuel);
        return coupActuel;
    }












    public IAFields<Couple<Integer,Integer>,String,String,String> choisitCoupIa(Joueur joueur1, Joueur joueur2, Plateau plateau, Plateau past, Plateau present, Plateau future){
        ArrayList<Couple<ArrayList<IAFields<Couple<Integer,Integer>,String,String,String>>,Integer>> parties = new ArrayList<>();
        Jeu jeu = new Jeu();

        //difficulte (equivaut au nombre de parties simulées)
        for (int z=0;z<this.difficulte;z++){

            //set et reset de la partie
            ArrayList<IAFields<Couple<Integer,Integer>,String,String,String>> partie = new ArrayList<>();

            IAFields<Couple<Integer,Integer>,String,String,String> coupActuel = null;

            Joueur simuleJoueur = new Joueur(joueur1.getNom(),joueur1.getId(),joueur1.getNbClones(),joueur1.getProchainPlateau());
            Joueur IAjoueur = new Joueur(joueur2.getNom(),joueur2.getId(),joueur2.getNbClones(),joueur2.getProchainPlateau());

            Plateau plateauIA = new Plateau(plateau.getType(),simuleJoueur,IAjoueur);
            Plateau plateauTraitant = plateauIA;
            Plateau pastIA = new Plateau(past.getType(),simuleJoueur,IAjoueur);
            Plateau presentIA = new Plateau(present.getType(),simuleJoueur,IAjoueur);
            Plateau futureIA = new Plateau(future.getType(),simuleJoueur,IAjoueur);

            Piece pieceCourante = null;

            Coup coup = null;
            String coupstr = null;

            //on simule des parties entières
            while (gameOverIA(pastIA,presentIA,futureIA) == 0){
                //JOUEUR IA
                //Etape 1: Recup piece
                Plateau.TypePlateau precedent = IAjoueur.getProchainPlateau();
                plateauTraitant = choisirPlateauValide(IAjoueur, precedent, pastIA, presentIA, futureIA);
                if (plateauTraitant == null) {
                    System.out.println("Aucun plateau valide pour l'IA.");
                    if (gameOverIA(pastIA,presentIA,futureIA) != 0){
                        System.out.println("Joueur a gagné.");
                    }
                    break;
                }
                coupActuel = coup(IAjoueur, plateauTraitant,pastIA,presentIA,futureIA);
                //System.out.println("Plateau Actuel: "+plateauTraitant.getType()+", coup actuel :" + coupActuel);
                pieceCourante = plateauTraitant.getPiece(coupActuel.getPosPiece().getPremier(),coupActuel.getPosPiece().getSecond());
                if (pieceCourante == null) {
                    System.out.println("Erreur: la pièce à la position " + coupActuel.getPosPiece() + " est null.");
                    break; // ou return ou break, selon le contexte
                }

                partie.add(coupActuel);
                //Etape 2: Joue les 2 coups
                for (int i = 0; i < 2; i++){
                    if (i == 0){
                        coupstr = coupActuel.getAction1();
                    } else if (i == 1){
                        coupstr = coupActuel.getAction2();
                    }

                    Coup couptmp = null;
                    if (!coupstr.equals("JUMP") && !coupstr.equals("CLONE")) {
                        String tmpstr = "MOVE";
                        couptmp = new Coup(pieceCourante, new Point(0, 0), plateauTraitant, Coup.TypeCoup.valueOf(tmpstr));
                    } else {
                        couptmp = new Coup(pieceCourante, new Point(0, 0), plateauTraitant, Coup.TypeCoup.valueOf(coupstr));
                    }
                    //System.out.println("Avant: "+couptmp.getPltCourant().getType()+", ou "+plateauTraitant.getType());
                    coup = couptmp.stringToCoup(pieceCourante,plateauTraitant,coupstr);
                    //System.out.println("Apres: "+coup.getPltCourant().getType()+", "+coup.getTypeCoup());

                    jeu.appliquerCoup(coup,IAjoueur,pastIA,presentIA,futureIA);
                    if (gameOverIA(pastIA,presentIA,futureIA) != 0){
                        break;
                    }

                    switch (IAjoueur.getProchainPlateau()) {
                        case PAST:
                            plateauTraitant = pastIA;
                            break;
                        case PRESENT:
                            plateauTraitant = presentIA;
                            break;
                        case FUTURE:
                            plateauTraitant = futureIA;
                            break;
                    }
                    IAjoueur.setProchainPlateau(plateauTraitant.getType());
                }
                //"retour" dans le while


                if (gameOverIA(pastIA,presentIA,futureIA) != 0){
                    break;
                }



                //Etape 3: Change de plateau

                Plateau.TypePlateau prochainPlateau = Plateau.TypePlateau.valueOf(coupActuel.getPlateau());
                IAjoueur.setProchainPlateau(prochainPlateau);




                //JOUEUR SIMULE
                //Etape 1: Recup piece
                precedent = simuleJoueur.getProchainPlateau();
                plateauTraitant = choisirPlateauValide(simuleJoueur, precedent, pastIA, presentIA, futureIA);
                if (plateauTraitant == null) {
                    System.out.println("Aucun plateau valide pour l'IA.");
                    if (gameOverIA(pastIA,presentIA,futureIA) != 0){
                        System.out.println("IA a gagné.");
                    }
                    break;
                }
                coupActuel = coup(simuleJoueur, plateauTraitant,pastIA,presentIA,futureIA);
                pieceCourante = plateauTraitant.getPiece(coupActuel.getPosPiece().getPremier(),coupActuel.getPosPiece().getSecond());
                if (pieceCourante == null) {
                    System.out.println("Erreur dans la recuperation de la piece.");
                    break;
                }

                partie.add(coupActuel);
                //Etape 2: Joue les 2 coups
                for (int i = 0; i < 2; i++){
                    if (i == 0){
                        coupstr = coupActuel.getAction1();
                    } else if (i == 1){
                        coupstr = coupActuel.getAction2();
                    }

                    Coup couptmp = null;
                    if (!coupstr.equals("JUMP") && !coupstr.equals("CLONE")) {
                        String tmpstr = "MOVE";
                        couptmp = new Coup(pieceCourante, new Point(0, 0), plateauTraitant, Coup.TypeCoup.valueOf(tmpstr));
                    } else {
                        couptmp = new Coup(pieceCourante, new Point(0, 0), plateauTraitant, Coup.TypeCoup.valueOf(coupstr));
                    }
                    //System.out.println("JOUEUR SIM");
                    coup = couptmp.stringToCoup(pieceCourante,plateauTraitant,coupstr);
                    jeu.appliquerCoup(coup,simuleJoueur,pastIA,presentIA,futureIA);
                    //System.out.println("FIN DU JOUEUR SIM");

                    if (gameOverIA(pastIA,presentIA,futureIA) != 0){
                        break;
                    }

                    switch (simuleJoueur.getProchainPlateau()) {
                        case PAST:
                            plateauTraitant = pastIA;
                            break;
                        case PRESENT:
                            plateauTraitant = presentIA;
                            break;
                        case FUTURE:
                            plateauTraitant = futureIA;
                            break;
                    }
                    simuleJoueur.setProchainPlateau(plateauTraitant.getType());
                }
                //"retour" dans le while


                if (gameOverIA(pastIA,presentIA,futureIA) != 0){
                    break;
                }


                //Etape 3: Change de plateau

                prochainPlateau = Plateau.TypePlateau.valueOf(coupActuel.getPlateau());
                simuleJoueur.setProchainPlateau(prochainPlateau);


            }
            parties.add(new Couple<ArrayList<IAFields<Couple<Integer,Integer>,String,String,String>>, Integer>(partie,gameOverIA(pastIA,presentIA,futureIA)));
        }

        //derniere etape, choisir une partie gagnante
        IAFields<Couple<Integer,Integer>,String,String,String> victoire = null;
        int partiesize = parties.size();
        for (int y=0;y<partiesize;y++){
            if (parties.get(y).getSecond() == 2){
                ArrayList<IAFields<Couple<Integer,Integer>,String,String,String>> tmp = parties.get(y).getPremier();
                victoire = new IAFields<Couple<Integer,Integer>,String,String,String>(tmp.get(0));
                return victoire;
            }
        }
        ArrayList<IAFields<Couple<Integer,Integer>,String,String,String>> tmp = parties.get(partiesize-1).getPremier();
        victoire = new IAFields<Couple<Integer,Integer>,String,String,String>(tmp.get(0));
        return victoire;
    }










    public int gameOverIA(Plateau past, Plateau present, Plateau future) {
        //System.out.println("Nombre de blancs: passe "+past.getNbBlancs()+", present "+present.getNbBlancs()+", future "+future.getNbBlancs());
        //System.out.println("Nombre de Noirs: passe "+past.getNbNoirs()+", present "+present.getNbNoirs()+", future "+future.getNbNoirs());
        int noirsSurPlateaux = 0;
        if (past.getNbNoirs() > 0) noirsSurPlateaux++;
        if (present.getNbNoirs() > 0) noirsSurPlateaux++;
        if (future.getNbNoirs() > 0) noirsSurPlateaux++;

        int blancsSurPlateaux = 0;
        if (past.getNbBlancs() > 0) blancsSurPlateaux++;
        if (present.getNbBlancs() > 0) blancsSurPlateaux++;
        if (future.getNbBlancs() > 0) blancsSurPlateaux++;

        if (noirsSurPlateaux == 1) {
            return 1; // Joueur 1 (blanc) gagne
        }
        if (blancsSurPlateaux == 1) {
            return 2; // Joueur 2 (noir) gagne
        }
        return 0;
    }




    public Plateau choisirPlateauValide(Joueur joueur, Plateau.TypePlateau precedent, Plateau past, Plateau present, Plateau future) {
        List<Plateau> candidats = new ArrayList<>();

        if (!joueur.listePiecesJoueur(past).isEmpty() && precedent != Plateau.TypePlateau.PAST) {
            candidats.add(past);
        }
        if (!joueur.listePiecesJoueur(present).isEmpty() && precedent != Plateau.TypePlateau.PRESENT) {
            candidats.add(present);
        }
        if (!joueur.listePiecesJoueur(future).isEmpty() && precedent != Plateau.TypePlateau.FUTURE) {
            candidats.add(future);
        }

        if (candidats.isEmpty()) {
            // Si tous les plateaux sont vides ou identiques au précédent, on ignore la contrainte sur le précédent
            if (!joueur.listePiecesJoueur(past).isEmpty()) return past;
            if (!joueur.listePiecesJoueur(present).isEmpty()) return present;
            if (!joueur.listePiecesJoueur(future).isEmpty()) return future;

            // Aucun plateau valide => fin de partie ?
            return null;
        }

        return candidats.get(new Random().nextInt(candidats.size()));
    }




}