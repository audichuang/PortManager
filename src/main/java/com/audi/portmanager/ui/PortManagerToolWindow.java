package com.audi.portmanager.ui;

import com.audi.portmanager.model.PortProcessInfo;
import com.audi.portmanager.service.PortService;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import com.intellij.ui.JBColor;
import javax.swing.border.Border;
import com.intellij.ide.util.PropertiesComponent;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class PortManagerToolWindow {

    private static final Logger LOG = Logger.getInstance(PortManagerToolWindow.class);
    private static final String NOTIFICATION_GROUP_ID = "PortManagerNotifications"; // Define a notification group ID
    private static final String PORT_HISTORY_KEY = "PortManager.PortHistory";
    private static final int MAX_HISTORY_SIZE = 10;

    private final Project project;
    private final ToolWindow toolWindow;
    private final PortService portService;
    private JPanel toolWindowContent;
    private JComboBox<String> portInputField;
    private JButton findButton;
    private JButton killButton;
    private JTable processTable;
    private DefaultTableModel tableModel;
    private final PropertiesComponent propertiesComponent;

    public PortManagerToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        this.portService = new PortService(); // Instantiate the service
        this.propertiesComponent = PropertiesComponent.getInstance(project);
        initializeUI();
        setupListeners();
    }

    private void initializeUI() {
        toolWindowContent = new JPanel(new BorderLayout(JBUI.scale(10), JBUI.scale(10)));
        toolWindowContent.setBorder(JBUI.Borders.empty(15));

        // --- Top Panel (Input and Find Button) ---
        JPanel topPanel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        topPanel.setBorder(JBUI.Borders.emptyBottom(10));

        JLabel portLabel = new JLabel("Port:");
        portLabel.setFont(portLabel.getFont().deriveFont(Font.BOLD));

        // 將文本框替換為下拉式選單，同時保持可編輯性
        portInputField = new JComboBox<>();
        portInputField.setEditable(true);
        loadPortHistory(); // 加載歷史記錄
        portInputField.setToolTipText("輸入端口號或從歷史記錄中選擇");
        portInputField.setPreferredSize(new Dimension(JBUI.scale(150), portInputField.getPreferredSize().height));

        findButton = new JButton("Find Processes");
        findButton.setFocusPainted(false);
        findButton.setBackground(new JBColor(new Color(235, 235, 235), new Color(50, 50, 50)));

        topPanel.add(portLabel, BorderLayout.WEST);
        topPanel.add(portInputField, BorderLayout.CENTER);
        topPanel.add(findButton, BorderLayout.EAST);

        // --- Center Panel (Table) ---
        String[] columnNames = { "PID", "Command/Name", "Port" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table cells non-editable
            }
        };
        processTable = new JBTable(tableModel);
        processTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        processTable.getTableHeader().setReorderingAllowed(false);
        processTable.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(80));
        processTable.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(350));
        processTable.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(60));

        // 美化表格
        processTable.setRowHeight(JBUI.scale(25));
        processTable.setIntercellSpacing(new Dimension(JBUI.scale(5), JBUI.scale(3)));
        processTable.getTableHeader().setFont(processTable.getTableHeader().getFont().deriveFont(Font.BOLD));

        // 交替行顏色
        processTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? table.getBackground()
                            : new JBColor(new Color(245, 245, 245), new Color(45, 45, 45)));
                }
                return c;
            }
        });

        JBScrollPane scrollPane = new JBScrollPane(processTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // --- Bottom Panel (Kill Button) ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.setBorder(JBUI.Borders.emptyTop(10));

        killButton = new JButton("Kill Selected Process");
        killButton.setEnabled(false); // Initially disabled
        killButton.setFocusPainted(false);
        killButton.setBackground(new JBColor(new Color(250, 220, 220), new Color(80, 40, 40)));
        killButton.setForeground(new JBColor(new Color(180, 0, 0), new Color(255, 160, 160)));
        killButton.setFont(killButton.getFont().deriveFont(Font.BOLD));

        bottomPanel.add(killButton);

        // --- Add panels to main content panel ---
        toolWindowContent.add(topPanel, BorderLayout.NORTH);
        toolWindowContent.add(scrollPane, BorderLayout.CENTER);
        toolWindowContent.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        // Find Button Listener
        findButton.addActionListener(e -> findProcessesAction());

        // 允許在輸入框按下 Enter 鍵觸發搜尋
        JTextField editor = (JTextField) portInputField.getEditor().getEditorComponent();
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    findProcessesAction();
                }
            }
        });

        // Kill Button Listener
        killButton.addActionListener(e -> killProcessAction());

        // Table Selection Listener (to enable/disable Kill button)
        processTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                killButton.setEnabled(processTable.getSelectedRow() != -1);
            }
        });
    }

    private void findProcessesAction() {
        // 獲取當前輸入的端口文本
        String portText = ((JTextField) portInputField.getEditor().getEditorComponent()).getText().trim();
        if (portText.isEmpty()) {
            Messages.showWarningDialog(project, "請輸入端口號。", "需要輸入");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                Messages.showErrorDialog(project, "端口號必須在 1 到 65535 之間。", "無效的端口");
                return;
            }
        } catch (NumberFormatException ex) {
            Messages.showErrorDialog(project, "請輸入有效的數字端口號。", "無效的輸入");
            return;
        }

        // 將端口號添加到歷史記錄
        addToPortHistory(portText);

        // Clear previous results before starting search
        ApplicationManager.getApplication().invokeLater(() -> {
            tableModel.setRowCount(0);
            killButton.setEnabled(false);
        });

        // Run the potentially long-running task in the background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在搜尋端口 " + port + " 的進程", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    List<PortProcessInfo> processes = portService.findProcessesOnPort(port);

                    // Update UI on the Event Dispatch Thread
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (processes.isEmpty()) {
                            // 不顯示通知，僅更新表格
                        } else {
                            for (PortProcessInfo info : processes) {
                                tableModel.addRow(new Object[] { info.getPid(), info.getCommand(), info.getPort() });
                            }
                            // 不顯示通知
                        }
                    });

                } catch (UnsupportedOperationException uoe) {
                    LOG.error(uoe);
                    ApplicationManager.getApplication().invokeLater(
                            () -> Messages.showErrorDialog(project, uoe.getMessage(), "不支持的操作系統"));
                } catch (Exception ex) {
                    LOG.error("Error finding processes on port " + port, ex);
                    ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project,
                            "查找進程時出錯: " + ex.getMessage(), "錯誤"));
                }
            }
        });
    }

    private void killProcessAction() {
        int selectedRow = processTable.getSelectedRow();
        if (selectedRow == -1) {
            return; // Should not happen
        }

        String pid = (String) tableModel.getValueAt(selectedRow, 0);
        String command = (String) tableModel.getValueAt(selectedRow, 1);
        String port = (String) tableModel.getValueAt(selectedRow, 2);

        // Confirmation dialog
        int confirmation = Messages.showYesNoDialog(project,
                "您確定要終止進程 PID: " + pid + " (" + command + ") 在端口 " + port + "?",
                "確認終止進程", Messages.getWarningIcon());

        if (confirmation != Messages.YES) {
            return;
        }

        // Run kill operation in background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在終止進程 " + pid, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                boolean success = portService.killProcess(pid);

                // Show result notification and update table on EDT
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (success) {
                        showNotification("成功終止進程 PID: " + pid, NotificationType.INFORMATION);
                        // Remove the row from the table
                        // Need to be careful if multiple rows were selected or table changed
                        // Re-find the row index based on PID before removing, or iterate and remove
                        for (int i = tableModel.getRowCount() - 1; i >= 0; i--) {
                            if (pid.equals(tableModel.getValueAt(i, 0))) {
                                tableModel.removeRow(i);
                                break; // Assume unique PID per port search
                            }
                        }

                        // Ensure kill button is disabled if no row is selected after removal
                        if (processTable.getSelectedRow() == -1) {
                            killButton.setEnabled(false);
                        }
                        // Optionally, refresh the list automatically after killing
                        // findProcessesAction();
                    } else {
                        Messages.showErrorDialog(project, "無法終止進程 PID: " + pid
                                + "。\n查看日誌了解更多詳情 (Help > Show Log in ...).", "終止失敗");
                    }
                });
            }
        });
    }

    // 加載端口歷史記錄
    private void loadPortHistory() {
        String historyStr = propertiesComponent.getValue(PORT_HISTORY_KEY, "");
        if (!historyStr.isEmpty()) {
            String[] historyItems = historyStr.split(",");
            for (String item : historyItems) {
                portInputField.addItem(item);
            }
        }
    }

    // 添加端口到歷史記錄
    private void addToPortHistory(String port) {
        // 檢查是否已存在，如果存在則先移除
        for (int i = 0; i < portInputField.getItemCount(); i++) {
            if (port.equals(portInputField.getItemAt(i))) {
                portInputField.removeItemAt(i);
                break;
            }
        }

        // 添加到最前面
        portInputField.insertItemAt(port, 0);
        portInputField.setSelectedIndex(0);

        // 如果超過最大數量，移除最舊的
        if (portInputField.getItemCount() > MAX_HISTORY_SIZE) {
            portInputField.removeItemAt(portInputField.getItemCount() - 1);
        }

        // 保存到持久化存儲
        savePortHistory();
    }

    // 保存端口歷史記錄
    private void savePortHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < portInputField.getItemCount(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(portInputField.getItemAt(i));
        }
        propertiesComponent.setValue(PORT_HISTORY_KEY, sb.toString());
    }

    // Helper method to show notifications
    private void showNotification(String content, NotificationType type) {
        Notification notification = new Notification(NOTIFICATION_GROUP_ID,
                "Port Manager", // Title
                content,
                type);
        Notifications.Bus.notify(notification, project);
    }

    public JPanel getContent() {
        return toolWindowContent;
    }
}