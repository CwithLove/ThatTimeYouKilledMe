public class Plateau {
    Couleur[][] plateau; // grille de 4x4
    int nbNoir; // nombre de noir
    int nbBlanc; // nombre de blanc

    public enum Couleur{ // enum pour les couleurs
        NOIR, BLANC,VIDE // puis dautre si besoin 
    }

    public Plateau() {
        this.plateau = new Couleur[4][4];
        if (plateau == null) {
            System.out.println("Erreur d'initialisation du plateau");
            return;
        }
        this.nbNoir = 1;
        this.nbBlanc = 1;
        
        // Initialisation du plateau avec des cases vides
        // et deux pions de chaque couleur
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                plateau[i][j] = Couleur.VIDE;
            }
        }
        this.plateau[0][0] = Couleur.BLANC;
        this.plateau[3][3] = Couleur.NOIR;

    }

    //getter
    public Couleur getCase(int i, int j) {
        return plateau[i][j];
    }
    public int getNbNoir() {
        return nbNoir;
    }

    public int getNbBlanc() {
        return nbBlanc;
    }

    public boolean estCaseVide(int i, int j) {
        return plateau[i][j] == Couleur.VIDE;
    }

    public boolean estCaseNoir(int i, int j) {
        return plateau[i][j] == Couleur.NOIR;
    }

    public boolean estCaseBlanc(int i, int j) {
        return plateau[i][j] == Couleur.BLANC;
    }

    //setter
    public void setCase(int i, int j, Couleur couleur) {
        plateau[i][j] = couleur;
    }

}