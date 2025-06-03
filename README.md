# That Time You Killed Me
Un jeu de stratégie basé sur Java Swing, prenant en charge les parties solo contre l'IA et les parties multijoueurs en réseau. Le jeu utilise un mécanisme de voyage dans le temps, où les joueurs doivent s'affronter stratégiquement sur un échiquier comprenant trois lignes temporelles : le passé, le présent et le futur.
## 🎮 Présentation du jeu
« That Time You Killed Me » est un jeu de stratégie innovant dans lequel les joueurs contrôlent des pions qui se déplacent et s'affrontent dans trois dimensions temporelles différentes (passé, présent et futur). Les mécanismes principaux du jeu sont les suivants :
- **Voyage dans le temps** : les pions peuvent se déplacer entre les trois lignes temporelles
- **Mécanisme de clonage** : les joueurs peuvent cloner leurs propres pions
- **Attaque sautée** : éliminer les pions adverses en sautant par-dessus eux
- **Choix stratégique** : à la fin de chaque tour, le joueur choisit la ligne temporelle suivante
## 🏗️ Structure du projet
```
src/
├── Modele/              # Modèle logique central du jeu
│   ├── Jeu.java         # Classe principale du jeu
│   ├── Joueur.java      # Classe du joueur
│   ├── Piece.java       # Classe des pions
│   ├── Plateau.java     # Classe du plateau
│   ├── Coup.java        # Classe de déplacement
│   └── HistoriqueJeu.java # Historique du jeu
├── SceneManager/        # Gestionnaire de scènes
│   ├── SceneManager.java # Gestionnaire de scènes
│   ├── Scene.java       # Classe de base des scènes
│   ├── MenuScene.java   # Scène du menu principal
│   ├── GameScene.java   # Scène du jeu
│   └── ...              # Autres classes de scène
├── Network/             # Module de communication réseau
│   ├── GameServerManager.java # Gestion du serveur de jeu
│   ├── GameClient.java  # Client du jeu
│   ├── IAminimax.java   # Implémentation de l'algorithme IA
│   └── ...              # Autres classes réseau
└── MainApp.java         # Entrée de l'application
```
## 🤖 Algorithme IA Minimax
### Présentation de l'algorithme
Le jeu implémente un adversaire IA basé sur l'algorithme Minimax, qui présente les caractéristiques suivantes :
#### Caractéristiques principales de l'algorithme
- **Élagage alpha-bêta** : optimise l'efficacité de la recherche et réduit l'évaluation inutile des nœuds
- **Recherche mémorisée** : utilise HashMap pour mettre en cache les valeurs d'évaluation des états de jeu déjà calculées
- **Plusieurs niveaux de difficulté** : prend en charge trois niveaux de difficulté : facile (EASY), moyen (MEDIUM) et difficile (HARD)
- **Profondeur de recherche maximale** : profondeur de recherche configurable (maximum de 5 niveaux par défaut)
#### Composants de la fonction d'évaluation
L'IA utilise plusieurs fonctions heuristiques pour évaluer l'état du jeu :
1. **Évaluation du matériel** (`hMateriel`) : calcule l'avantage en nombre de pièces
2. **Évaluation des clones** (`hClone`) : évalue la valeur de la capacité de clonage
3. **Contrôle de la position** (`hSurPlt`) : évalue la présence sur différentes lignes temporelles
4. **Contrôle du plateau** (`hControlePlateaux`) : évalue le contrôle de l'ensemble du plateau
5. **Pièces adjacentes** (`hPiecesAdjacentes`) : évalue la synergie entre les pièces
6. **Position en bordure** (`hBordPlateau`) : évalue la valeur stratégique des positions en bordure
7. **Contrôle des coins** (`hCoinPlateau`) : évalue l'importance des positions dans les coins
8. **Contrôle du centre** (`hCentrePlateau`) : évaluation de la valeur des positions centrales
#### Exemple d'implémentation de l'algorithme
```java
// Implémentation centrale de l'élagage Alpha-Beta
private int alphabeta(int profondeur, int alpha, int beta, boolean tourIA, Jeu clone) {
    
if (profondeur >= difficulte || clone.gameOver(clone.getJoueurCourant()) != 0) {
        return heuristique(clone, tourIA, false);
    }
if (tourIA) {
// Maximiser le nœud
int maxEval = Integer.MIN_VALUE;
        
for (IAFields tour : getTourPossible(clone.getJoueurCourant(), clone)) {
            // Appliquer le déplacement et évaluer récursivement
int eval = alphabeta(profondeur + 1, alpha, beta, false, newGameState);
maxEval = Math.max(maxEval, eval);
alpha = Math.max(alpha, eval);
            
if (beta <= alpha) break; // Élagage bêta
        }
return maxEval;
} else {
// Minimisation du nœud
// Implémentation similaire...
}
}
```
## 🎬 SceneManager Système de gestion des scènes
### Architecture du système
SceneManager utilise le modèle d'état pour gérer les différentes interfaces et états du jeu :
#### Composants principaux
1. **Classe SceneManager** :
   
- Gère la scène actuellement active
 - Traite les changements de scène et le cycle de vie
- Coordonne le rendu et le cycle de mise à jour
2. **Classe de base Scene** :
- Définit l'interface standard de la scène
- Fournit des méthodes d'initialisation, de mise à jour, de rendu et de nettoyage
3. **Classes de scène spécifiques** :
- `MenuScene` : interface du menu principal
- `GameScene` : interface principale du jeu
   
- `HostingScene` : salle de jeu multijoueur
   - `ResultScene` : interface des résultats du jeu
#### Cycle de vie de la scène
```java
public class SceneManager {
    private Scene currentScene;
public void setScene(Scene scene) {
    if (currentScene != null) {
    currentScene.dispose(); // Nettoie les ressources de l'ancienne scène
    }
        
currentScene = scene;
        currentScene.init();        // Initialisation de la nouvelle scène
}
public void update() {
if (currentScene != null) {
currentScene.update();   // Mise à jour de la logique de la scène
}
}
public void render(Graphics g, int width, int height) {
if (currentScene != null) {
            
currentScene.render(g, width, height); // Rendu de la scène
        }
    }
}
```
#### Exemple de changement de scène
```java
// Passage du menu principal à la scène du jeu
sceneManager.setScene(new GameScene(gameInstance, sceneManager));
// Passage du jeu à la scène des résultats
sceneManager.setScene(new ResultScene(winner, sceneManager));
```
## 🌐 Système de communication réseau
### Conception de l'architecture
Le jeu utilise une architecture client-serveur et prend en charge les combats multijoueurs en temps réel :
#### Côté serveur (GameServerManager)
**Fonctions principales** :
- **Gestion des connexions** : gestion des connexions et déconnexions des clients
- **Synchronisation de l'état du jeu** : maintien de l'état officiel du jeu et synchronisation avec tous les clients
- **Routage des messages** : traitement et distribution des messages des clients
- **Logique du jeu** : exécution des règles du jeu et des transitions d'état
**Caractéristiques clés** :
```java
public class GameServerManager {
    private final BlockingQueue<Message> incomingMessages = new LinkedBlockingQueue<>();
    private final Map<Integer, BlockingQueue<String>> outgoingQueues = new ConcurrentHashMap<>();
    
private final List<Integer> connectedClientIds = Collections.synchronizedList(new ArrayList<>());
// Démarrer le serveur
public void startServer() throws IOException {
serverSocket = new ServerSocket(currentPort);
isServerRunning = true;
createAndStartAcceptThread(« GameServerThread »);
}
// Boucle principale du moteur de jeu
    
private void runGameEngine() {
        while (isServerRunning) {
            Message msg = incomingMessages.take();
            processGameMessage(msg);
            sendGameStateToAllClients();
        }
    }
}
```
#### Client (GameClient)
**Fonctionnalités principales** :
- **Connexion au serveur** : établit et maintient la connexion avec le serveur
- **Envoi de messages** : envoie les actions des joueurs au serveur
- **Réception de l'état** : reçoit et traite les mises à jour de l'état du jeu provenant du serveur
- **Mise à jour de l'interface utilisateur** : met à jour l'interface locale en fonction de l'état du serveur
#### Protocole de communication
Le jeu utilise un protocole de messages basé sur du texte :
```java
// Énumération des types de messages
public enum Code {
    MOVE,           // Commande de déplacement
    ETAT,           // État du jeu
    
PLATEAU_CHOICE, // Choix du plateau
    GAME_OVER,      // Fin de la partie
    PLAYER_DISCONNECTED, // Déconnexion du joueur
REDOABLE        // Action réversible
}
// Exemple de format de message
« MOVE:1,2,3,4 »              // Déplacement du pion de (1,2) à (3,4)
« ETAT:gameStateString »      // État complet du jeu
« PLATEAU_CHOICE:FUTURE »     // Sélection de la ligne temporelle future
```
#### Sécurité et stabilité du réseau
1. **Gestion des connexions** :
- Mécanisme de reconnexion automatique
- Détection des pulsations
- Gestion élégante des déconnexions
2. **Synchronisation des données** :
- Statut d'autorité du serveur
- Vérification du statut du client
   
- Mécanisme de résolution des conflits
3. **Gestion des erreurs** :
- Récupération après une anomalie réseau
- Mécanisme de retransmission des messages
- Gestion des délais d'expiration
## 🚀 Instructions d'exécution
### Configuration requise
- Java 8 ou version supérieure
- Au moins 512 Mo de mémoire
- Connexion réseau (mode multijoueur)
### Démarrer le jeu
#### Méthode 1 : à l'aide du fichier JAR
```bash
java -jar TTYKM_GROUPE5.jar
```
#### Méthode 2 : compilation à partir du code source
```bash
# Compilation
javac -d out src/**/*.java
# Exécution
java -cp out MainApp
```
### Modes de jeu
1. **Mode solo** : affrontez l'IA et choisissez le niveau de difficulté
2. **Mode multijoueur** :
   
- Créer une salle : en tant qu'hôte, attendez que d'autres joueurs rejoignent la salle
   - Rejoindre une salle : connectez-vous à une salle créée par d'autres joueurs
## 🎯 Règles du jeu
### Règles de base
1. Au début, chaque joueur dispose de 4 pions sur sa ligne de départ
2. Les joueurs jouent à tour de rôle, chaque tour comprenant :
- Sélectionner un pion de son camp
- Effectuer au maximum deux déplacements/attaques
   
- Choisir la chronologie du tour suivant
### Types de déplacements
- **Déplacement normal** : se déplacer vers une case adjacente
- **Déplacement clone** : créer une copie du pion sur une case adjacente
- **Attaque sautée** : sauter par-dessus un pion adjacent et le détruire
### Conditions de victoire
- Détruire tous les pions de l'adversaire
- Empêcher l'adversaire d'effectuer un déplacement valide
## 🛠️ Caractéristiques techniques
### Modèle de conception
- **Modèle d'état** : gestion des scènes
- **Modèle observateur** : mise à jour de l'état du jeu
- **Modèle stratégique** : mise en œuvre de la difficulté de l'IA
- **Modèle singleton** : gestion des connexions réseau
### Optimisation des performances
- **Pool d'objets** : réduction de la pression sur le GC
- **Traitement asynchrone** : communication réseau non bloquante
- **Mécanisme de mise en cache** : mise en cache des résultats des calculs de l'IA
- **Gestion des ressources** : libération rapide des ressources inutilisées
## 👥 Équipe de développement
**The MACKYZ Protocol**
Authors:
- Members:
	- [@Chu Hoang Anh](https://github.com/CwithLove)
	- [@YilunJiang](https://github.com/ThearchyHelios)
	- [@Mathis](https://github.com/Smash10000)
	- [@Kevin](https://github.com/Kevin272727)
	- [@YuzhenNI](https://github.com/Clement-NI)
	- [@Anthony](https://github.com/anthonyMont)
