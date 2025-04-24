/*
 * @Author: ThearchyHelios work@thearchyhelios.com
 * @Date: 2025-04-24 13:32:27
 * @LastEditors: ThearchyHelios work@thearchyhelios.com
 * @LastEditTime: 2025-04-24 14:00:58
 * @FilePath: /ThatTimeYouKilledMe/Joueur.java
 * @Description: 
 */
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


}
