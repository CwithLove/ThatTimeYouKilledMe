
public class Jeu {
    //3 Plateau : past, present, future
    Plateau Present;
    Plateau Future;
    Plateau Past;

    public Jeu() {
        this.Present = new Plateau();
        this.Future = new Plateau();
        this.Past = new Plateau();
        if (Present == null || Future == null || Past == null) {
            System.out.println("Erreur d'initialisation du jeu");
            return;
        }
    }

    // getter
    public Plateau getPresent() {
        return Present;
    }
    public Plateau getFuture() {
        return Future;
    }
    public Plateau getPast() {
        return Past;
    }

    public boolean finJeu() {
        //a faire
        return false;
    }
}   
