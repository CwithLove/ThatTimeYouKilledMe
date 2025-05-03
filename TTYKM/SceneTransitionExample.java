import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SceneTransitionExample extends JFrame {
    private CardLayout cardLayout = new CardLayout();
    private JPanel mainPanel = new JPanel(cardLayout);
    private FadePanel fadePanel = new FadePanel();  // panel hiệu ứng fade

    public SceneTransitionExample() {
        setTitle("Scene Transition with Fade Effect");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 350);
        setLocationRelativeTo(null);

        JPanel menuPanel = createMenuPanel();
        JPanel settingsPanel = createSettingsPanel();
        JPanel gamePanel = createGamePanel();

        mainPanel.add(menuPanel, "Menu");
        mainPanel.add(settingsPanel, "Settings");
        mainPanel.add(gamePanel, "Game");

        setLayout(new BorderLayout());
        add(mainPanel, BorderLayout.CENTER);
        add(fadePanel, BorderLayout.CENTER); // luôn nằm trên
        setVisible(true);
    }

    private JPanel createMenuPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(52, 152, 219)); // Xanh biển
        panel.setBorder(new LineBorder(Color.WHITE, 5));

        JLabel title = new JLabel("MAIN MENU", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(Color.WHITE);

        JButton settingsBtn = new JButton("⚙ Settings");
        JButton gameBtn = new JButton("🎮 Start Game");
        JButton showSecretBtn = new JButton("👁 Hiện nút bí mật");
        JButton magicBtn = new JButton("✨ Magic Button");

        // Font chung
        Font btnFont = new Font("SansSerif", Font.PLAIN, 18);
        settingsBtn.setFont(btnFont);
        gameBtn.setFont(btnFont);
        showSecretBtn.setFont(btnFont);
        magicBtn.setFont(btnFont);

        // Ban đầu ẩn magicBtn
        magicBtn.setVisible(false);

        // Sự kiện các nút
        settingsBtn.addActionListener(e -> fadePanel.startFade(() -> cardLayout.show(mainPanel, "Settings")));
        gameBtn.addActionListener(e -> fadePanel.startFade(() -> cardLayout.show(mainPanel, "Game")));
        showSecretBtn.addActionListener(e -> magicBtn.setVisible(true));
        magicBtn.addActionListener(e -> fadePanel.startFade(() -> cardLayout.show(mainPanel, "Game")));

        // Panel chứa các nút
        JPanel buttonPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        buttonPanel.setOpaque(false);
        buttonPanel.add(settingsBtn);
        buttonPanel.add(gameBtn);
        buttonPanel.add(showSecretBtn);
        buttonPanel.add(magicBtn);

        panel.add(title, BorderLayout.NORTH);
        panel.add(buttonPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(241, 196, 15)); // Vàng
        panel.setBorder(new LineBorder(Color.DARK_GRAY, 5));

        JLabel title = new JLabel("SETTINGS", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(Color.BLACK);

        JButton backBtn = new JButton("⬅ Back to Menu");
        backBtn.setFont(new Font("SansSerif", Font.PLAIN, 18));
        backBtn.addActionListener(e -> fadePanel.startFade(() -> cardLayout.show(mainPanel, "Menu")));

        panel.add(title, BorderLayout.CENTER);
        panel.add(backBtn, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createGamePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(46, 204, 113)); // Xanh lá
        panel.setBorder(new LineBorder(Color.BLACK, 5));

        JLabel title = new JLabel("GAME SCREEN", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(Color.WHITE);

        JButton backBtn = new JButton("⬅ Back to Menu");
        backBtn.setFont(new Font("SansSerif", Font.PLAIN, 18));
        backBtn.addActionListener(e -> fadePanel.startFade(() -> cardLayout.show(mainPanel, "Menu")));

        panel.add(title, BorderLayout.CENTER);
        panel.add(backBtn, BorderLayout.SOUTH);
        return panel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SceneTransitionExample::new);
    }
}

// 🔸 Panel đặc biệt để vẽ hiệu ứng mờ dần
class FadePanel extends JPanel {
    float alpha = 0f;

    public FadePanel() {
        setOpaque(false); // không che mainPanel
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (alpha > 0f) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    public void startFade(Runnable onComplete) {
        alpha = 0f;
        Timer timer = new Timer(20, null);
        timer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                alpha += 0.05f;
                if (alpha >= 1f) {
                    alpha = 1f;
                    timer.stop();
                    onComplete.run();
                    fadeBack();
                }
                repaint();
            }
        });
        timer.start();
    }

    public void fadeBack() {
        Timer timer = new Timer(20, null);
        timer.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                alpha -= 0.05f;
                if (alpha <= 0f) {
                    alpha = 0f;
                    timer.stop();
                }
                repaint();
            }
        });
        timer.start();
    }
}
// 🔸 Lớp này tạo hiệu ứng mờ dần cho các panel