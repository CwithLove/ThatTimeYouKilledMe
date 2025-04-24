import java.awt.Point;

public class Piece {
    private Joueur owner;
    private Point position;

    public Piece(Joueur owner, Point position) {
        this.owner = owner;
        this.position = position;
    }

    public Point getPosition() {
        return position;
    }

    public Joueur getOwner() {
        return owner;
    }

    public void setPosition(Point position) {
        this.position = position;
    }
}
