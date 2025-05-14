package Modele;

import java.awt.Point;
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
        RIGHT, // Move Right
        MOVE  // 通用移动操作（用于客户端与服务器之间的通信）
    }


    public Coup(Piece piece, Plateau pltCourant, TypeCoup typeCoup) {
        this.piece = piece;
        this.pltCourant = pltCourant;  
        this.typeCoup = typeCoup;
    }

    // 添加接收四个参数的构造函数（支持direction参数）
    public Coup(Piece piece, Point direction, Plateau pltCourant, TypeCoup typeCoup) {
        this.piece = piece;
        this.pltCourant = pltCourant;  
        this.typeCoup = typeCoup;
        // direction参数在这里被忽略，因为TypeCoup已经包含了方向信息
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
        return null;
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