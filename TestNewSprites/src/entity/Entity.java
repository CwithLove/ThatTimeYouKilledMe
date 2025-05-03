package entity;

import java.awt.image.BufferedImage;

public class Entity {
    public int x, y; // Position of the entity
    public int speed; // Speed of the entity
    
    public BufferedImage Idle1, Idle2, Idle3, Idle4, Walk1; // Image of the entity
    public String direction; // Direction of the entity (up, down, left, right)
}
