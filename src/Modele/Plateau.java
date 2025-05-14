package Modele;

import java.awt.Point;
import java.util.ArrayList;
public class Plateau {
    public enum TypePlateau {
        PAST,
        PRESENT,
        FUTURE
    }

    private TypePlateau type;
    private Piece[][] grille;
    private int nbBlancs;
    private int nbNoirs;
    private int size;

    public Plateau(TypePlateau type, Joueur joueur1, Joueur joueur2) {
        this.nbBlancs = 1;
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
        
        this.size = 4;
    }

    public int getSize() { return size; }

    public void setPiece(Piece p, int lig, int col) {
        if (lig < 0 || lig >= this.size || col < 0 || col >= this.size) {
            return;
        }
        grille[lig][col] = p;
    }

    public void removePiece(int lig, int col) {
        if (lig < 0 || lig >= this.size || col < 0 || col >= this.size) {
            return;
        }
        grille[lig][col] = null;
    }

    public Piece getPiece(int lig, int col) {
        if (lig < 0 || lig >= this.size || col < 0 || col >= this.size) {
            return null;
        }
        return grille[lig][col];
    }

    public ArrayList<Piece> getPieces() {
        ArrayList<Piece> pieces = new ArrayList<>();
        for (int lig = 0; lig < this.size; lig++) {
            for (int col = 0; col < this.size; col++) {
                if (grille[lig][col] != null) {
                    pieces.add(grille[lig][col]);
                }
            }
        }
        return pieces;
    }

    public TypePlateau getType() {
        return type;
    }

    public int getNbBlancs() {
        return nbBlancs;
    }

    public int getNbNoirs() {
        return nbNoirs;
    }

    public void decBlancs() {
        if (this.nbBlancs > 0)
            this.nbBlancs -= 1;
    }

    public void decNoirs() {
        if (this.nbNoirs > 0)
            this.nbNoirs -= 1;
    }

    public void incBlancs() { this.nbBlancs+=1; }

    public void incNoirs() { this.nbNoirs+=1; }

    // 重置计数器
    public void resetCounts() {
        this.nbBlancs = 0;
        this.nbNoirs = 0;
    }

    public void appliquerCoup(Coup coup) {
        return;
    }

    public boolean paradoxe(Piece piece1, Piece piece2, int ecartx, int ecarty){
        if (piece1 != null && piece2 != null) {
            if (estPareilPion(piece1,piece2) && (piece1.getPosition().x == (piece2.getPosition().x + ecartx)) && (piece1.getPosition().y == (piece2.getPosition().y + ecarty))) {
                return true;
            }
        }
        return false;
    }

    public boolean estPareilPion(Piece piece1, Piece piece2){
        return piece1.getOwner() == piece2.getOwner();
    }

    public String plateauToString() {
        if (this.getType() == TypePlateau.PAST) {
            return "PAST";
        }
        else if (this.getType() == TypePlateau.PRESENT) {
            return "PRESENT";
        }
        else if (this.getType() == TypePlateau.FUTURE) {
            return "FUTURE";
        }
        else {
            System.out.println("Erreur de type de plateau");
            return null;
        }

    }

    // 清除棋盘上所有棋子
    public void clearPieces() {
        for (int i = 0; i < this.getSize(); i++) {
            for (int j = 0; j < this.getSize(); j++) {
                if (this.getPiece(i, j) != null) {
                    this.removePiece(i, j);
                }
            }
        }
        this.nbBlancs = 0;
        this.nbNoirs = 0;
    }

    // 重新计算棋盘上的棋子数量
    public void updatePieceCount() {
        int blancs = 0;
        int noirs = 0;
        for (int i = 0; i < this.getSize(); i++) {
            for (int j = 0; j < this.getSize(); j++) {
                Piece p = this.getPiece(i, j);
                if (p != null) {
                    if (p.getOwner().getId() == 1) {
                        blancs++;
                    } else {
                        noirs++;
                    }
                }
            }
        }
        this.nbBlancs = blancs;
        this.nbNoirs = noirs;
        System.out.println("棋盘" + this.getType() + "：白棋=" + blancs + "，黑棋=" + noirs);
    }
}   