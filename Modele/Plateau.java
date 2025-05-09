package Modele;

import java.awt.Point;

public class Plateau {
    public enum TypePlateau {
        PAST,
        PRESENT,
        FUTURE
    }

    private TypePlateau type;
    private Piece[][] grille;
    private int nbBlancs;
    private int nbNoirs;
    private int size;

    public Plateau(TypePlateau type, Joueur joueur1, Joueur joueur2) {
        this.nbBlancs = 1;
        this.nbNoirs = 1;
        this.type = type;
        this.grille = new Piece[4][4];
        for (int lig = 0; lig < 4; lig++) {
            for (int col = 0; col < 4; col++) {
                this.grille[lig][col] = null;
            }
        }

        // Initialisation des pièces sur le plateau
        this.grille[0][0] = new Piece(joueur1, new Point(0, 0));
        this.grille[3][3] = new Piece(joueur2, new Point(3, 3));
        
        this.size = 4;
    }

    public int getSize() { return size; }

    public void setPiece(Piece p, int lig, int col) {
        if (lig < 0 || lig >= this.size || col < 0 || col >= this.size) {
            return;
        }
        grille[lig][col] = p;
    }

    public void removePiece(int lig, int col) {
        if (lig < 0 || lig >= this.size || col < 0 || col >= this.size) {
            return;
        }
        grille[lig][col] = null;
    }

    public Piece getPiece(int lig, int col) {
        if (lig < 0 || lig >= this.size || col < 0 || col >= this.size) {
            return null;
        }
        return grille[lig][col];
    }

    public TypePlateau getType() {
        return type;
    }

    public int getNbBlancs() {
        return nbBlancs;
    }

    public int getNbNoirs() {
        return nbNoirs;
    }

    public void decBlancs() {
        if (this.nbBlancs > 0)
            this.nbBlancs -= 1;
    }

    public void decNoirs() {
        if (this.nbNoirs > 0)
            this.nbNoirs -= 1;
    }

    public void incBlancs() { this.nbBlancs+=1; }

    public void incNoirs() { this.nbNoirs+=1; }

    public void appliquerCoup(Coup coup) {
        return;
    }

    public boolean estCoupValide(Coup coup) {
        if (coup.getTypeCoup() == Coup.TypeCoup.MOVE) {
            
        }
        
        return true;
    }

    public boolean paradoxe(Piece piece1, Piece piece2, int ecartx, int ecarty){
        if (piece1 != null && piece2 != null) {
            if (estPareilPion(piece1,piece2) && (piece1.getPosition().x == (piece2.getPosition().x + ecartx)) && (piece1.getPosition().y == (piece2.getPosition().y + ecarty))) {
                return true;
            }
        }
        return false;
    }

    public boolean estPareilPion(Piece piece1, Piece piece2){
        return piece1.getOwner() == piece2.getOwner();
    }

    public String plateauToString() {
        if (this.getType() == TypePlateau.PAST) {
            return "PAST";
        }
        else if (this.getType() == TypePlateau.PRESENT) {
            return "PRESENT";
        }
        else if (this.getType() == TypePlateau.FUTURE) {
            return "FUTURE";
        }
        else {
            System.out.println("Erreur de type de plateau");
            return null;
        }

    }
}   
