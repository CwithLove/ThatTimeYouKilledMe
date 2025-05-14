import java.util.Stack;

public class HistoriqueJeu {

    // pile des etats du jeu contenant les plateaux et les reserves et prochain
    // plateau des joueurs

    Stack<Plateau> past = new Stack<>();
    Stack<Plateau> present = new Stack<>();
    Stack<Plateau> future = new Stack<>();
    Stack<Joueur> joueur1 = new Stack<>();
    Stack<Joueur> joueur2 = new Stack<>();
    private int nbTours;

    public HistoriqueJeu() {
            this.past = new Stack<>();
            this.present = new Stack<>();
            this.future = new Stack<>();
            this.joueur1 = new Stack<>();
            this.joueur2 = new Stack<>();
            this.nbTours = -1;
        }

    public HistoriqueJeu(Plateau past, Plateau present, Plateau future, Joueur joueur1, Joueur joueur2) {
            this.joueur1.push(joueur1.copie());
            this.joueur2.push(joueur2.copie());
            this.past.push(past.copie(getJoueur1(), getJoueur2()));
            this.present.push(present.copie(getJoueur1(), getJoueur2()));
            this.future.push(future.copie(getJoueur1(), getJoueur2()));
            
            
            this.nbTours = 1;
        }

    public void add(Plateau past, Plateau present, Plateau future, Joueur joueur1, Joueur joueur2) {
        this.joueur1.push(joueur1.copie());
        this.joueur2.push(joueur2.copie());

        this.past.push(past.copie(getJoueur1(), getJoueur2()));
        this.present.push(present.copie(getJoueur1(), getJoueur2()));
        this.future.push(future.copie(getJoueur1(), getJoueur2()));      
        this.nbTours++;
    }

    public void pop() {
        this.past.pop();
        this.present.pop();
        this.future.pop();
        this.joueur1.pop();
        this.joueur2.pop();
        this.nbTours--;
    }

    public Plateau getPast(Joueur joueur1, Joueur joueur2) {
        return past.peek().copie(joueur1, joueur2);
    }

    public Plateau getPresent(Joueur joueur1, Joueur joueur2) {
        return present.peek().copie(joueur1, joueur2);
    }

    public Plateau getFuture(Joueur joueur1, Joueur joueur2) {
        return future.peek().copie(joueur1, joueur2);
    }

    public Joueur getJoueur1() {
        return joueur1.peek().copie();
    }

    public Joueur getJoueur2() {
        return joueur2.peek().copie();
    }

    public int getNbTours() {
        return nbTours;
    }

}

