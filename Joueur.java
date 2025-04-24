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
        if (this.nbClones > 0) {
            this.nbClones -= 1;
        }
	}

    public void setProchainPlateau(Plateau.TypePlateau prochainPlateau) {
        this.prochainPlateau = prochainPlateau;
    }

	public Coup choisirCoup(Plateau plateau){
        java.util.List<Coup> coupsPossibles = new java.util.ArrayList<>();

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                Piece piece = plateau.getPiece(i, j);
                if (piece != null && piece.getOwner() == this) {
                    coupsPossibles.add(new Coup(piece, new Point(i, j), plateau, Coup.TypeCoup.MOVE));

                    // logique pour les futures
                }
            }
        }
        
        if (!coupsPossibles.isEmpty()) {
            int index = (int)(Math.random() * coupsPossibles.size());
            return coupsPossibles.get(index);
        }
        
        return new Coup(null, null, plateau, null);
	}

	public Piece choisirPiece(Plateau plateau){
        java.util.List<Piece> piecesDisponibles = new java.util.ArrayList<>();
        
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                Piece piece = plateau.getPiece(i, j);
                if (piece != null && piece.getOwner() == this) {
                    piecesDisponibles.add(piece);
                }
            }
        }
        
        if (!piecesDisponibles.isEmpty()) {
            int index = (int)(Math.random() * piecesDisponibles.size());
            return piecesDisponibles.get(index);
        }
        
        return null;
	}

}
