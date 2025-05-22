package Modele;

public class Coup {
    private Piece piece;
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


    public Coup(Piece piece, Plateau pltCourant, TypeCoup typeCoup) {
        this.piece = piece;
        this.pltCourant = pltCourant;  
        this.typeCoup = typeCoup;
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

    public static Coup stringToCoup(Piece piece, Plateau plateau, String coupstr) {
        //System.out.println("pendant: " + plateau.getType());
        if (coupstr != null){
            switch(coupstr){
                case "UP":
                    return new Coup(piece, plateau, TypeCoup.UP);
                case "DOWN":
                    return new Coup(piece, plateau, TypeCoup.DOWN);
                case "RIGHT":
                    return new Coup(piece, plateau, TypeCoup.RIGHT);
                case "LEFT":
                    return new Coup(piece, plateau, TypeCoup.LEFT);
                case "CLONE":
                    return new Coup(piece, plateau, TypeCoup.CLONE);
                case "JUMP":
                    return new Coup(piece, plateau, TypeCoup.JUMP);
                default:
                    System.out.println("BUG: erreur dans la méthode stringToCoup.");
                    break;
            }
        }
        return null;
    }

    public TypeCoup getCoup(Piece piece1, Piece piece2) {
        int dx = piece2.getPosition().x - piece1.getPosition().x;
        int dy = piece2.getPosition().y - piece1.getPosition().y;
        
        if (dx == -1 && dy == 0) return TypeCoup.UP;
        if (dx == 1 && dy == 0) return TypeCoup.DOWN;
        if (dx == 0 && dy == -1) return TypeCoup.LEFT;
        if (dx == 0 && dy == 1) return TypeCoup.RIGHT;
        
        // 如果两个棋子在不同的棋盘上，可能是JUMP或CLONE
        if (piece1.getPosition().equals(piece2.getPosition())) {
            // 相同位置，可能是跨时间的移动
            if (pltCourant.getType() == Plateau.TypePlateau.PAST || 
                pltCourant.getType() == Plateau.TypePlateau.PRESENT) {
                return TypeCoup.JUMP;
            } else {
                return TypeCoup.CLONE;
            }
        }
        
        // 默认情况
        return TypeCoup.LEFT;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;

        Coup coup = (Coup) obj;

        if (!piece.equals(coup.piece)) return false;
        if (!pltCourant.equals(coup.pltCourant)) return false;
        return typeCoup == coup.typeCoup;
    }
}
