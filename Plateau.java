public class Plateau {
    public enum TypePlateau {
        PAST,
        PRESENT,
        FUTURE
    }

    private TypePlateau typePlateau;
    private Piece[][] grille; // tableau de pieces, null si pas de piece

    // Les methodes a completer
    // Constructor(int period) => tableau de pieces 4x4

    // getPiece(int x, int y) => retourne la piece a la position (x,y) 
    // setPiece(int x, int y, Piece piece) => place la piece a la position (x,y)
    // getPeriod() => retourne le type de plateau
    // supprimer(int x, int y) => supprime la piece a la position (x,y)
}   