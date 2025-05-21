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
import java.util.Random;

public class IAminimax {

    private Jeu jeu;
    private final int PROFONDEUR_MAX = 5;
    private int difficulte = 1;
    private String mode = "";
    private Random r = new Random();

    //builder de l'ia
    public IAminimax(int difficulte, Jeu jeu) {
        if (difficulte > PROFONDEUR_MAX) {
            difficulte = PROFONDEUR_MAX;
        }
        this.difficulte = difficulte;
        this.jeu = jeu;
        if (difficulte >= PROFONDEUR_MAX) {
            this.mode = "HARD";
        } else if (difficulte >= (int) (PROFONDEUR_MAX * 0.5)) {
            this.mode = "MEDIUM";
        } else {
            this.mode = "EASY";
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

        //Minimax de profondeur [difficulte] (max 3, 6 avec heuristique) avec elagage
        for (IAFields<Piece, String, String, Plateau.TypePlateau> tour : tours) {
            Jeu jeuClone = new Jeu(gameState);
            if (tour.getPremier() != null) {
                Jeu sim = new Jeu(jeuClone);
                int x = (int) tour.getPremier().getPosition().getX();
                int y = (int) tour.getPremier().getPosition().getY();
                Piece pieceCourante = sim.getPlateauCourant().getPiece(x, y);
                if (pieceCourante == null) {
                    //System.out.println("IAMinimax: Piece courante null");
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
                    break;
                }
                sim.appliquerCoup(coup2);
                jeuClone = sim;
            }

            jeuClone.choisirPlateau(tour.getQuatrieme());

            int score = alphabeta(this.difficulte - 1, alpha, beta, false, joueur, jeuClone);
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
                seuil = (int) (best * 0.95);
            } else if (this.mode.equals("MEDIUM")) {
                seuil = (int) (best * 0.75);
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
        return best_coup;
    }

    private int alphabeta(int profondeur, int alpha, int beta, boolean tourIA, Joueur joueur, Jeu clone) {
        if (profondeur <= 0) {
            // return heuristique(clone);
            return heuristique(clone, joueur);
        }

        ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> tours = null;
        if (!tourIA) {
            if (joueur.getId() == 1) {
                tours = getTourPossible(clone.getJoueur2(), clone);
            } else {
                tours = getTourPossible(clone.getJoueur1(), clone);
            }
        } else {
            tours = getTourPossible(joueur, clone);
        }

        if (tours.isEmpty()) {
            return tourIA ? -1000 + profondeur : 1000 - profondeur;
        }

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
                if (coup2 == null) {
                    continue;
                }
                jeuClone.appliquerCoup(coup2);

                if (tourIA) {
                    best = Math.max(best, alphabeta(profondeur - 1, alpha, beta, false, joueur, jeuClone));
                    alpha = Math.max(alpha, best);
                } else {
                    beta = Math.min(beta, best);
                    best = Math.min(best, alphabeta(profondeur - 1, alpha, beta, true, joueur, jeuClone));
                }
            }

            if (beta <= alpha) {
                break;
            }
        }
        return best;
    }

    //heuristique
    // private int heuristique(Jeu jeu) {
    //     Plateau passe = jeu.getPast();
    //     Plateau present = jeu.getPresent();
    //     Plateau futur = jeu.getFuture();
    //     Plateau plateauCourant = jeu.getPlateauCourant();
    //     Joueur joueur = jeu.getJoueurCourant();
    //     Joueur opponent = null;
    //     if (joueur.getId() == 1) {
    //         opponent = jeu.getJoueur2();
    //     } else {
    //         opponent = jeu.getJoueur1();
    //     }
    //     int score = 0;
    //     //heuristique 1: nb Pieces restantes, +poids pour chaque piece restante (heuristique efficace)
    //     score += scorePiecesRestantes(joueur, passe, present, futur, 1);
    //     //heuritique 2: position sur plateau, +poids au milieu, 0 sur les bords, -poids dans les coins (heuristique peu efficace)
    //     score += scorePositionPlateau(joueur, plateauCourant, 3);
    //     //heuristique3: nombre de pieces restantes dans l'inventaire (currentPlayer - opp.Player)*poids (heuristique efficace)
    //     score += scorePionsInventaire(joueur, opponent, 3);
    //     //heurisitque 4: presence plateau, +2 pour chaque plateau ou l'ia se situe, -2 si elle n'est que sur 1 plateau (heuristique moyenne)
    //     score += presencePlateau(joueur, passe, present, futur, 5);
    //     //heuristique 5: nombre de pièces par rapport à l'adversaire, (own piece - opponent piece)*poids (heuristique très efficace)
    //     score += piecesContreAdversaire(joueur, passe, present, futur, 10);
    //     return score;
    // }
    // //heuristique 1: nb Pieces restantes, +2 pour chaque piece restante
    // private int scorePiecesRestantes(Joueur joueur, Plateau passe, Plateau present, Plateau futur, int poids) {
    //     int score = 0;
    //     if (joueur.getNom().equals("Blanc")) {
    //         score += (passe.getNbBlancs() + present.getNbBlancs() + futur.getNbBlancs()) * poids;
    //     } else if (joueur.getNom().equals("Noir")) {
    //         score += (passe.getNbNoirs() + present.getNbNoirs() + futur.getNbNoirs()) * poids;
    //     }
    //     return score;
    // }
    // //heuritique 2: position sur plateau, +2 au milieu, 0 sur les bords, -2 dans les coins
    // private int scorePositionPlateau(Joueur joueur, Plateau plateauCourant, int poids) {
    //     int score = 0;
    //     Piece tmp = null;
    //     for (int i = 0; i < plateauCourant.getSize(); i++) {
    //         for (int j = 0; j < plateauCourant.getSize(); j++) {
    //             if (plateauCourant.getPiece(i, j) != null) {
    //                 //Les bords
    //                 if ((i == 0 || i == 3) && (j == 0 || j == 3) && plateauCourant.getPiece(i, j).getOwner().equals(joueur)) {
    //                     score -= poids;
    //                 } //les cotes
    //                 else if ((i == 0 || i == 3) || (j == 0 || j == 3) && plateauCourant.getPiece(i, j).getOwner().equals(joueur)) {
    //                     score += 0; //cette condition est la dans le cas ou il faut modifier le score pour une piece au bord
    //                 } //le centre (ce qu'il erste
    //                 else {
    //                     score += poids;
    //                 }
    //             }
    //         }
    //     }
    //     return score;
    // }
    // //heuristique3: nombre de pieces restantes dans l'inventaire (currentPlayer - opp.Player)*poids (heuristique efficace)
    // private int scorePionsInventaire(Joueur joueur, Joueur opponent, int poids) {
    //     return (joueur.getNbClones() - opponent.getNbClones()) * poids;
    // }
    // //heurisitque 4: presence plateau, +2 pour chaque plateau ou l'ia se situe, -2 si elle n'est que sur 1 plateau
    // private int presencePlateau(Joueur joueur, Plateau passe, Plateau present, Plateau futur, int poids) {
    //     int score = 0;
    //     int nbPlateau = 0;
    //     if (joueur.existePion(passe)) {
    //         nbPlateau++;
    //     }
    //     if (joueur.existePion(present)) {
    //         nbPlateau++;
    //     }
    //     if (joueur.existePion(futur)) {
    //         nbPlateau++;
    //     }
    //     switch (nbPlateau) {
    //         case 1:
    //             score = -poids;
    //             break;
    //         case 2:
    //             score = poids;
    //             break;
    //         case 3:
    //             score = 2 * poids;
    //             break;
    //         default:
    //             break;
    //     }
    //     return score;
    // }
    // //heuristique 5: nombre de pièces par rapport à l'adversaire, (own piece - opponent piece)*10
    // private int piecesContreAdversaire(Joueur joueur, Plateau passe, Plateau present, Plateau futur, int poids) {
    //     int score = 0;
    //     if (joueur.getNom().equals("Blanc")) {
    //         score += (passe.getNbBlancs() - passe.getNbNoirs()) * poids + (present.getNbBlancs() - present.getNbNoirs()) * poids + (futur.getNbBlancs() - futur.getNbNoirs()) * poids;
    //     } else if (joueur.getNom().equals("Noir")) {
    //         score += (passe.getNbNoirs() - passe.getNbBlancs()) * poids + (present.getNbNoirs() - present.getNbBlancs()) * poids + (futur.getNbNoirs() - futur.getNbBlancs()) * poids;
    //     }
    //     return score;
    // }
    // heuristique proposee par Chu
    private int heuristique(Jeu jeu, Joueur joueur) {
        int wMateriel = 10;
        int wPositionnel = 7;
        int wMobilite = 2;
        int wRisque = 8;
        int wProchainPlateau = 4;

        return wMateriel * hMateriel(jeu, joueur)
                + wPositionnel * hPositionnel(jeu, joueur) +
                wMobilite*hMobilite(jeu, joueur) +
                wRisque*hRisque(jeu, joueur) +
                wProchainPlateau*hProchainPlateau(jeu, joueur);
    }

    // h1 materiel:
    private int hMateriel(Jeu jeu, Joueur joueur) {
        int score = 0;
        // Evaluer chaque piece sur le plateau
        for (int row = 0; row < jeu.getPlateauCourant().getSize(); row++) {
            for (int col = 0; col < jeu.getPlateauCourant().getSize(); col++) {
                Piece piece = jeu.getPlateauCourant().getPiece(row, col);
                if (piece != null && piece.getOwner().equals(joueur)) {
                    score += 10; // +10 pour chaque piece du AI
                } else if (piece != null && !piece.getOwner().equals(joueur)) {
                    score -= 10; // -10 pour chaque piece de l'adversaire
                }
            }
        }

        // Evaluer les pieces restantes dans l'inventaire (clones)
        Joueur AI = joueur.getId() == 1 ? jeu.getJoueur1() : jeu.getJoueur2();
        Joueur adversaire = joueur.getId() == 1 ? jeu.getJoueur2() : jeu.getJoueur1();

        score += AI.getNbClones() * 5; // +5 pour chaque clone du AI
        score -= adversaire.getNbClones() * 5; // -5 pour chaque clone de l'adversaire
        return score;
    }

    // h2 positionnel:
    private int hPositionnel(Jeu jeu, Joueur joueur) {
        int score = 0;

        score = jeu.gameOver(joueur) == joueur.getId() ? score + 10000 : score; // +10000 si le joueur gagne
        score = (jeu.gameOver(joueur) != joueur.getId() && jeu.gameOver(joueur) != 0) ? score - 10000 : score; // -10000 si le joueur perd

        // Evaluer l'existence de IA sur les plateaux
        int numBlancs = 0, numNoirs = 0;
        if (jeu.getPast().getNbBlancs() > 0) {
            numBlancs++;
        }
        if (jeu.getPresent().getNbBlancs() > 0) {
            numBlancs++;
        }
        if (jeu.getFuture().getNbBlancs() > 0) {
            numBlancs++;
        }
        if (jeu.getPast().getNbNoirs() > 0) {
            numNoirs++;
        }
        if (jeu.getPresent().getNbNoirs() > 0) {
            numNoirs++;
        }
        if (jeu.getFuture().getNbNoirs() > 0) {
            numNoirs++;
        }

        int AI = joueur.getId() == 1 ? numBlancs : numNoirs;
        int adversaire = joueur.getId() == 1 ? numNoirs : numBlancs;

        score = AI == 3 ? score + 100 : score; // +100 si le AI est sur les 3 plateaux
        score = adversaire == 3 ? score - 100 : score; // -100 si l'adversaire est sur les 3 plateaux
        score = AI == 2 ? score - 25 : score; // -25 si le AI est sur 2 plateaux
        score = adversaire == 2 ? score + 25 : score; // +25 si l'adversaire est sur 2 plateaux

        // Evaluer la position des pieces sur chaque plateau
        for (Plateau plateau : new Plateau[]{jeu.getPast(), jeu.getPresent(), jeu.getFuture()}) {
            int size = plateau.getSize();
            int mid = size / 2;
            // On considère les 4 cases centrales
            for (int i = mid - 1; i <= mid; i++) {
                for (int j = mid - 1; j <= mid; j++) {
                    Piece piece = plateau.getPiece(i, j);
                    if (piece != null) {
                        if (piece.getOwner().equals(joueur)) {
                            score += 1;
                        } else {
                            score -= 1;
                        }
                    }
                }
            }

            // Evaluer les positions au coin
            // Coins : (0,0), (0,size-1), (size-1,0), (size-1,size-1)
            int[][] coins = {{0, 0}, {0, size - 1}, {size - 1, 0}, {size - 1, size - 1}};
            for (int[] coin : coins) {
                Piece coinPiece = plateau.getPiece(coin[0], coin[1]);
                if (coinPiece != null) {
                    Joueur owner = coinPiece.getOwner();
                    // Calculer la distance de Manhattan minimale avec un pion adverse
                    int minDist = Integer.MAX_VALUE;
                    for (int x = 0; x < size; x++) {
                        for (int y = 0; y < size; y++) {
                            Piece other = plateau.getPiece(x, y);
                            if (other != null && !other.getOwner().equals(owner)) {
                                int dist = Math.abs(coin[0] - x) + Math.abs(coin[1] - y);
                                if (dist < minDist) {
                                    minDist = dist;
                                }
                            }
                        }
                    }
                    if (owner.equals(joueur)) {
                        score -= 3;
                        if (minDist <= 2) {
                            score -= 25;
                        }
                    } else {
                        score += 3;
                        if (minDist <= 2) {
                            score += 25;
                        }
                    }
                }
            }
        }

        return score;
    }

    // h3 mobilite:
    private int hMobilite(Jeu jeu, Joueur joueur) {
        // Calculer la mobilité sur tous les plateaux pour le joueur donné
        int score = 0;
        for (Plateau plateau : new Plateau[]{jeu.getPast(), jeu.getPresent(), jeu.getFuture()}) {
            ArrayList<Piece> pieces = new ArrayList<>();
            for (int i = 0; i < plateau.getSize(); i++) {
                for (int j = 0; j < plateau.getSize(); j++) {
                    Piece piece = plateau.getPiece(i, j);
                    if (piece != null && piece.getOwner().equals(joueur)) {
                        pieces.add(piece);
                    }
                }
            }
            for (Piece piece : pieces) {
                ArrayList<Coup> coups = jeu.getCoupPossibles(plateau, piece);
                if (coups != null) {
                    score += coups.size();
                }
            }
        }
        return score;
    }

    // h4 risque:
    private int hRisque(Jeu jeu, Joueur joueur) {
        int score = 0;
        for (Plateau plateau : new Plateau[]{jeu.getPast(), jeu.getPresent(), jeu.getFuture()}) {
            ArrayList<Point> ownPositions = new ArrayList<>();
            ArrayList<Point> oppPositions = new ArrayList<>();
            for (int i = 0; i < plateau.getSize(); i++) {
                for (int j = 0; j < plateau.getSize(); j++) {
                    Piece piece = plateau.getPiece(i, j);
                    if (piece != null) {
                        if (piece.getOwner().equals(joueur)) {
                            ownPositions.add(new Point(i, j));
                        } else {
                            oppPositions.add(new Point(i, j));
                        }
                    }
                }
            }
            // Pour chaque paire de pions du joueur
            for (int i = 0; i < ownPositions.size(); i++) {
                for (int j = i + 1; j < ownPositions.size(); j++) {
                    int distOwn = Math.abs(ownPositions.get(i).x - ownPositions.get(j).x)
                                + Math.abs(ownPositions.get(i).y - ownPositions.get(j).y);
                    // Distance min avec un pion adverse pour chaque pion du joueur
                    int minDistOppI = Integer.MAX_VALUE;
                    int minDistOppJ = Integer.MAX_VALUE;
                    for (Point opp : oppPositions) {
                        int distI = Math.abs(ownPositions.get(i).x - opp.x)
                                  + Math.abs(ownPositions.get(i).y - opp.y);
                        int distJ = Math.abs(ownPositions.get(j).x - opp.x)
                                  + Math.abs(ownPositions.get(j).y - opp.y);
                        if (distI < minDistOppI) minDistOppI = distI;
                        if (distJ < minDistOppJ) minDistOppJ = distJ;
                    }
                    // Cas 1 : distOwn == 2 et minDistOpp == 1
                    if (distOwn == 2 && (minDistOppI == 1 || minDistOppJ == 1)) {
                        score -= 50;
                    }
                    // Cas 2 : distOwn == 1 et minDistOpp == 2
                    if (distOwn == 1 && (minDistOppI == 2 || minDistOppJ == 2)) {
                        score -= 50;
                    }
                }
            }
            // Inverse : pour chaque paire de pions adverses
            for (int i = 0; i < oppPositions.size(); i++) {
                for (int j = i + 1; j < oppPositions.size(); j++) {
                    int distOpp = Math.abs(oppPositions.get(i).x - oppPositions.get(j).x)
                                + Math.abs(oppPositions.get(i).y - oppPositions.get(j).y);
                    int minDistOwnI = Integer.MAX_VALUE;
                    int minDistOwnJ = Integer.MAX_VALUE;
                    for (Point own : ownPositions) {
                        int distI = Math.abs(oppPositions.get(i).x - own.x)
                                  + Math.abs(oppPositions.get(i).y - own.y);
                        int distJ = Math.abs(oppPositions.get(j).x - own.x)
                                  + Math.abs(oppPositions.get(j).y - own.y);
                        if (distI < minDistOwnI) minDistOwnI = distI;
                        if (distJ < minDistOwnJ) minDistOwnJ = distJ;
                    }
                    if (distOpp == 2 && (minDistOwnI == 1 || minDistOwnJ == 1)) {
                        score += 50;
                    }
                    if (distOpp == 1 && (minDistOwnI == 2 || minDistOwnJ == 2)) {
                        score += 50;
                    }
                }
            }
        }
        return score;
    }

    // h5 plt prochain
    private int hProchainPlateau(Jeu jeu, Joueur joueur) {
        int score = 0;
        Plateau.TypePlateau prochainPlateau = joueur.getProchainPlateau();

        // Si joueur est 1, vérifier sur prochainPlateau s'il existe des pions, sinon -50
        if (joueur.getId() == 1) {
            Plateau plateau = null;
            if (prochainPlateau == Plateau.TypePlateau.PAST) {
                plateau = jeu.getPast();
            } else if (prochainPlateau == Plateau.TypePlateau.PRESENT) {
                plateau = jeu.getPresent();
            } else if (prochainPlateau == Plateau.TypePlateau.FUTURE) {
                plateau = jeu.getFuture();
            }
            boolean pionExiste = false;
            for (int i = 0; i < plateau.getSize(); i++) {
                for (int j = 0; j < plateau.getSize(); j++) {
                    Piece piece = plateau.getPiece(i, j);
                    if (piece != null && piece.getOwner().equals(joueur)) {
                        pionExiste = true;
                        break;
                    }
                }
                if (pionExiste) {
                    break;
                }
            }
            if (!pionExiste) {
                score -= 50;
            }
        }

        // Calculer la mobilité sur chaque plateau
        int maxMobilite = Integer.MIN_VALUE;
        int minMobilite = Integer.MAX_VALUE;
        Plateau.TypePlateau maxPlateau = null;
        Plateau.TypePlateau minPlateau = null;
        for (Plateau.TypePlateau type : Plateau.TypePlateau.values()) {
            Plateau plateau = null;
            if (type == Plateau.TypePlateau.PAST) {
                plateau = jeu.getPast(); 
            }else if (type == Plateau.TypePlateau.PRESENT) {
                plateau = jeu.getPresent(); 
            }else if (type == Plateau.TypePlateau.FUTURE) {
                plateau = jeu.getFuture();
            }

            int mobilite = 0;
            for (int i = 0; i < plateau.getSize(); i++) {
                for (int j = 0; j < plateau.getSize(); j++) {
                    Piece piece = plateau.getPiece(i, j);
                    if (piece != null && piece.getOwner().equals(joueur)) {
                        ArrayList<Coup> coups = jeu.getCoupPossibles(plateau, piece);
                        if (coups != null) {
                            mobilite += coups.size();
                        }
                    }
                }
            }
            if (mobilite > maxMobilite) {
                maxMobilite = mobilite;
                maxPlateau = type;
            }
            if (mobilite < minMobilite) {
                minMobilite = mobilite;
                minPlateau = type;
            }
        }

        // Si prochainPlateau a la plus grande mobilité alors +50, si la plus faible -50
        if (prochainPlateau == maxPlateau) {
            score += 50;
        }
        if (prochainPlateau == minPlateau) {
            score -= 50;
        }
        return score;
    }

    private ArrayList<IAFields<Piece, String, String, Plateau.TypePlateau>> getTourPossible(Joueur joueur, Jeu clone) {
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
