import java.awt.Point;
import java.util.Scanner;
import java.util.ArrayList;

public class Joueur {

    private String nom;
    private int id;
    private int nbClones;
    private Plateau.TypePlateau prochainPlateau;
    private Scanner scanner;
    public Joueur(String nom, int id, int nbClones, Plateau.TypePlateau prochainPlateau) {
        this.nom = nom;
        this.id = id;
        this.nbClones = nbClones;
        this.prochainPlateau = prochainPlateau;
    }

    public String getNom() {
        return nom;
    }

    public int getId() {
        return id;
    }

    public int getNbClones() {
        return nbClones;
    }

    public Plateau.TypePlateau getProchainPlateau() {
        return prochainPlateau;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean declone(){
        if (this.nbClones > 0) {
            this.nbClones -= 1;
            return true;
        }
        return false;
	}

    public void addClone(int n){
        if (n > 0) {
            this.nbClones += n;
        }
    }

    public void setProchainPlateau(Plateau.TypePlateau prochainPlateau) {
        this.prochainPlateau = prochainPlateau;
    }

    public boolean existePion(Plateau plateau){
        if (plateau.getType() != null){
            if ((this.nom == "Noir" && plateau.getNbNoirs() > 0) || (this.nom == "Blanc" && plateau.getNbBlancs() > 0)){
                return true;
            }
        }
        return false;
    }

    public ArrayList<Couple<Integer,Integer>> listePiecesJoueur(Plateau plateau){
        //Renvoie la liste des pions du joueur actuel, dans le plateau actuel
        ArrayList<Couple<Integer,Integer>> coordonnesPions = new ArrayList<>();
        Piece pion;
        if (plateau.getType() != null){
            for (int i = 0; i < plateau.getSize(); i++){
                for (int j = 0; j < plateau.getSize(); j++){
                    pion = plateau.getPiece(i,j);
                    if (pion != null && this == pion.getOwner()) {
                        coordonnesPions.add(new Couple<>(i, j));
                    }
                }
            }
        }
        return coordonnesPions;
    }

    public Joueur copie() {
        return new Joueur(this.nom, this.id, this.nbClones, this.prochainPlateau);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Joueur)) return false;
        Joueur other = (Joueur) obj;
        return id == other.id;
    }

}
