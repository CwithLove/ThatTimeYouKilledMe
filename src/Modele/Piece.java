package Modele;

import java.awt.Point;

public class Piece {
    private Joueur owner;
    private Point position;
    
    public Piece(Joueur owner, Point position) {
        this.owner = owner;
        this.position = position;
    }

    public Joueur getOwner() {
        return owner;
    }

    public Point getPosition() {
        return position;
    }

    public void setPosition(Point position) {
        this.position = position;
    }
}

