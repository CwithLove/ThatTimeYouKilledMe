import java.awt.Point;

public class Plateau {
    public enum TypePlateau {
        PAST,
        PRESENT,
        FUTURE
    }

    private TypePlateau type;
    private Piece[][] grille; // tableau de pieces, null si pas de piece

    // Les methodes a completer
    // Constructor(int period) => tableau de pieces 4x4

    public Plateau(TypePlateau typePlateau) {
        this.type = typePlateau;
        this.grille = new Piece[4][4];
        for (int lig = 0; lig < 4; lig++) {
            for (int col = 0; col < 4; col++) {
                this.grille[lig][col] = null;
            }
        }
    }

    public void setPiece(Piece p, int x, int y) {
        grille[x][y] = p;
    }




    // getPiece(int x, int y) => retourne la piece a la position (x,y)
    // setPiece(int x, int y, Piece piece) => place la piece a la position (x,y)
    // getPeriod() => retourne le type de plateau
    // supprimer(int x, int y) => supprime la piece a la position (x,y)
}   