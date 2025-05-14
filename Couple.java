// Définition d'une classe générique Couple pour représenter un couple de valeurs
class Couple<T, U> {
    private T premier;
    private U second;

    public Couple(T premier, U second) {
        this.premier = premier;
        this.second = second;
    }

    public T getPremier() {
        return premier;
    }

    public U getSecond() {
        return second;
    }

    @Override
    public String toString() {
        return "(" + premier + ", " + second + ")";
    }
}