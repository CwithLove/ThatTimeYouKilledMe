/*
 * @Author: ThearchyHelios work@thearchyhelios.com
 * @Date: 2025-05-05 23:58:23
 * @LastEditors: ThearchyHelios work@thearchyhelios.com
 * @LastEditTime: 2025-05-06 08:53:47
 * @FilePath: /ThatTimeYouKilledMe/TestSceneManage/MultiplayerGame.java
 * @Description: 
 */
import javax.swing.*;

public class MultiplayerGame {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("TTYKM");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            
            MultiplayerGamePanel gamePanel = new MultiplayerGamePanel();
            frame.add(gamePanel);
            
            frame.pack();
            frame.setLocationRelativeTo(null); // 居中显示
            frame.setVisible(true);
        });
    }
} 