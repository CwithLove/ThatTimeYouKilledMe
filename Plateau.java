import java.awt.Point;

public class Plateau {
    public enum TypePlateau {
        PAST,
        PRESENT,
        FUTURE
    }

    private TypePlateau type;
    private Piece[][] grille;
    private int nbBlacns;
    private int nbNoirs;

    public Plateau(TypePlateau type, Joueur joueur1, Joueur joueur2) {
        this.nbBlacns = 1;
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
    }

    public void setPiece(Piece p, int lig, int col) {
        grille[lig][col] = p;
    }

    public Piece getPiece(int lig, int col) {
        return grille[lig][col];
    }

    public TypePlateau getType() {
        return type;
    }

    public int getNbBlancs() {
        return nbBlacns;
    }

    public int getNbNoirs() {
        return nbNoirs;
    }

    public void decBlancs() {
        this.nbBlacns -= 1;
    }

    public void decNoirs() {
        this.nbNoirs -= 1;
    }

    public void incBlancs() { this.nbBlacns+=1; }

    public void incNoirs() { this.nbNoirs+=1; }

    public void appliquerCoup(Coup coup) {
        return;
    }

    public boolean estCoupValide(Coup coup) {
        if (coup.getTypeCoup() == Coup.TypeCoup.MOVE) {
            
        }
        
        return true;
    }
}   