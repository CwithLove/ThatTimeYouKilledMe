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
        isHovered = rect.contains(mousePos);
    }

    public void render(Graphics2D g2d) {
        if (isClicked) {
            g2d.setColor(clickColor);
        } else if (isHovered) {
            g2d.setColor(hoverColor);
        } else {
            g2d.setColor(normalColor);
        }
        g2d.fill(rect);

        g2d.setColor(Color.WHITE);
        g2d.setFont(font);
        FontMetrics metrics = g2d.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int textHeight = metrics.getHeight();
        g2d.drawString(text, rect.x + (rect.width - textWidth) / 2, rect.y + (rect.height + textHeight / 2) / 2);
    }

    public void onClick() {
        if (onClick != null) {
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
}
