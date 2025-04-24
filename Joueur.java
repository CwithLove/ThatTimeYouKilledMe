import java.awt.Point;

public class Joueur {

    private String nom;
    private int id;
    private int nbClones;
    private Plateau.TypePlateau prochainPlateau;

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

    public void declone(){
		this.nbClones -= 1;
	}

    public void setProchainPlateau(Plateau.TypePlateau prochainPlateau) {
        this.prochainPlateau = prochainPlateau;
    }

	public Coup choisirCoup(Plateau plateau){
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (plateau.getPiece(i, j) != null && plateau.getPiece(i, j).getOwner() == this) {
                    return new Coup(plateau.getPiece(i, j), new Point(i, j), plateau, null);
                }
            }
        }
        return new Coup(null, null, plateau, null);
	}

	public Piece choisirPiece(Plateau plateau){
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (plateau.getPiece(i, j) != null && plateau.getPiece(i, j).getOwner() == this) {
                    return plateau.getPiece(i, j);
                }
            }
        }
		return null;
	}

}
