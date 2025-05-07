import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.*;

public class MainApp {

    // 定义我们想要的16:9长宽比
    private static final double ASPECT_RATIO = 16.0 / 9.0;
    // 最小窗口大小
    private static final int MIN_WIDTH = 640;
    private static final int MIN_HEIGHT = 360; // 16:9比例下的高度

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("TTYKM");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);

            GamePanel gamePanel = new GamePanel(frame);
            frame.add(gamePanel);

            // 设置初始大小为16:9比例
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int initialWidth = (int) (screenSize.width * 0.8); // 屏幕宽度的80%
            int initialHeight = (int) (initialWidth / ASPECT_RATIO);

            // 如果高度超出屏幕，则从高度计算宽度
            if (initialHeight > screenSize.height * 0.8) {
                initialHeight = (int) (screenSize.height * 0.8);
                initialWidth = (int) (initialHeight * ASPECT_RATIO);
            }

            // 设置初始尺寸
            frame.setSize(initialWidth, initialHeight);

            // 设置最小窗口大小
            frame.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));

            frame.addComponentListener(new ComponentAdapter() {
                private boolean isResizing = false;
                private Timer resizeEndTimer;
                private Dimension oldSize = frame.getSize();
                private boolean sizeSetByCode = false; // 标记是否由代码设置大小

                {
                    // 初始化计时器
                    resizeEndTimer = new Timer(200, e -> {
                        isResizing = false;

                        // 通知GamePanel大小已调整完成
                        gamePanel.onResizeCompleted(frame.getWidth(), frame.getHeight());

                        resizeEndTimer.stop();
                    });
                    resizeEndTimer.setRepeats(false);
                }

                @Override
                public void componentResized(ComponentEvent e) {
                    // 如果这次大小调整是由我们的代码触发的，不要再次调整
                    if (sizeSetByCode) {
                        sizeSetByCode = false;
                        return;
                    }

                    // 获取当前窗口大小
                    int currentWidth = frame.getWidth();
                    int currentHeight = frame.getHeight();

                    // 确保窗口不小于最小大小
                    if (currentWidth < MIN_WIDTH) {
                        currentWidth = MIN_WIDTH;
                    }
                    if (currentHeight < MIN_HEIGHT) {
                        currentHeight = MIN_HEIGHT;
                    }

                    // 计算目标大小
                    int targetHeight = (int) (currentWidth / ASPECT_RATIO);
                    int targetWidth = (int) (currentHeight * ASPECT_RATIO);

                    // 检测用户是否正在进行对角线拖动（宽度和高度同时变化）
                    boolean widthChanged = oldSize.width != currentWidth;
                    boolean heightChanged = oldSize.height != currentHeight;

                    if (widthChanged && heightChanged) {
                        // 对角线拖动 - 判断变化较大的方向
                        double widthChangeRatio = Math.abs((double)(currentWidth - oldSize.width) / oldSize.width);
                        double heightChangeRatio = Math.abs((double)(currentHeight - oldSize.height) / oldSize.height);

                        sizeSetByCode = true;
                        if (widthChangeRatio >= heightChangeRatio) {
                            // 宽度变化比较大，保持宽度，调整高度
                            frame.setSize(currentWidth, targetHeight);
                        } else {
                            // 高度变化比较大，保持高度，调整宽度
                            frame.setSize(targetWidth, currentHeight);
                        }
                    } else if (widthChanged || heightChanged) {
                        // 单向拖动 - 根据变化的维度调整另一个维度
                        sizeSetByCode = true;
                        if (widthChanged) {
                            // 宽度变化，调整高度
                            frame.setSize(currentWidth, targetHeight);
                        } else {
                            // 高度变化，调整宽度
                            frame.setSize(targetWidth, currentHeight);
                        }
                    }

                    // 更新旧大小
                    oldSize = new Dimension(frame.getWidth(), frame.getHeight());

                    // 检测调整大小开始
                    if (!isResizing) {
                        isResizing = true;
                        gamePanel.onResizeStarted();
                    }

                    // 通知GamePanel大小调整中
                    gamePanel.onResizing(frame.getWidth(), frame.getHeight());

                    // 重新启动计时器
                    resizeEndTimer.restart();
                }
            });

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
