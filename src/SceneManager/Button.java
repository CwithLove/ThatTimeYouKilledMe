package SceneManager;

import java.awt.*;

public class Button {

    private Rectangle rect;
    private String text;
    private Font font;
    public Color normalColor;
    public Color hoverColor;
    public Color clickColor;
    private boolean isHovered = false;
    private boolean isClicked = false;
    private Runnable onClick;
    private boolean enabled = true;

    public Button(int x, int y, int width, int height, String text, Runnable onClick) {
        this.rect = new Rectangle(x, y, width, height);
        this.text = text;
        this.font = new Font("Arial", Font.BOLD, 16);
        this.normalColor = new Color(100, 100, 200);
        this.hoverColor = new Color(130, 130, 230);
        this.clickColor = new Color(70, 70, 150);
        this.onClick = onClick;
    }

    public void update(Point mousePos) {
        if (!enabled) {
            isHovered = false;
            return;
        }
        
        isHovered = rect.contains(mousePos);
    }

    public void render(Graphics2D g2d) {
        if (enabled) {
            if (isClicked) {
                g2d.setColor(clickColor);
            } else if (isHovered) {
                g2d.setColor(hoverColor);
            } else {
                g2d.setColor(normalColor);
            }
        } else {
            g2d.setColor(new Color(100, 100, 100));
        }
        g2d.fill(rect);

        g2d.setColor(Color.BLACK);
        g2d.drawRect(rect.x, rect.y, rect.width, rect.height);

        g2d.setColor(Color.WHITE);
        g2d.setFont(font);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        int textX = rect.x + (rect.width - textWidth) / 2;
        int textY = rect.y + ((rect.height - textHeight) / 2) + metrics.getAscent();

        if (enabled) {
            g2d.setColor(Color.BLACK);
        } else {
            g2d.setColor(new Color(70, 70, 70));
        }

        g2d.drawString(text, textX, textY);
    }

    public void onClick() {
        if (enabled && onClick != null) {
            onClick.run();
        }
    }

    public boolean contains(Point p) {
        return rect.contains(p);
    }

    public void setClicked(boolean clicked) {
        isClicked = clicked;
    }

    public void setLocation(int x, int y) {
        rect.setLocation(x, y);
    }

    public void setSize(int width, int height) {
        rect.setSize(width, height);
    }

    public void setFont(Font font) {
        this.font = font;
    }

		public void setNormalColor(Color color) {
			this.normalColor = color;
		}

		public void setHoverColor(Color color) {
			this.hoverColor = color;
		}

		public void setClickColor(Color color) {
			this.clickColor = color;
		}

    public int getX() {
        return rect.x;
    }

    public int getY() {
        return rect.y;
    }

    public int getWidth() {
        return rect.width;
    }

    public int getHeight() {
        return rect.height;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setHovering(boolean hovering) {
        this.isHovered = hovering;
    }
    
    public void setFontSize(int size) {
        this.font = new Font(font.getName(), font.getStyle(), size);
    }
}
