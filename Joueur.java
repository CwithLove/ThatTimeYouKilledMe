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
		// choisir un coup (jump, move, clone)
		// 这里应该由子类实现具体逻辑
		return null;
	}

	public Piece choisirPiece(Plateau plateau){
		// choisir une piece
		// 这里应该由子类实现具体逻辑
		return null;
	}

}
