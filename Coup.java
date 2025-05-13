import java.awt.Point;

public class Coup {
    private Piece piece;
    private Point direction;
    private Plateau pltCourant;
    private TypeCoup typeCoup;

    public enum TypeCoup {
        JUMP, // Travel Forward: Sauter a la future
        CLONE, // Clone: Travel Backward: Revenir dans le passe et se cloner
        UP, // Move Up
        DOWN, // Move Down
        LEFT, // Move Left
        RIGHT // Move Right
    }


    public Coup(Piece piece, Point direction, Plateau pltCourant, TypeCoup typeCoup) {
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

    public TypeCoup getTypeCoup() {
        return typeCoup;
    }

    public Coup stringToCoup(Piece piece, Plateau plateau, String coupstr) {
        //System.out.println("pendant: " + plateau.getType());
        switch(coupstr){
            case "UP":
                return new Coup(piece,new Point(-1, 0),plateau,TypeCoup.UP);
            case "DOWN":
                return new Coup(piece,new Point(1, 0),plateau,TypeCoup.DOWN);
            case "RIGHT":
                return new Coup(piece,new Point(0, 1),plateau,TypeCoup.RIGHT);
            case "LEFT":
                return new Coup(piece,new Point(0,-1),plateau,TypeCoup.LEFT);
            case "CLONE":
                return new Coup(piece,new Point(0,0),plateau,TypeCoup.CLONE);
            case "JUMP":
                return new Coup(piece,new Point(0,0),plateau,TypeCoup.JUMP);
            default:
                System.out.println("BUG: erreur dans la méthode stringToCoup.");
                break;
        }
        return null;
    }
}
