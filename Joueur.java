import java.awt.Point;
import java.util.Scanner;

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

	public Coup choisirCoup(Plateau plateau, Piece piece) {
        scanner = new Scanner(System.in);
        Coup.TypeCoup type = null;
        boolean validChoice = false;

        while (!validChoice) {
            System.out.println("Choose your action: JUMP, CLONE, MOVE");
            String choice = scanner.nextLine().toUpperCase();

            switch (choice) {
            case "JUMP":
                type = Coup.TypeCoup.JUMP;
                validChoice = true;
                break;
            case "CLONE":
                type = Coup.TypeCoup.CLONE;
                validChoice = true;
                break;
            case "MOVE":
                type = Coup.TypeCoup.MOVE;
                validChoice = true;
                break;
            default:
                System.out.println("Invalid choice. Please try again.");
            }
        }
        
        Point dir = null;
        switch (type) {
            case JUMP:
                break;
            case CLONE:
                break;
            case MOVE:
                boolean validDirection = false;

                while (!validDirection) {
                    try {
                        System.out.println("Choose the direction: UP, DOWN, LEFT, RIGHT");
                        String direction = scanner.nextLine().toUpperCase();

                        switch (direction) {
                            case "UP":
                                if (piece.getPosition().x > 0) {
                                    dir = new Point(-1, 0);
                                    validDirection = true;
                                }
                                break;
                            case "DOWN":
                                if (piece.getPosition().x < plateau.getSize()-1) {
                                    dir = new Point(1, 0);
                                    validDirection = true;
                                }
                                break;
                            case "LEFT":
                                if (piece.getPosition().y > 0) {
                                    dir = new Point(0, -1);
                                    validDirection = true;
                                }
                                break;
                            case "RIGHT":
                                if (piece.getPosition().y < plateau.getSize()-1) {
                                    dir = new Point(0, 1);
                                    validDirection = true;
                                }
                                break;
                            default:
                                throw new IllegalArgumentException("Invalid direction. Please try again.");
                        }
                        if (!validDirection) {
                            System.out.println("Direction invalide, veuillez réessayer :");
                        }
                    } catch (IllegalArgumentException e) {
                        System.out.println(e.getMessage());
                    }
                }

        }


        return new Coup(piece, dir, plateau, type);
	}

	public Piece choisirPiece(Plateau plateau){
        
        
        return null;
	}

}
