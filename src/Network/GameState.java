package Network;

import java.awt.Point;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;

public class GameState {
    public Plateau past;
    public Plateau present;
    public Plateau future;
    public Joueur joueur1;
    public Joueur joueur2;
    public Joueur joueurCourant;
    public Piece pieceCourante;

    public void afficherEtat() {
        System.out.println("==== ÉTAT DU JEU ====");
    
        // Prochain plateau des joueurs
        System.out.println("Prochain plateau Joueur 1 (" + joueur1.getNom() + ") : " + joueur1.getProchainPlateau());
        System.out.println("Prochain plateau Joueur 2 (" + joueur2.getNom() + ") : " + joueur2.getProchainPlateau());
        System.out.println("Clones Joueur 1 (" + joueur1.getNom() + ") : " + joueur1.getNbClones());
        System.out.println("Clones Joueur 2 (" + joueur2.getNom() + ") : " + joueur2.getNbClones());
    
        // Affichage de chaque plateau
        afficherPlateauUnique("PAST", past);
        afficherPlateauUnique("PRESENT", present);
        afficherPlateauUnique("FUTURE", future);
    
        // Joueur courant
        System.out.println("\nJoueur courant : " + (joueurCourant != null ? joueurCourant.getNom() : "Aucun"));
    
        // Pièce courante
        if (pieceCourante != null) {
            Point pos = pieceCourante.getPosition();
            System.out.println("Pièce courante : " + pieceCourante.getOwner().getNom() + " en position (" + pos.x + "," + pos.y + ")");
        } else {
            System.out.println("Pièce courante : Aucune");
        }
        System.out.println("==============");
    }
    
    private void afficherPlateauUnique(String nom, Plateau plateau) {
        System.out.println("\nPlateau " + nom + " :");
        for (int i = 0; i < plateau.getSize(); i++) {
            for (int j = 0; j < plateau.getSize(); j++) {
                Piece p = plateau.getPiece(i, j);
                if (p == null) {
                    System.out.print("[ ]");
                } else if (p.getOwner() == joueur1) {
                    System.out.print("[B]");
                } else {
                    System.out.print("[N]");
                }
            }
            System.out.println();
        }
    }
    
}
