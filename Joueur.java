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

	public Coup choisirCoup(Plateau plateau, Piece piece, Plateau past, Plateau present, Plateau future) {
        if ((this.nom == "Blanc" && plateau.getNbBlancs() == 0) || (this.nom == "Noir" && plateau.getNbNoirs() == 0)){
            return null;
        }
        scanner = new Scanner(System.in);
        Coup.TypeCoup type = null;
        boolean validChoice = false;

        while (!validChoice) {
            System.out.println("Choose your action: JUMP, CLONE, MOVE");
            String choice = scanner.nextLine().toUpperCase();

            switch (choice) {
            case "JUMP":
                switch(plateau.getType()) {
                    case FUTURE:
                        System.out.println("Vous ne pouvez pas jump dans le futur");
                        break;
                    case PAST:
                        Piece targetPiece = present.getPiece((int) piece.getPosition().getX(), (int) piece.getPosition().getY());
                        if (targetPiece == null) {
                            type = Coup.TypeCoup.JUMP;
                            validChoice = true;
                        }
                        else {
                            System.out.println("Vous ne pouvez pas vous jump sur quelque chose");
                        }
                        break;
                    case PRESENT:
                       targetPiece = future.getPiece((int) piece.getPosition().getX(), (int) piece.getPosition().getY());
                        if (targetPiece == null) {
                            type = Coup.TypeCoup.JUMP;
                            validChoice = true;
                        }
                        else {
                            System.out.println("Vous ne pouvez pas vous jump sur quelque chose");
                        }
                        break;
                    default:
                        System.out.println("Erreur dans choisir coup");
                        break;
                }
                break;
            case "CLONE":
                if (this.nbClones <= 0){
                    System.out.println("Vous n'avez plus de pions dans la réserve.");
                }
                switch(plateau.getType()) {
                    case PAST:
                        System.out.println("Vous ne pouvez pas vous cloner dans le passé");
                        break;
                    case PRESENT:
                        Piece targetPiece = past.getPiece((int) piece.getPosition().getX(), (int) piece.getPosition().getY());
                        if (targetPiece == null) {
                            type = Coup.TypeCoup.CLONE;
                            validChoice = true;
                        }
                        else {
                            System.out.println("Vous ne pouvez pas vous cloner sur quelque chose");
                        }
                        break;
                    case FUTURE:
                        targetPiece = present.getPiece((int) piece.getPosition().getX(), (int) piece.getPosition().getY());
                        if (targetPiece == null) {
                            type = Coup.TypeCoup.CLONE;
                            validChoice = true;
                        }
                        else {
                            System.out.println("Vous ne pouvez pas vous cloner sur quelque chose");
                        }
                        break;
                    default:
                        System.out.println("Erreur dans choisir coup");
                        break;
                }
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
                            case "UP": {
                                int newX = piece.getPosition().x - 1;
                                int y = piece.getPosition().y;
                                if (newX >= 0) {
                                    Piece sidePiece = plateau.getPiece(newX, y);
                                    if (sidePiece == null || !sidePiece.getOwner().equals(piece.getOwner())) {
                                        dir = new Point(-1, 0);
                                        validDirection = true;
                                    } else {
                                        System.out.println("On ne peut pas rentrer dans son propre pion");
                                    }
                                }
                                break;
                            }
                            case "DOWN": {
                                int newX = piece.getPosition().x + 1;
                                int y = piece.getPosition().y;
                                if (newX < plateau.getSize()) {
                                    Piece sidePiece = plateau.getPiece(newX, y);
                                    if (sidePiece == null || !sidePiece.getOwner().equals(piece.getOwner())) {
                                        dir = new Point(1, 0);
                                        validDirection = true;
                                    } else {
                                        System.out.println("On ne peut pas rentrer dans son propre pion");
                                    }
                                }
                                break;
                            }
                            case "LEFT": {
                                int x = piece.getPosition().x;
                                int newY = piece.getPosition().y - 1;
                                if (newY >= 0) {
                                    Piece sidePiece = plateau.getPiece(x, newY);
                                    if (sidePiece == null || !sidePiece.getOwner().equals(piece.getOwner())) {
                                        dir = new Point(0, -1);
                                        validDirection = true;
                                    } else {
                                        System.out.println("On ne peut pas rentrer dans son propre pion");
                                    }
                                }
                                break;
                            }
                            case "RIGHT": {
                                int x = piece.getPosition().x;
                                int newY = piece.getPosition().y + 1;
                                if (newY < plateau.getSize()) {
                                    Piece sidePiece = plateau.getPiece(x, newY);
                                    if (sidePiece == null || !sidePiece.getOwner().equals(piece.getOwner())) {
                                        dir = new Point(0, 1);
                                        validDirection = true;
                                    } else {
                                        System.out.println("On ne peut pas rentrer dans son propre pion");
                                    }
                                }
                                break;
                            }
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
                break; // <= important : termine le case MOVE correctement
        }



        return new Coup(piece, dir, plateau, type);
	}

	public Piece choisirPiece(Plateau plateau){
        
        
        return null;
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
}
