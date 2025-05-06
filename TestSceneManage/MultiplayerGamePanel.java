import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

public class MultiplayerGamePanel extends JPanel implements ActionListener {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int FPS = 60;
    private Timer timer;
    private SceneManager sceneManager;
    
    public MultiplayerGamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
        requestFocus();
        
        // 创建场景管理器
        sceneManager = new SceneManager(this);
        
        // 设置初始场景为多人游戏选择场景
        sceneManager.setScene(new HostOrConnectScene(sceneManager));
        
        // 启动游戏循环
        timer = new Timer(1000 / FPS, this);
        timer.start();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        sceneManager.render(g);
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        // 更新游戏逻辑
        sceneManager.update();
        
        // 重绘界面
        repaint();
    }
} 