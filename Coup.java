import java.awt.Point;

public class Coup {
    private Piece piece;
    private Point direction;
    private Plateau pltCourant;
    private typeCoup typeCoup;

    public enum typeCoup {
        JUMP, // Travel Forward: Sauter a la future
        MOVE, // Move: Deplacer une piece dans le meme plateau
        CLONE // Clone: Travel Backward: Revenir dans le passe et se cloner
    }


    public Coup(Piece piece, Point direction, Plateau pltCourant, typeCoup typeCoup) {
        this.direction = direction;
        this.piece = piece;
        this.pltCourant = pltCourant;  
        this.typeCoup = typeCoup;
    }

    public Point getDirection() {
        return direction;
    }

    public Plateau getPltCourant() {
        return pltCourant;
    }

    public Piece getPiece() {
        return piece;
    }

    public typeCoup getTypeCoup() {
        return typeCoup;
    }
}
