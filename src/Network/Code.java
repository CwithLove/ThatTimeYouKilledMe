package Network;

public enum Code {
    ETAT,           // Mise à jour de l'état du jeu
    PIECE,          // Les coups possibles a partir de la pièce choisie
    DESELECT,       // Le joueur deselectionne une pièce

    ACTION,        // Le serveur attend l'action choisie par le joueur
    DIRECTION,       // Le serveur attend une direction si MOVE a été sélectionné
    PLATEAU,        // Le serveur attend que le joueur choisisse le plateau
    ADVERSAIRE,     // C'est le tour de l'adversaire
    COUP,           // Le coup a été joué
    GAGNE,          // Le joueur a gagné
    PERDU,          // Le joueur a perdu
    SERVER_SHUTDOWN  // Le serveur est en cours de fermeture
}
