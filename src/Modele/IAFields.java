package Modele;

// Définition d'une classe générique Couple pour représenter un couple de valeurs
public class IAFields <T, U, V, W> {
	private T positionPiece;
	private U action1;
	private V action2;
	private W nouveauPlateau;

	public IAFields(T positionPiece, U action1, V action2, W nouveauPlateau) {
			this.positionPiece = positionPiece;
			this.action1 = action1;
			this.action2 = action2;
			this.nouveauPlateau = nouveauPlateau;
	}

	public IAFields(IAFields<Couple<Integer,Integer>,String,String,String> t) {
	}

	public T getPremier() {
			return positionPiece;
	}

	public U getSecond() {
			return action1;
	}

	public V getTroisieme() {
			return action2;
	}

	public W getQuatrieme() {
			return nouveauPlateau;
	}

	@Override
	public String toString() {
			return "(" + positionPiece + ", " + action1 + ", " + action2 +  ", "+ nouveauPlateau + ")";
	}
}