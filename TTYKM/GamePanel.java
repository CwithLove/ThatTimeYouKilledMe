import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

public class GamePanel extends JPanel {
    private BufferedImage lemielImage, zarekImage, lemielAvatar, zarekAvatar;
    private BufferedImage crackPresent, crackFuture;
    private BufferedImage background;

    private final int tileWidth = 96;
    private final int tileHeight = 32;
    private final int deltaX = -24;
    private final int spacing = 72;
    private final int boardCols = 4;
    private final int boardRows = 4;

    private boolean isPlayerTurn = true; // true = Your Turn, false = Opponent Turn
    private Point selectedCharacter = null;
    private Timer moveTimer;
    private Point currentPos, targetPos;
    private BufferedImage movingImage;
    private int animationStep = 0;
    private final int totalSteps = 20;

    public GamePanel() {
        try {
            lemielImage = ImageIO.read(new File("res/Lemiel/Lemiel_Idle.png"));
            zarekImage = ImageIO.read(new File("res/Zarek/Zarek_Idle_1.png"));
            crackFuture = ImageIO.read(new File("res/Plateau/Crack_Future.png"));
            crackPresent = ImageIO.read(new File("res/Plateau/Crack_Present.png"));
            background = ImageIO.read(new File("res/Background.png"));
            lemielAvatar = ImageIO.read(new File("res/Avatar/Lemiel_Avatar.png"));
            zarekAvatar = ImageIO.read(new File("res/Avatar/Zarek_Avatar.png"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        setPreferredSize(new Dimension(1600, 800));
        setBackground(Color.BLACK);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point clicked = screenToBoard(e.getX(), e.getY());
                if (clicked == null) return;
        
                if (selectedCharacter == null) {
                    if (clicked.equals(new Point(0, 0))) {
                        selectedCharacter = clicked;
                        movingImage = lemielImage;
                        currentPos = selectedCharacter;
                        System.out.println("Bạn đã chọn Lemiel tại " + clicked);
                    } else if (clicked.equals(new Point(3, 3))) {
                        selectedCharacter = clicked;
                        movingImage = zarekImage;
                        currentPos = selectedCharacter;
                        System.out.println("Bạn đã chọn Zarek tại " + clicked);
                    }
                } else {
                    System.out.println("Di chuyển từ " + selectedCharacter + " đến " + clicked);
                    startMoveAnimation(selectedCharacter, clicked);
                    selectedCharacter = null;
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Draw the background
        // if (background != null) {
            // g2d.drawImage(background, 0, 0, getWidth(), getHeight(), null)
        // } 

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        int singleBoardWidth = boardCols * tileWidth + (boardRows - 1) * Math.abs(deltaX);
        int boardCenterShift = (tileWidth + deltaX) / 2;
        int mapHeight = boardRows * tileHeight;

        int presentStartX = centerX - tileWidth - boardCenterShift;
        int pastStartX = presentStartX - spacing - singleBoardWidth;
        int futureStartX = presentStartX + singleBoardWidth + spacing;
        int offsetY = centerY - mapHeight / 2;
    
        // Draw number of clones
        drawNbCloneP1(g2d);
        drawNbCloneP2(g2d);

        // Draw the area picker
        drawPickerP1(g2d, pastStartX, presentStartX, futureStartX, offsetY, 0);
        drawPickerP2(g2d, pastStartX, presentStartX, futureStartX, offsetY, 2);
        
        // Draw 3 boards
        drawBoard(g2d, pastStartX, offsetY, 0);
        drawBoard(g2d, presentStartX, offsetY, 1);
        drawBoard(g2d, futureStartX, offsetY, 2);
        
        // Characters on past and future boards (fixe)
        drawCharacter(g2d, 0, 0, pastStartX, offsetY, lemielImage);
        drawCharacter(g2d, 3, 3, pastStartX, offsetY, zarekImage);
        drawCharacter(g2d, 0, 0, futureStartX, offsetY, lemielImage);
        drawCharacter(g2d, 3, 3, futureStartX, offsetY, zarekImage);
        
        // Character on present board
        if (currentPos != null && targetPos != null && animationStep < totalSteps) {
            double t = animationStep / (double) totalSteps;
            double row = currentPos.x + t * (targetPos.x - currentPos.x);
            double col = currentPos.y + t * (targetPos.y - currentPos.y);
            drawCharacter(g2d, row, col, presentStartX, offsetY, movingImage);
        } else {
            drawCharacter(g2d, currentPos != null ? currentPos.x : 0,
                                currentPos != null ? currentPos.y : 0,
                                presentStartX, offsetY, lemielImage);
            drawCharacter(g2d, 3, 3, presentStartX, offsetY, zarekImage);
        }

        // Draw your turn or opponent turn
        Graphics2D g2 = (Graphics2D) g.create();
        String turnText = isPlayerTurn ? "Your Turn" : "Opponent Turn";

        g2.setFont(new Font("Arial Black", Font.BOLD, 36));
        FontMetrics fm = g2.getFontMetrics();
        int textWidth = fm.stringWidth(turnText);
        int x = getWidth() / 2 - textWidth / 2;
        int y = tileHeight * 2;

        // Shadow
        g2.setColor(Color.BLACK);
        g2.drawString(turnText, x + 2, y + 2);

        // Main text
        g2.setColor(Color.WHITE);
        g2.drawString(turnText, x, y);

        g2.dispose();
    }

    private void drawNbCloneP1(Graphics2D g2d) {
        int x = tileWidth;
        int y = tileHeight * 3;
        for (int i = 0; i < 4; i++) {
            g2d.drawImage(lemielImage, x + i * (lemielImage.getWidth() + 10), y, null);
        }
    }

    private void drawNbCloneP2(Graphics2D g2d) {
        int x = getWidth() - zarekImage.getWidth() * 4 - 10 * 4;
        int y = getHeight() - tileHeight * 4 ;
        for (int i = 0; i < 4; i++) {
            g2d.drawImage(zarekImage, x + i * (zarekImage.getWidth() + 10), y, null);
        }
    }

    private void drawPickerP1(Graphics2D g2d, int past, int present, int future, int startY, int area) {
        int size = 50;
        int y0 = startY - tileHeight * 3;
    
        int[] xs = new int[3];
        xs[0] = past + tileWidth * 2;
        xs[1] = present + tileWidth * 2;
        xs[2] = future + tileWidth * 2;
    
        // Lưu clip hiện tại để khôi phục sau
        Shape oldClip = g2d.getClip();
    
        for (int i = 0; i < 3; i++) {
            int x = xs[i];
            
            // Vẽ hình tròn đen cho các vùng không được chọn
            g2d.setColor(Color.WHITE);
            g2d.fillOval(x, y0, size, size);
            if (i == area && lemielAvatar != null) {
                // Vẽ hình ảnh trong hình tròn (được chọn)
                Ellipse2D.Float circle = new Ellipse2D.Float(x, y0, size, size);
                g2d.setClip(circle);
                g2d.drawImage(lemielAvatar, x, y0, size, size, null);
                g2d.setClip(oldClip); // KHÔNG quên reset clip sau mỗi ảnh
            }
        }
    
        // Khôi phục vùng vẽ ban đầu (phòng khi có sót)
        g2d.setClip(oldClip);
    }
    private void drawPickerP2(Graphics2D g2d, int past, int present, int future, int startY, int area) {
        int size = 50;
        int y0 = startY + tileHeight * 6;
    
        int[] xs = new int[3];
        xs[0] = past + tileWidth / 2;
        xs[1] = present + tileWidth / 2;
        xs[2] = future + tileWidth / 2;
    
        // Lưu clip hiện tại để khôi phục sau
        Shape oldClip = g2d.getClip();
    
        for (int i = 0; i < 3; i++) {
            int x = xs[i];
    
            // Vẽ hình tròn đen cho các vùng không được chọn
            g2d.setColor(Color.WHITE);
            g2d.fillOval(x, y0, size, size);
            if (i == area && lemielAvatar != null) {
                // Vẽ hình ảnh trong hình tròn (được chọn)
                Ellipse2D.Float circle = new Ellipse2D.Float(x, y0, size, size);
                g2d.setClip(circle);
                g2d.drawImage(zarekAvatar, x, y0, size, size, null);
                g2d.setClip(oldClip); // KHÔNG quên reset clip sau mỗi ảnh
            }
        }
    
        // Khôi phục vùng vẽ ban đầu (phòng khi có sót)
        g2d.setClip(oldClip);
    }

    private void drawBoard(Graphics2D g2d, int startX, int startY, int time_flag) {        
        int x0, x1, x2, x3, y0, y1, y2, y3 = startY + tileHeight;
        // Draw tiles
        for (int row = 0; row < boardRows; row++) {
            x0 = startX + deltaX * row;
            y0 = startY + row * tileHeight;
            for (int col = 0; col < boardCols; col++) {
                x1 = x0 + tileWidth;
                y1 = y0;
                x2 = x1 + deltaX;
                y2 = y1 + tileHeight;
                x3 = x0 + deltaX;
                y3 = y0 + tileHeight;

                Polygon tile = new Polygon(new int[]{x0, x1, x2, x3}, new int[]{y0, y1, y2, y3}, 4);
                g2d.setColor(Color.LIGHT_GRAY);
                g2d.fill(tile);

                Color black = null, white = null;
                switch (time_flag % 3) {
                    case 0:
                        white = new Color(0xe8e7de); // Light gray
                        black = new Color(0xbfb9b4); // Medium dark gray
                        break;
                    case 1:
                        white = new Color(0xb3afac);
                        black = new Color(0x8e8a84); 
                        break;
                    case 2:
                        white = new Color(0x777871);
                        black = new Color(0x545251);
                        break;
                }

                if (row % 2 == 0 && col % 2 == 0 || row %2 == 1 && col % 2 == 1) {
                    g2d.setColor(white);
                } else {
                    g2d.setColor(black);
                }

                g2d.fill(tile);
                g2d.setColor(Color.GRAY);
                g2d.draw(tile);
                x0 += tileWidth;
            }
        }

        // Draw base bottom
        x0 = startX + deltaX * (boardRows);
        y0 = startY + tileHeight * boardRows;
        x1 = x0 + tileWidth * boardCols;
        y1 = y0;
        x2 = x1;
        y2 = y1 + tileHeight / 2;
        x3 = x0;
        y3 = y0 + tileHeight / 2;
        Polygon baseBottom = new Polygon(new int[]{x0, x1, x2, x3}, new int[]{y0, y1, y2, y3}, 4);
        g2d.setColor(Color.GRAY);
        g2d.fill(baseBottom);
        g2d.setColor(Color.BLACK);
        g2d.draw(baseBottom);

        // Draw base right
        x0 = startX + tileWidth * (boardCols);
        y0 = startY;
        x1 = x0;
        y1 = y0 + tileHeight / 2;
        x2 = x1 + deltaX * boardRows;
        y2 = y1 + tileHeight * boardRows;
        x3 = x2;
        y3 = y2 - tileHeight / 2;
        Polygon baseRight = new Polygon(new int[]{x0, x1, x2, x3}, new int[]{y0, y1, y2, y3}, 4);
        g2d.setColor(Color.GRAY);
        g2d.fill(baseRight);
        g2d.setColor(Color.BLACK);
        g2d.draw(baseRight);

        // Draw crack
        if (time_flag % 3 == 0) {
            
        } else if (time_flag == 1) {
            drawCrack(g2d, startX, startY, crackPresent);
        } else {
            drawCrack(g2d, startX, startY, crackFuture);
        }
    }   

    private void drawCrack(Graphics2D g2d, int startX, int startY, BufferedImage fullImage) {
        int x0 = startX;
        int y0 = startY;

        int x1 = x0 + tileWidth * boardCols;
        int y1 = y0;

        int x2 = x1 + deltaX * boardRows;
        int y2 = y1 + tileHeight * boardRows;

        int x3 = x0 + deltaX * boardRows;
        int y3 = y0 + tileHeight * boardRows;

        Shape oldClip = g2d.getClip();
        Polygon baseBottom = new Polygon(new int[]{x0, x1, x2, x3}, new int[]{y0, y1, y2, y3}, 4);
        g2d.setClip(baseBottom);

        AffineTransform transform = new AffineTransform(
            (x1 - x0) / (double) fullImage.getWidth(),  // m00: scale + shear X
            (y1 - y0) / (double) fullImage.getWidth(),  // m10: scale + shear Y
            (x3 - x0) / (double) fullImage.getHeight(), // m01: scale + shear X (orthogonal)
            (y3 - y0) / (double) fullImage.getHeight(), // m11: scale + shear Y (orthogonal)
            x0, y0                                      // m02, m12: translate
        );

        g2d.drawImage(fullImage, transform, null);
        g2d.setClip(oldClip);
    }


    private void drawCharacter(Graphics2D g2d, double row, double col, int startX, int startY, BufferedImage img) {
        int x = (int)(col * tileWidth + row * deltaX + startX);
        int y = (int)(row * tileHeight + startY + 16);

        int drawX = x + (tileWidth + deltaX) / 2 - img.getWidth() / 2;
        int drawY = y + tileHeight / 2 - img.getHeight();

        g2d.drawImage(img, drawX, drawY, null);
    }


    private Point screenToBoard(int x, int y) {
        int startX = getWidth() / 2 - tileWidth - ((tileWidth + deltaX) / 2);
        int startY = getHeight() / 2 - (boardRows * tileHeight / 2);

        for (int row = 0; row < boardRows; row++) {
            for (int col = 0; col < boardCols; col++) {
                int px = col * tileWidth + row * deltaX + startX;
                int py = row * tileHeight + startY;
                Polygon tile = new Polygon();
                tile.addPoint(px, py);
                tile.addPoint(px + tileWidth, py);
                tile.addPoint(px + tileWidth + deltaX, py + tileHeight);
                tile.addPoint(px + deltaX, py + tileHeight);
                if (tile.contains(x, y)) return new Point(row, col);
            }
        }
        return null;
    }

    private void startMoveAnimation(Point from, Point to) {
        currentPos = from;
        targetPos = to;
        animationStep = 0;

        if (moveTimer != null && moveTimer.isRunning()) {
            moveTimer.stop();
        }

        moveTimer = new Timer(30, e -> {
            animationStep++;
            if (animationStep >= totalSteps) {
                moveTimer.stop();
                currentPos = targetPos;
            }
            repaint();
        });

        moveTimer.start();
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("That Time You Killed Me - Java Edition");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new GamePanel());
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
