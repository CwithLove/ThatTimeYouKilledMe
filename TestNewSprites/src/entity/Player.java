package entity;

import main.KeyHandler;
import main.GamePanel;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Paths;
import java.awt.AlphaComposite;

public class Player extends Entity {
    private final GamePanel gp;
    private final KeyHandler keyHandler;

    private boolean isMoving = false;
    private boolean isExploding = true;
    private boolean sparkling = false;

    private BufferedImage[] idleImages = new BufferedImage[8];
    private BufferedImage[] sparkleImages = new BufferedImage[8];
    private BufferedImage walkImage;
    private BufferedImage[] explosionFrames = new BufferedImage[10];

    private int spriteCounter = 0;
    private int spriteNum = 0;

    private int explosionCounter = 0;

    public Player(GamePanel gp, KeyHandler keyHandler) {
        this(gp, keyHandler, false);
    }

    public Player(GamePanel gp, KeyHandler keyHandler, boolean sparkling) {
        this.gp = gp;
        this.keyHandler = keyHandler;
        this.sparkling = sparkling;

        setDefaultValues();
        loadPlayerImages();
    }

    private void setDefaultValues() {
        x = gp.SCREEN_WIDTH / 2 - 32;
        y = gp.SCREEN_HEIGHT / 2 - 48;
        speed = 1;
        direction = "down";
    }

    private void loadPlayerImages() {
        try {
            for (int i = 0; i < idleImages.length; i++) {
                idleImages[i] = ImageIO.read(Paths.get("res/Personnage/Lemiel/Idle/Lemiel_Idle_" + (i + 1) + ".png").toFile());
                if (sparkling) {
                    sparkleImages[i] = ImageIO.read(Paths.get("res/Personnage/Lemiel/Sparkle/Sparkle-" + (i + 1) + ".png").toFile());
                }
            }

            walkImage = ImageIO.read(Paths.get("res/Personnage/Lemiel_Avatar.png").toFile());

            for (int i = 0; i < explosionFrames.length; i++) {
                explosionFrames[i] = ImageIO.read(Paths.get("res/explosions/Explosion_two_colors" + (i + 1) + ".png").toFile());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void update() {
        handleMovement();
        constrainToScreen();

        spriteCounter++;

        if (!isExploding) {
            animateIdling();
        } else {
            animateExplosion();
        }
    }

    private void handleMovement() {
        isMoving = false;

        if (keyHandler.upPressed) {
            y -= speed;
            direction = "up";
            isMoving = true;
        } else if (keyHandler.downPressed) {
            y += speed;
            direction = "down";
            isMoving = true;
        } else if (keyHandler.leftPressed) {
            x -= speed;
            direction = "left";
            isMoving = true;
        } else if (keyHandler.rightPressed) {
            x += speed;
            direction = "right";
            isMoving = true;
        }
    }

    private void constrainToScreen() {
        x = Math.max(0, Math.min(x, gp.SCREEN_WIDTH - 64));
        y = Math.max(0, Math.min(y, gp.SCREEN_HEIGHT - 96));
    }

    private void animateIdling() {
        if (spriteCounter % 10 == 0) {
            spriteNum = (spriteNum + 1) % idleImages.length;
        }
    }

    private void animateExplosion() {
        if (spriteCounter % 6 == 0) {
            explosionCounter++;
            if (explosionCounter >= explosionFrames.length) {
                explosionCounter = 0;
                isExploding = false;
            }
        }
    }

    public void draw(Graphics2D g2) {
                // A COMPLETE SWITCH STATEMENT FOR DIRECTION
        // switch (direction) {
        //     case "up":
        //         image = Idle2; // Use the appropriate image for the direction
        //         break;
        //     case "down":
        //         image = Idle1; // Use the appropriate image for the direction
        //         if (Idle1 == null) {
        //             System.out.println("Im here! Image not found for direction: " + direction);
        //         }
        //         break;
        //     case "left":
        //         image = Idle3; // Use the appropriate image for the direction
        //         break;
        //     case "right":
        //         image = Idle4; // Use the appropriate image for the direction
        //         break;
        // }

        if (isExploding) {
            BufferedImage explosionImage = explosionFrames[explosionCounter];
            g2.drawImage(explosionImage, x - 32, y - 32, 128, 128, null);
        } else if (isMoving) {
            g2.drawImage(walkImage, x, y, 64, 96, null);
        } else {
            // Draw Idle
            BufferedImage idleImage = idleImages[spriteNum];
            g2.drawImage(idleImage, x, y, 64, 96, null);

            // Draw Sparkling on top if active
            if (sparkling && sparkleImages[spriteNum] != null) {
                // Optional: Set transparency
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f)); // 70% opacity
                BufferedImage sparkleImage = sparkleImages[spriteNum];
                g2.drawImage(sparkleImage, x, y, 64, 96, null);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f)); // Reset opacity
            }
        }
    }
}
