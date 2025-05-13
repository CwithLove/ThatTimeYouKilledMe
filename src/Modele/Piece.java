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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Piece)) return false;
        Piece other = (Piece) obj;
        return this.owner.equals(other.owner) && this.position.equals(other.position);
    }
}