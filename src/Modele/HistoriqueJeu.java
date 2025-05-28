package Modele;

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
    //redo stack past present future joueur1 joueur2
    Stack<Plateau> redoPast = new Stack<>();
    Stack<Plateau> redoPresent = new Stack<>();
    Stack<Plateau> redoFuture = new Stack<>();
    Stack<Joueur> redoJoueur1 = new Stack<>();
    Stack<Joueur> redoJoueur2 = new Stack<>();

    private boolean redo;

    public HistoriqueJeu() {
        this.past = new Stack<>();
        this.present = new Stack<>();
        this.future = new Stack<>();
        this.joueur1 = new Stack<>();
        this.joueur2 = new Stack<>();
        this.nbTours = -1;
        // redo stack
        this.redoPast = new Stack<>();
        this.redoPresent = new Stack<>();
        this.redoFuture = new Stack<>();
        this.redoJoueur1 = new Stack<>();
        this.redoJoueur2 = new Stack<>();

        this.redo = false;
    }

    public HistoriqueJeu(Plateau past, Plateau present, Plateau future, Joueur joueur1, Joueur joueur2) {
        this.joueur1.push(joueur1.copie());
        this.joueur2.push(joueur2.copie());
        this.past.push(past.copie(joueur1, joueur2));
        this.present.push(present.copie(joueur1, joueur2));
        this.future.push(future.copie(joueur1, joueur2));

        this.nbTours = 0;

        // redo stack
        this.redoPast = new Stack<>();
        this.redoPresent = new Stack<>();
        this.redoFuture = new Stack<>();
        this.redoJoueur1 = new Stack<>();
        this.redoJoueur2 = new Stack<>();

        this.redo = false;
    }

    public void add(Plateau past, Plateau present, Plateau future, Joueur joueur1, Joueur joueur2) {
        this.joueur1.push(joueur1.copie());
        this.joueur2.push(joueur2.copie());

        this.past.push(past.copie(joueur1, joueur2));
        this.present.push(present.copie(joueur1, joueur2));
        this.future.push(future.copie(joueur1, joueur2));
        this.nbTours++;
        this.redo = false; // reset redo state when a new move is added
        // redo stack
        this.redoPast = new Stack<>();
        this.redoPresent = new Stack<>();
        this.redoFuture = new Stack<>();
        this.redoJoueur1 = new Stack<>();
        this.redoJoueur2 = new Stack<>();
    }

    public void pop() {

        if (isRedoPossible()){
            //redo stack
            this.redoPast.push(this.past.peek().copie(this.joueur1.peek(), this.joueur2.peek()));
            this.redoPresent.push(this.present.peek().copie(this.joueur1.peek(), this.joueur2.peek()));
            this.redoFuture.push(this.future.peek().copie(this.joueur1.peek(), this.joueur2.peek()));
            this.redoJoueur1.push(this.joueur1.peek().copie());
            this.redoJoueur2.push(this.joueur2.peek().copie());
        }
        
        // pop the last element of the stack
        this.past.pop();
        this.present.pop();
        this.future.pop();
        this.joueur1.pop();
        this.joueur2.pop();
        this.nbTours--;
    }

    public void redo() {
        if (redoPast.isEmpty()) {
            this.redo = false;
        }
        if (!redoPast.isEmpty()) {
            this.past.push(this.redoPast.pop().copie(this.joueur1.peek(), this.joueur2.peek()));
            this.present.push(this.redoPresent.pop().copie(this.joueur1.peek(), this.joueur2.peek()));
            this.future.push(this.redoFuture.pop().copie(this.joueur1.peek(), this.joueur2.peek()));
            this.joueur1.push(this.redoJoueur1.pop().copie());
            this.joueur2.push(this.redoJoueur2.pop().copie());
            this.nbTours++;

            if (redoPast.isEmpty()) {
                this.redo = false;
            }
        }

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

    public boolean isRedoPossible(){
        return redo;
    }

    public void setRedo(boolean redo){
        this.redo = redo;
    }

}
