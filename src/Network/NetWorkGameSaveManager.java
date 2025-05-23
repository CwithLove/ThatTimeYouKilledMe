package Network;

import Modele.Jeu;
import Modele.Joueur;
import Modele.Piece;
import Modele.Plateau;

import java.awt.Point;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Network Game Save Manager
 * Responsible for saving and loading client game states without external dependencies
 */
public class NetWorkGameSaveManager {

    private static final String SAVE_DIRECTORY = "saves";
    private static final String SAVE_EXTENSION = ".save";
    private static final String BACKUP_EXTENSION = ".bak";
    private static final String METADATA_EXTENSION = ".meta";

    static {
        // Ensure save directory exists
        try {
            System.out.println("NetWorkGameSaveManager : ");
            Files.createDirectories(Paths.get(SAVE_DIRECTORY));
        } catch (IOException e) {
            System.err.println("NetworkGameSaveManager: Unable to create save directory: " + e.getMessage());
        }
    }

    /**
     * Save client game state
     * @param client GameClient instance
     * @param saveName Save file name
     * @return true if save successful
     */
    public static boolean saveClientState(GameClient client, String saveName) {
        if (client == null || saveName == null || saveName.trim().isEmpty()) {
            System.err.println("NetworkGameSaveManager: Invalid client or save name");
            return false;
        }

        try {
            String sanitizedName = sanitizeFileName(saveName.trim());
            String saveFileName = sanitizedName + SAVE_EXTENSION;
            String metaFileName = sanitizedName + METADATA_EXTENSION;

            Path saveFilePath = Paths.get(SAVE_DIRECTORY, saveFileName);
            Path metaFilePath = Paths.get(SAVE_DIRECTORY, metaFileName);

            // Create backup if files already exist
            if (Files.exists(saveFilePath)) {
                createBackup(saveFilePath);
            }
            if (Files.exists(metaFilePath)) {
                createBackup(metaFilePath);
            }

            // Save game state data
            Jeu gameInstance = client.getGameInstance();
            if (gameInstance == null) {
                System.err.println("NetworkGameSaveManager: Game instance is null");
                return false;
            }

            // Save game data using custom format
            boolean saveSuccess = saveGameData(gameInstance, saveFilePath);
            if (!saveSuccess) {
                return false;
            }

            // Save metadata
            boolean metaSuccess = saveMetadata(saveName, client.getMyPlayerId(),
                                             client.isConnected(), metaFilePath);
            if (!metaSuccess) {
                // If metadata save fails, cleanup game data file
                try {
                    Files.deleteIfExists(saveFilePath);
                } catch (IOException e) {
                    System.err.println("Error cleaning up failed save file: " + e.getMessage());
                }
                return false;
            }

            System.out.println("NetworkGameSaveManager: Game state saved to: " + saveFileName);
            return true;

        } catch (Exception e) {
            System.err.println("NetworkGameSaveManager: Save failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Load client game state
     * @param client GameClient instance
     * @param saveName Save file name
     * @return true if load successful
     */
    public static boolean loadClientState(GameClient client, String saveName) {
        if (client == null || saveName == null || saveName.trim().isEmpty()) {
            System.err.println("NetworkGameSaveManager: Invalid client or save name");
            return false;
        }

        try {
            String sanitizedName = sanitizeFileName(saveName.trim());
            String saveFileName = sanitizedName + SAVE_EXTENSION;
            String metaFileName = sanitizedName + METADATA_EXTENSION;

            Path saveFilePath = Paths.get(SAVE_DIRECTORY, saveFileName);
            Path metaFilePath = Paths.get(SAVE_DIRECTORY, metaFileName);

            if (!Files.exists(saveFilePath)) {
                System.err.println("NetworkGameSaveManager: Save file does not exist: " + saveFileName);
                return false;
            }

            // Load metadata
            SaveMetadata metadata = loadMetadata(metaFilePath);
            if (metadata == null) {
                System.err.println("NetworkGameSaveManager: Unable to read save metadata");
                return false;
            }

            // Load game data
            Jeu restoredGame = loadGameData(saveFilePath);
            if (restoredGame == null) {
                System.err.println("NetworkGameSaveManager: Unable to restore game state");
                return false;
            }

            // Update client game instance
            updateClientGameInstance(client, restoredGame);

            System.out.println("NetworkGameSaveManager: Game state loaded from save: " + saveFileName);
            System.out.println("Save time: " + metadata.saveTime);
            System.out.println("Player ID: " + metadata.playerId);

            return true;

        } catch (Exception e) {
            System.err.println("NetworkGameSaveManager: Load failed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get list of all available saves
     * @return List of save information
     */
    public static List<SaveInfo> listAvailableSaves() {
        List<SaveInfo> saves = new ArrayList<>();

        try {
            Path saveDir = Paths.get(SAVE_DIRECTORY);
            if (!Files.exists(saveDir)) {
                return saves;
            }

            Files.list(saveDir)
                    .filter(path -> path.toString().endsWith(SAVE_EXTENSION))
                    .forEach(path -> {
                        try {
                            String baseName = path.getFileName().toString()
                                    .replace(SAVE_EXTENSION, "");
                            Path metaPath = saveDir.resolve(baseName + METADATA_EXTENSION);

                            SaveMetadata metadata = loadMetadata(metaPath);
                            if (metadata != null) {
                                SaveInfo info = new SaveInfo();
                                info.fileName = path.getFileName().toString();
                                info.saveName = metadata.saveName;
                                info.saveTime = metadata.saveTime;
                                info.playerId = metadata.playerId;
                                info.fileSize = Files.size(path);
                                saves.add(info);
                            }
                        } catch (Exception e) {
                            System.err.println("NetworkGameSaveManager: Unable to read save info: " +
                                             path.getFileName());
                        }
                    });

        } catch (IOException e) {
            System.err.println("NetworkGameSaveManager: Unable to list save files: " + e.getMessage());
        }

        // Sort by save time (newest first)
        saves.sort((a, b) -> b.saveTime.compareTo(a.saveTime));

        return saves;
    }

    /**
     * Delete specified save
     * @param saveName Save name to delete
     * @return true if deletion successful
     */
    public static boolean deleteSave(String saveName) {
        if (saveName == null || saveName.trim().isEmpty()) {
            return false;
        }

        try {
            String sanitizedName = sanitizeFileName(saveName.trim());
            String saveFileName = sanitizedName + SAVE_EXTENSION;
            String metaFileName = sanitizedName + METADATA_EXTENSION;

            Path saveFilePath = Paths.get(SAVE_DIRECTORY, saveFileName);
            Path metaFilePath = Paths.get(SAVE_DIRECTORY, metaFileName);

            boolean saveDeleted = Files.deleteIfExists(saveFilePath);
            boolean metaDeleted = Files.deleteIfExists(metaFilePath);

            if (saveDeleted || metaDeleted) {
                System.out.println("NetworkGameSaveManager: Save deleted: " + saveFileName);

                // Also delete backup files if they exist
                Path saveBackupPath = Paths.get(SAVE_DIRECTORY, sanitizedName + SAVE_EXTENSION + BACKUP_EXTENSION);
                Path metaBackupPath = Paths.get(SAVE_DIRECTORY, sanitizedName + METADATA_EXTENSION + BACKUP_EXTENSION);
                Files.deleteIfExists(saveBackupPath);
                Files.deleteIfExists(metaBackupPath);

                return true;
            }
        } catch (IOException e) {
            System.err.println("NetworkGameSaveManager: Delete save failed: " + e.getMessage());
        }

        return false;
    }

    // ==================== Private Helper Methods ====================

    /**
     * Save game data to file
     */
    private static boolean saveGameData(Jeu jeu, Path filePath) {
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filePath.toFile())))) {

            // Write file header and version info
            dos.writeInt(0x47414D45); // "GAME" magic number
            dos.writeInt(1); // Version number

            // Save basic state
            dos.writeInt(jeu.getEtapeCoup());
            dos.writeInt(jeu.getGameState());

            // Save current player ID
            dos.writeInt(jeu.getJoueurCourant() != null ? jeu.getJoueurCourant().getId() : 0);

            // Save player information
            saveJoueurData(dos, jeu.getJoueur1());
            saveJoueurData(dos, jeu.getJoueur2());

            // Save board states
            savePlateauData(dos, jeu.getPast());
            savePlateauData(dos, jeu.getPresent());
            savePlateauData(dos, jeu.getFuture());

            // Save current selected piece information
            if (jeu.getPieceCourante() != null) {
                dos.writeBoolean(true); // Has selected piece
                dos.writeInt(jeu.getPieceCourante().getOwner().getId());
                dos.writeInt(jeu.getPieceCourante().getPosition().x);
                dos.writeInt(jeu.getPieceCourante().getPosition().y);
                dos.writeInt(jeu.getPlateauCourant().getType().ordinal());
            } else {
                dos.writeBoolean(false); // No selected piece
            }

            return true;

        } catch (IOException e) {
            System.err.println("NetworkGameSaveManager: Save game data failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load game data from file
     */
    private static Jeu loadGameData(Path filePath) {
        try (DataInputStream dis = new DataInputStream(
                new BufferedInputStream(new FileInputStream(filePath.toFile())))) {

            // Verify file header
            int magic = dis.readInt();
            if (magic != 0x47414D45) {
                System.err.println("NetworkGameSaveManager: Invalid save file format");
                return null;
            }

            int version = dis.readInt();
            if (version != 1) {
                System.err.println("NetworkGameSaveManager: Unsupported save version: " + version);
                return null;
            }

            // Create new game instance
            Jeu jeu = new Jeu();

            // Read basic state
            jeu.setEtapeCoup(dis.readInt());
            int gameState = dis.readInt(); // Currently unused as no public setter

            // Read current player ID
            int currentPlayerId = dis.readInt();

            // Read player information
            loadJoueurData(dis, jeu.getJoueur1());
            loadJoueurData(dis, jeu.getJoueur2());

            // Set current player
            if (currentPlayerId == 1) {
                jeu.setJoueurCourant(jeu.getJoueur1());
            } else if (currentPlayerId == 2) {
                jeu.setJoueurCourant(jeu.getJoueur2());
            }

            // Read board states
            loadPlateauData(dis, jeu.getPast(), jeu.getJoueur1(), jeu.getJoueur2());
            loadPlateauData(dis, jeu.getPresent(), jeu.getJoueur1(), jeu.getJoueur2());
            loadPlateauData(dis, jeu.getFuture(), jeu.getJoueur1(), jeu.getJoueur2());

            // Read current selected piece
            boolean hasPieceCourante = dis.readBoolean();
            if (hasPieceCourante) {
                int ownerId = dis.readInt();
                int x = dis.readInt();
                int y = dis.readInt();
                int plateauTypeOrdinal = dis.readInt();

                Joueur owner = (ownerId == 1) ? jeu.getJoueur1() : jeu.getJoueur2();
                Plateau.TypePlateau plateauType = Plateau.TypePlateau.values()[plateauTypeOrdinal];

                // Set current board
                jeu.setPlateauCourant(plateauType);

                // Get piece from corresponding board
                Plateau plateau = jeu.getPlateauByType(plateauType);
                Piece piece = plateau.getPiece(x, y);
                if (piece != null && piece.getOwner().getId() == ownerId) {
                    jeu.setPieceCourante(piece);
                }
            }

            return jeu;

        } catch (IOException e) {
            System.err.println("NetworkGameSaveManager: Load game data failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Save player data
     */
    private static void saveJoueurData(DataOutputStream dos, Joueur joueur) throws IOException {
        dos.writeUTF(joueur.getNom());
        dos.writeInt(joueur.getId());
        dos.writeInt(joueur.getNbClones());
        dos.writeInt(joueur.getProchainPlateau().ordinal());
    }

    /**
     * Load player data
     */
    private static void loadJoueurData(DataInputStream dis, Joueur joueur) throws IOException {
        String nom = dis.readUTF();
        int id = dis.readInt();
        int nbClones = dis.readInt();
        int prochainPlateauOrdinal = dis.readInt();

        joueur.setNom(nom);
        joueur.setId(id);
        joueur.setNbClones(nbClones);
        joueur.setProchainPlateau(Plateau.TypePlateau.values()[prochainPlateauOrdinal]);
    }

    /**
     * Save board data
     */
    private static void savePlateauData(DataOutputStream dos, Plateau plateau) throws IOException {
        dos.writeInt(plateau.getType().ordinal());
        dos.writeInt(plateau.getNbBlancs());
        dos.writeInt(plateau.getNbNoirs());
        dos.writeInt(plateau.getSize());

        // Save piece positions (could use bitmap for more compact representation, but using individual saves for simplicity)
        for (int i = 0; i < plateau.getSize(); i++) {
            for (int j = 0; j < plateau.getSize(); j++) {
                Piece piece = plateau.getPiece(i, j);
                if (piece != null) {
                    dos.writeInt(piece.getOwner().getId());
                } else {
                    dos.writeInt(0); // 0 indicates empty position
                }
            }
        }
    }

    /**
     * Load board data
     */
    private static void loadPlateauData(DataInputStream dis, Plateau plateau,
                                      Joueur joueur1, Joueur joueur2) throws IOException {
        int typeOrdinal = dis.readInt(); // Type verification (optional)
        int nbBlancs = dis.readInt(); // Can be used for verification
        int nbNoirs = dis.readInt(); // Can be used for verification
        int size = dis.readInt();

        // Clear board
        plateau.clearPieces();

        // Read piece positions
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int ownerId = dis.readInt();
                if (ownerId != 0) {
                    Joueur owner = (ownerId == 1) ? joueur1 : joueur2;
                    Piece piece = new Piece(owner, new Point(i, j));
                    plateau.setPiece(piece, i, j);
                }
            }
        }

        // Update piece count
        plateau.updatePieceCount();
    }

    /**
     * Save metadata
     */
    private static boolean saveMetadata(String saveName, int playerId, boolean isConnected, Path filePath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath.toFile()))) {
            writer.println("saveName=" + saveName);
            writer.println("saveTime=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            writer.println("playerId=" + playerId);
            writer.println("isConnected=" + isConnected);
            return true;
        } catch (IOException e) {
            System.err.println("NetworkGameSaveManager: Save metadata failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Load metadata
     */
    private static SaveMetadata loadMetadata(Path filePath) {
        if (!Files.exists(filePath)) {
            return null;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath.toFile()))) {
            SaveMetadata metadata = new SaveMetadata();
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();

                    switch (key) {
                        case "saveName":
                            metadata.saveName = value;
                            break;
                        case "saveTime":
                            metadata.saveTime = value;
                            break;
                        case "playerId":
                            metadata.playerId = Integer.parseInt(value);
                            break;
                        case "isConnected":
                            metadata.isConnected = Boolean.parseBoolean(value);
                            break;
                    }
                }
            }

            return metadata;

        } catch (IOException | NumberFormatException e) {
            System.err.println("NetworkGameSaveManager: Load metadata failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Update client game instance
     */
    private static void updateClientGameInstance(GameClient client, Jeu newGameInstance) {
        try {
            // First try to call public method (needs to be added to GameClient)
            java.lang.reflect.Method setGameInstanceMethod =
                GameClient.class.getMethod("setGameInstance", Jeu.class);
            setGameInstanceMethod.invoke(client, newGameInstance);
        } catch (NoSuchMethodException e) {
            // If no public method, use reflection to access private field
            try {
                java.lang.reflect.Field gameField = GameClient.class.getDeclaredField("gameInstance");
                gameField.setAccessible(true);
                gameField.set(client, newGameInstance);
            } catch (Exception ex) {
                System.err.println("NetworkGameSaveManager: Unable to update client game instance: " + ex.getMessage());
            }
        } catch (Exception e) {
            System.err.println("NetworkGameSaveManager: Failed to call setGameInstance method: " + e.getMessage());
        }
    }

    private static String sanitizeFileName(String fileName) {
        // Remove or replace unsafe filename characters
        return fileName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private static void createBackup(Path originalFile) {
        try {
            String backupFileName = originalFile.getFileName().toString() + BACKUP_EXTENSION;
            Path backupPath = originalFile.getParent().resolve(backupFileName);
            Files.copy(originalFile, backupPath,
                      java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("NetworkGameSaveManager: Create backup failed: " + e.getMessage());
        }
    }

    // ==================== Internal Data Classes ====================

    /**
     * Save metadata
     */
    private static class SaveMetadata {
        String saveName;
        String saveTime;
        int playerId;
        boolean isConnected;
    }

    /**
     * Save information
     */
    public static class SaveInfo {
        public String fileName;
        public String saveName;
        public String saveTime;
        public int playerId;
        public long fileSize;

        @Override
        public String toString() {
            return String.format("%s (Player%d, %s, %.1fKB)",
                               saveName, playerId, saveTime, fileSize / 1024.0);
        }
    }
}