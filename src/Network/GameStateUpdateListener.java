package Network;

import Modele.Jeu;

public interface GameStateUpdateListener {
    void onGameStateUpdate(Jeu newGameState);
    void onGameMessage(String messageType, String messageContent);
}