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
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;

/**
 * 埠口管理工具視窗
 * 提供用戶友好的介面查詢和管理佔用特定埠口的進程
 */
public class PortManagerToolWindow {

    private static final Logger LOG = Logger.getInstance(PortManagerToolWindow.class);
    private static final String NOTIFICATION_GROUP_ID = "PortManagerNotifications";
    private static final String PORT_HISTORY_KEY = "PortManager.PortHistory";
    private static final String FAVORITE_PORTS_KEY = "PortManager.FavoritePorts";
    private static final String LAYOUT_PREFERENCE_KEY = "PortManager.LayoutPreference";
    private static final int MAX_HISTORY_SIZE = 10;

    // 佈局常數
    private static final String LAYOUT_VERTICAL = "vertical";
    private static final String LAYOUT_HORIZONTAL = "horizontal";
    private static final String LAYOUT_AUTO = "auto";

    private final Project project;
    private final ToolWindow toolWindow;
    private final PortService portService;
    private JPanel toolWindowContent;
    private JPanel contentPanel; // 主要內容面板 (不包含搜尋區)
    private JSplitPane splitPane; // 分割面板
    private JComboBox<String> portInputField;
    private JButton findButton;
    private JButton killButton;
    private JButton settingsButton;
    private JButton layoutToggleButton; // 新增：佈局切換按鈕
    private JTable processTable;
    private DefaultTableModel tableModel;
    private JPanel favoritePortsPanel;
    private final PropertiesComponent propertiesComponent;
    private List<String> favoritePorts = new ArrayList<>();
    private String currentLayout = LAYOUT_AUTO; // 當前佈局模式

    public PortManagerToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        this.portService = new PortService();
        this.propertiesComponent = PropertiesComponent.getInstance(project);
        loadFavoritePorts();
        // 載入使用者佈局偏好
        currentLayout = propertiesComponent.getValue(LAYOUT_PREFERENCE_KEY, LAYOUT_AUTO);
        initializeUI();
        setupListeners();

        // 添加窗口大小變化監聽器
        toolWindow.getComponent().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateLayoutBasedOnSize();
            }
        });
    }

    private void initializeUI() {
        toolWindowContent = new JPanel(new BorderLayout(JBUI.scale(6), JBUI.scale(6)));
        toolWindowContent.setBorder(JBUI.Borders.empty(8));
        toolWindowContent.setBackground(UIUtil.getPanelBackground());

        // --- 頂部搜索面板 ---
        JPanel searchPanel = createSearchPanel();

        // --- 創建分割面板 ---
        splitPane = new JSplitPane();
        splitPane.setBackground(UIUtil.getPanelBackground());
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setDividerSize(JBUI.scale(4));
        splitPane.setContinuousLayout(true);

        // --- 常用埠口面板 ---
        JPanel favoritePortsContainer = createFavoritePortsPanel(false); // 初始為水平模式

        // --- 表格面板 ---
        JPanel tablePanel = createTablePanel();

        // --- 組合内容面板 ---
        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(UIUtil.getPanelBackground());

        // 初始佈局 - 先用垂直(上下)布局
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(favoritePortsContainer, BorderLayout.NORTH);
        contentPanel.add(tablePanel, BorderLayout.CENTER);

        // --- 組合所有面板 ---
        toolWindowContent.add(searchPanel, BorderLayout.NORTH);
        toolWindowContent.add(contentPanel, BorderLayout.CENTER);

        // 初始化佈局
        SwingUtilities.invokeLater(this::updateLayoutBasedOnSize);
    }

    /**
     * 根據窗口大小更新佈局
     */
    private void updateLayoutBasedOnSize() {
        Dimension size = toolWindow.getComponent().getSize();
        boolean isWide = size.width > size.height * 1.2; // 寬度超過高度1.2倍視為寬屏

        if (LAYOUT_AUTO.equals(currentLayout)) {
            updateLayout(isWide ? LAYOUT_HORIZONTAL : LAYOUT_VERTICAL);
        } else {
            updateLayout(currentLayout);
        }
    }

    /**
     * 更新佈局模式
     */
    private void updateLayout(String layoutMode) {
        contentPanel.removeAll();

        // 重新創建面板
        JPanel favoritePortsContainer = createFavoritePortsPanel(LAYOUT_HORIZONTAL.equals(layoutMode));
        JPanel tablePanel = createTablePanel();

        if (LAYOUT_HORIZONTAL.equals(layoutMode)) {
            // 水平佈局 (左右分割)
            splitPane.setOrientation(JSplitPane.HORIZONTAL_SPLIT);
            splitPane.setLeftComponent(favoritePortsContainer);
            splitPane.setRightComponent(tablePanel);

            // 設定分割比例 (30% 左側，70% 右側)
            SwingUtilities.invokeLater(() -> {
                int totalWidth = splitPane.getWidth();
                splitPane.setDividerLocation(totalWidth * 3 / 10);
            });

            contentPanel.add(splitPane, BorderLayout.CENTER);
        } else {
            // 垂直佈局 (上下分割)
            contentPanel.setLayout(new BorderLayout());
            contentPanel.add(favoritePortsContainer, BorderLayout.NORTH);
            contentPanel.add(tablePanel, BorderLayout.CENTER);
        }

        contentPanel.revalidate();
        contentPanel.repaint();

        // 更新按鈕狀態
        if (layoutToggleButton != null) {
            layoutToggleButton.setText(LAYOUT_HORIZONTAL.equals(layoutMode) ? "切換為上下佈局" : "切換為左右佈局");
        }
    }

    /**
     * 切換佈局模式
     */
    private void toggleLayout() {
        if (LAYOUT_AUTO.equals(currentLayout)) {
            // 從自動模式切換到指定模式
            Dimension size = toolWindow.getComponent().getSize();
            boolean isWide = size.width > size.height * 1.2;

            // 切換到相反模式
            currentLayout = isWide ? LAYOUT_VERTICAL : LAYOUT_HORIZONTAL;
        } else if (LAYOUT_HORIZONTAL.equals(currentLayout)) {
            currentLayout = LAYOUT_VERTICAL;
        } else {
            currentLayout = LAYOUT_HORIZONTAL;
        }

        // 保存使用者偏好
        propertiesComponent.setValue(LAYOUT_PREFERENCE_KEY, currentLayout);

        // 更新布局
        updateLayout(currentLayout);
    }

    /**
     * 創建搜索面板
     */
    private JPanel createSearchPanel() {
        JPanel searchPanel = new JPanel(new BorderLayout(JBUI.scale(6), 0));
        searchPanel.setBackground(UIUtil.getPanelBackground());
        searchPanel.setBorder(JBUI.Borders.empty(0, 0, 3, 0));

        // 標籤 - 使用更小的字體
        JLabel portLabel = new JLabel("埠口號:");
        portLabel.setFont(JBUI.Fonts.label(12).asBold());

        // 搜索欄位
        portInputField = new JComboBox<>();
        portInputField.setEditable(true);
        loadPortHistory();

        // 自訂下拉框渲染器
        portInputField.setRenderer(new PortHistoryRenderer());

        // 取得編輯器組件並設置提示文字
        JTextField editor = (JTextField) portInputField.getEditor().getEditorComponent();
        editor.setToolTipText("輸入埠口號或從歷史記錄中選擇");

        // 設置更精簡的邊框
        editor.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JBColor(new Color(200, 200, 200), new Color(80, 80, 80)), 1, true),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        // 超小型搜索按鈕
        findButton = createMiniStyledButton("Find", new Color(76, 175, 80), new Color(46, 125, 50));

        // 超小型設定按鈕
        settingsButton = createMiniStyledButton("Settings", new Color(66, 165, 245), new Color(30, 136, 229));

        // 新增：佈局切換按鈕
        layoutToggleButton = createMiniStyledButton("切換佈局", new Color(156, 39, 176), new Color(123, 31, 162));

        // 按鈕面板 - 減少間距
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));
        buttonsPanel.setBackground(UIUtil.getPanelBackground());
        buttonsPanel.add(findButton);
        buttonsPanel.add(settingsButton);
        buttonsPanel.add(layoutToggleButton);

        // 組合搜索面板
        JPanel inputContainer = new JPanel(new BorderLayout(JBUI.scale(5), 0));
        inputContainer.setBackground(UIUtil.getPanelBackground());
        inputContainer.add(portLabel, BorderLayout.WEST);
        inputContainer.add(portInputField, BorderLayout.CENTER);

        searchPanel.add(inputContainer, BorderLayout.CENTER);
        searchPanel.add(buttonsPanel, BorderLayout.EAST);

        return searchPanel;
    }

    /**
     * 創建極小型風格化按鈕
     */
    private JButton createMiniStyledButton(String text, Color backgroundColor, Color borderColor) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setFont(JBUI.Fonts.label(11));
        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));
        return button;
    }

    /**
     * 創建常用埠口面板
     * @param isHorizontalLayout 是否為水平(左右)佈局
     */
    private JPanel createFavoritePortsPanel(boolean isHorizontalLayout) {
        JPanel container = new JPanel(new BorderLayout());
        container.setBackground(UIUtil.getPanelBackground());

        // 面板標題
        JLabel titleLabel = new JLabel("常用埠口");
        titleLabel.setFont(JBUI.Fonts.label(12).asBold());
        titleLabel.setBorder(JBUI.Borders.empty(0, 2, 3, 0));

        // 創建常用埠口卡片面板 - 根據佈局模式選擇不同的布局管理器
        favoritePortsPanel = new JPanel();
        if (isHorizontalLayout) {
            // 垂直顯示卡片 (在水平佈局時)
            favoritePortsPanel.setLayout(new BoxLayout(favoritePortsPanel, BoxLayout.Y_AXIS));
        } else {
            // 水平顯示卡片 (在垂直佈局時)
            favoritePortsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, JBUI.scale(3), JBUI.scale(3)));
        }
        favoritePortsPanel.setBackground(UIUtil.getPanelBackground());

        // 將面板放入可滾動區域
        JBScrollPane scrollPane = new JBScrollPane(favoritePortsPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // 根據佈局模式調整大小
        if (isHorizontalLayout) {
            // 水平佈局時，高度不限制但寬度固定
            scrollPane.setPreferredSize(new Dimension(JBUI.scale(120), -1));
        } else {
            // 垂直佈局時，高度固定但寬度不限制
            scrollPane.setPreferredSize(new Dimension(-1, JBUI.scale(40)));
        }

        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(16);

        // 更新常用埠口卡片
        updateFavoritePortsPanel(isHorizontalLayout);

        container.add(titleLabel, BorderLayout.NORTH);
        container.add(scrollPane, BorderLayout.CENTER);

        // 創建帶邊框的容器
        JPanel roundedContainer = new JPanel(new BorderLayout());
        roundedContainer.setBackground(UIUtil.getPanelBackground());
        roundedContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JBColor(new Color(220, 220, 220), new Color(60, 60, 60)), 1, true),
                BorderFactory.createEmptyBorder(5, 6, 5, 6)));
        roundedContainer.add(container, BorderLayout.CENTER);

        return roundedContainer;
    }

    /**
     * 創建進程表格面板
     */
    private JPanel createTablePanel() {
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(UIUtil.getPanelBackground());
        tablePanel.setBorder(JBUI.Borders.empty(6, 0, 0, 0));

        // 表格標題
        JLabel tableTitle = new JLabel("進程列表");
        tableTitle.setFont(JBUI.Fonts.label(12).asBold());
        tableTitle.setBorder(JBUI.Borders.empty(0, 2, 3, 0));

        // 初始化表格
        String[] columnNames = { "PID", "程序名稱/命令", "埠口" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        processTable = new JBTable(tableModel);
        processTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        processTable.getTableHeader().setReorderingAllowed(false);
        processTable.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(80));
        processTable.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(260));
        processTable.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(50));

        // 精簡表格
        processTable.setRowHeight(JBUI.scale(22));
        processTable.setIntercellSpacing(new Dimension(JBUI.scale(5), JBUI.scale(2)));
        processTable.getTableHeader().setFont(JBUI.Fonts.label(11).asBold());

        // 啟用表格的行選擇高亮顯示
        processTable.setRowSelectionAllowed(true);
        processTable.setSelectionBackground(new JBColor(new Color(232, 242, 254), new Color(45, 53, 61)));
        processTable.setSelectionForeground(UIUtil.getListSelectionForeground(true));

        // 提高表格可視性
        processTable.setShowGrid(true);
        processTable.setGridColor(new JBColor(new Color(230, 230, 230), new Color(50, 50, 50)));

        // 設置表格渲染器
        processTable.setDefaultRenderer(Object.class, new ProcessTableCellRenderer());

        // 表格滾動面板
        JBScrollPane scrollPane = new JBScrollPane(processTable);
        scrollPane.setBorder(
                BorderFactory.createLineBorder(new JBColor(new Color(220, 220, 220), new Color(60, 60, 60)), 1));

        // 添加終止按鈕到底部 - 使用全新的按鈕實例
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(UIUtil.getPanelBackground());

        // 重新創建按鈕
        killButton = new JButton("Kill Process");
        killButton.setEnabled(false);
        killButton.setFocusPainted(false);
        killButton.setFont(JBUI.Fonts.label(11));
        killButton.setBackground(new JBColor(new Color(255, 82, 82), new Color(183, 28, 28)));
        killButton.setForeground(Color.WHITE);
        killButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(211, 47, 47), 1, true),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));

        // 直接在這裡添加動作監聽器
        killButton.addActionListener(e -> killProcessAction());

        buttonPanel.add(killButton);

        tablePanel.add(tableTitle, BorderLayout.NORTH);
        tablePanel.add(scrollPane, BorderLayout.CENTER);
        tablePanel.add(buttonPanel, BorderLayout.SOUTH);

        return tablePanel;
    }

    /**
     * 創建空狀態面板 (當沒有查詢結果時顯示)
     */
    private JPanel createEmptyStatePanel(String port) {
        JPanel emptyPanel = new JPanel();
        emptyPanel.setLayout(new BoxLayout(emptyPanel, BoxLayout.Y_AXIS));
        emptyPanel.setBackground(UIUtil.getPanelBackground());
        emptyPanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 20, 0));

        // 圖標
        JLabel iconLabel = new JLabel();
        try {
            // 嘗試使用 IntelliJ 內建圖標
            iconLabel.setIcon(com.intellij.icons.AllIcons.General.Information);
        } catch (Exception e) {
            // 圖標無法載入時不顯示
            LOG.warn("無法載入圖標", e);
        }
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 空間間隔
        emptyPanel.add(Box.createVerticalStrut(15));

        // 訊息標籤
        JLabel messageLabel = new JLabel("未找到監聽埠口 " + port + " 的進程");
        messageLabel.setFont(JBUI.Fonts.label(14));
        messageLabel.setForeground(new JBColor(new Color(120, 120, 120), new Color(180, 180, 180)));
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // 添加到面板
        if (iconLabel.getIcon() != null) {
            emptyPanel.add(iconLabel);
            emptyPanel.add(Box.createVerticalStrut(10));
        }
        emptyPanel.add(messageLabel);

        return emptyPanel;
    }

    /**
     * 更新常用埠口面板
     */
    private void updateFavoritePortsPanel(boolean isHorizontalLayout) {
        favoritePortsPanel.removeAll();

        if (favoritePorts.isEmpty()) {
            JLabel emptyLabel = new JLabel("尚未設定常用埠口，請點擊「Settings」按鈕進行設定。");
            emptyLabel.setForeground(new JBColor(new Color(120, 120, 120), new Color(180, 180, 180)));
            favoritePortsPanel.add(emptyLabel);
        } else {
            for (String port : favoritePorts) {
                favoritePortsPanel.add(createPortCard(port, isHorizontalLayout));
            }
        }

        favoritePortsPanel.revalidate();
        favoritePortsPanel.repaint();
    }

    /**
     * 創建埠口卡片
     * @param port 埠口號
     * @param isHorizontalLayout 是否為水平(左右)佈局
     */
    private JPanel createPortCard(String port, boolean isHorizontalLayout) {
        // 創建卡片面板 - 根據佈局模式調整大小
        JPanel card = new JPanel(new BorderLayout());

        if (isHorizontalLayout) {
            // 水平佈局下，卡片占据整个宽度且高度适中
            card.setPreferredSize(new Dimension(JBUI.scale(110), JBUI.scale(28)));
            card.setMaximumSize(new Dimension(Integer.MAX_VALUE, JBUI.scale(28)));
            card.setAlignmentX(Component.LEFT_ALIGNMENT);
        } else {
            // 垂直佈局下，卡片是小方块
            card.setPreferredSize(new Dimension(JBUI.scale(58), JBUI.scale(30)));
        }

        // 設置簡單邊框
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new JBColor(new Color(200, 200, 200), new Color(70, 70, 70)), 1, true),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)));

        // 設置背景顏色
        Color bgColor = new JBColor(new Color(245, 245, 250), new Color(50, 50, 55));
        card.setBackground(bgColor);

        // 埠口號標籤
        JLabel portLabel = new JLabel(port);
        portLabel.setFont(JBUI.Fonts.label(12).asBold());
        portLabel.setHorizontalAlignment(SwingConstants.CENTER);

        card.add(portLabel, BorderLayout.CENTER);

        // 添加點擊事件
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                card.setBackground(new JBColor(new Color(230, 230, 250), new Color(60, 60, 65)));
                card.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                card.setBackground(bgColor);
                card.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                portInputField.setSelectedItem(port);
                findProcessesAction();
            }
        });

        return card;
    }

    /**
     * 埠口歷史記錄渲染器
     */
    private class PortHistoryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value != null) {
                label.setText(value.toString());
                label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                if (!isSelected) {
                    label.setBackground(UIUtil.getListBackground());
                    label.setForeground(UIUtil.getListForeground());
                }
            }

            return label;
        }
    }

    /**
     * 進程表格單元格渲染器
     */
    private class ProcessTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            // 設置更小的邊距
            ((JLabel) c).setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));

            // 設置交替行顏色
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? new JBColor(new Color(252, 252, 252), new Color(43, 43, 43))
                        : new JBColor(new Color(246, 246, 246), new Color(49, 49, 49)));
            } else {
                // 使用預設的選中背景色，讓IDE主題控制
                c.setBackground(table.getSelectionBackground());
                c.setForeground(table.getSelectionForeground());
            }

            // 根據欄位設置對齊
            if (column == 0) { // PID 欄位
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEFT);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? new JBColor(new Color(240, 247, 255), new Color(38, 45, 52))
                            : new JBColor(new Color(232, 242, 254), new Color(43, 50, 57)));
                }
                c.setFont(c.getFont().deriveFont(Font.BOLD));
            } else { // 其他欄位
                ((JLabel) c).setHorizontalAlignment(SwingConstants.LEFT);
            }

            return c;
        }
    }

    private void setupListeners() {
        // 搜尋按鈕事件
        findButton.addActionListener(e -> findProcessesAction());

        // 設定按鈕事件
        settingsButton.addActionListener(e -> showFavoritePortsDialog());

        // 布局切換按鈕事件
        layoutToggleButton.addActionListener(e -> toggleLayout());

        // 輸入框按下 Enter 鍵事件
        JTextField editor = (JTextField) portInputField.getEditor().getEditorComponent();
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    findProcessesAction();
                }
            }
        });

        // 注意：killButton 的事件監聽器已在 createTablePanel() 方法中直接設置

        // 表格選擇事件 - 優化邏輯，確保按鈕狀態正確更新
        processTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = processTable.getSelectedRow();
                if (selectedRow != -1) {
                    // 檢查選中行是否有有效的 PID（不是 "-"）
                    Object pidObj = tableModel.getValueAt(selectedRow, 0);
                    boolean validPid = pidObj instanceof String && !"-".equals(pidObj);
                    killButton.setEnabled(validPid);
                    LOG.info("表格選擇行: " + selectedRow + ", 有效PID: " + validPid + ", 按鈕狀態: " + (validPid ? "啟用" : "禁用"));
                } else {
                    killButton.setEnabled(false);
                    LOG.info("未選擇表格行，Kill按鈕已禁用");
                }
            }
        });

        // 表格滑鼠點擊事件 - 額外監聽確保選擇正確觸發
        processTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                int row = processTable.rowAtPoint(evt.getPoint());
                if (row != -1) {
                    processTable.setRowSelectionInterval(row, row);
                    Object pidObj = tableModel.getValueAt(row, 0);
                    boolean validPid = pidObj instanceof String && !"-".equals(pidObj);
                    killButton.setEnabled(validPid);
                    LOG.info("滑鼠點擊表格行: " + row + ", 有效PID: " + validPid + ", 按鈕狀態: " + (validPid ? "啟用" : "禁用"));
                }
            }
        });
    }

    /**
     * 顯示常用埠口設定對話框
     */
    private void showFavoritePortsDialog() {
        // 創建主面板
        JPanel panel = new JPanel(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.empty(8));
        panel.setPreferredSize(new Dimension(320, 400));

        // 標題
        JLabel titleLabel = new JLabel("設定常用埠口");
        titleLabel.setFont(JBUI.Fonts.label(14).asBold());

        // 描述
        JLabel descLabel = new JLabel("請輸入或管理常用埠口 (1-65535)：");
        descLabel.setFont(JBUI.Fonts.label(12));

        // 頭部面板
        JPanel headerPanel = new JPanel(new VerticalLayout(3));
        headerPanel.setBackground(UIUtil.getPanelBackground());
        headerPanel.add(titleLabel);
        headerPanel.add(descLabel);

        // 創建埠口列表模型
        DefaultListModel<String> portListModel = new DefaultListModel<>();
        for (String port : favoritePorts) {
            portListModel.addElement(port);
        }

        // 創建列表
        JList<String> portList = new JList<>(portListModel);
        portList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        portList.setCellRenderer(new PortListCellRenderer());

        // 放入滾動面板
        JBScrollPane scrollPane = new JBScrollPane(portList);
        scrollPane.setBorder(
                BorderFactory.createLineBorder(new JBColor(new Color(200, 200, 200), new Color(80, 80, 80))));

        // --- 操作按鈕面板 ---
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actionPanel.setBackground(UIUtil.getPanelBackground());

        // 按鈕樣式
        Dimension buttonSize = new Dimension(JBUI.scale(28), JBUI.scale(28));
        Font buttonFont = JBUI.Fonts.label(12).asBold();
        Insets buttonMargin = JBUI.emptyInsets();

        // 添加新埠口按鈕
        JButton addButton = new JButton("+");
        addButton.setFont(buttonFont);
        addButton.setFocusPainted(false);
        addButton.setMargin(buttonMargin);
        addButton.setPreferredSize(buttonSize);

        // 刪除埠口按鈕
        JButton deleteButton = new JButton("-");
        deleteButton.setFont(buttonFont);
        deleteButton.setFocusPainted(false);
        deleteButton.setMargin(buttonMargin);
        deleteButton.setPreferredSize(buttonSize);
        deleteButton.setEnabled(false); // 初始禁用

        // 上移按鈕
        JButton moveUpButton = new JButton("↑");
        moveUpButton.setFont(buttonFont);
        moveUpButton.setFocusPainted(false);
        moveUpButton.setMargin(buttonMargin);
        moveUpButton.setPreferredSize(buttonSize);
        moveUpButton.setEnabled(false); // 初始禁用

        // 下移按鈕
        JButton moveDownButton = new JButton("↓");
        moveDownButton.setFont(buttonFont);
        moveDownButton.setFocusPainted(false);
        moveDownButton.setMargin(buttonMargin);
        moveDownButton.setPreferredSize(buttonSize);
        moveDownButton.setEnabled(false); // 初始禁用

        // 輔助函數：根據選擇更新按鈕狀態
        Runnable updateButtonStates = () -> {
            int selectedIndex = portList.getSelectedIndex();
            int listSize = portListModel.getSize();

            deleteButton.setEnabled(selectedIndex != -1);
            moveUpButton.setEnabled(selectedIndex > 0); // 如果不是第一項，則可以上移
            moveDownButton.setEnabled(selectedIndex != -1 && selectedIndex < listSize - 1); // 如果不是最後一項，則可以下移
        };

        // --- 添加按鈕監聽器 ---

        // 添加按鈕
        addButton.addActionListener(e -> {
            String input = JOptionPane.showInputDialog(panel, "請輸入埠口號 (1-65535):", "新增埠口", JOptionPane.PLAIN_MESSAGE);
            if (input != null && !input.trim().isEmpty()) {
                try {
                    int portNum = Integer.parseInt(input.trim());
                    if (portNum > 0 && portNum <= 65535) {
                        String portToAdd = input.trim();
                        if (!portListModel.contains(portToAdd)) { // 更有效的檢查
                            portListModel.addElement(portToAdd);
                            portList.setSelectedIndex(portListModel.getSize() - 1); // 選中新添加的項
                            portList.ensureIndexIsVisible(portListModel.getSize() - 1); // 滾動到新項
                        } else {
                            JOptionPane.showMessageDialog(panel, "該埠口已存在於列表中", "提示", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(panel, "埠口號必須在 1 到 65535 之間", "錯誤", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(panel, "請輸入有效的數字", "錯誤", JOptionPane.ERROR_MESSAGE);
                }
            }
            updateButtonStates.run(); // 添加後更新按鈕狀態
        });

        // 刪除按鈕
        deleteButton.addActionListener(e -> {
            int selectedIndex = portList.getSelectedIndex();
            if (selectedIndex != -1) {
                portListModel.remove(selectedIndex);
                // 嘗試選中前一項或新的最後一項
                if (portListModel.getSize() > 0) {
                    portList.setSelectedIndex(Math.min(selectedIndex, portListModel.getSize() - 1));
                }
            }
            updateButtonStates.run(); // 刪除後更新按鈕狀態
        });

        // 上移按鈕
        moveUpButton.addActionListener(e -> {
            int selectedIndex = portList.getSelectedIndex();
            if (selectedIndex > 0) { // 檢查是否可以上移
                // 交換模型中的元素
                String element = portListModel.remove(selectedIndex);
                portListModel.insertElementAt(element, selectedIndex - 1);
                // 重新選中移動的項
                portList.setSelectedIndex(selectedIndex - 1);
                portList.ensureIndexIsVisible(selectedIndex - 1); // 確保其可見
            }
            updateButtonStates.run(); // 移動後更新狀態
        });

        // 下移按鈕
        moveDownButton.addActionListener(e -> {
            int selectedIndex = portList.getSelectedIndex();
            if (selectedIndex != -1 && selectedIndex < portListModel.getSize() - 1) { // 檢查是否可以下移
                // 交換模型中的元素
                String element = portListModel.remove(selectedIndex);
                portListModel.insertElementAt(element, selectedIndex + 1);
                // 重新選中移動的項
                portList.setSelectedIndex(selectedIndex + 1);
                portList.ensureIndexIsVisible(selectedIndex + 1); // 確保其可見
            }
            updateButtonStates.run(); // 移動後更新狀態
        });

        // 列表選擇監聽器
        portList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // 僅在選擇穩定後反應
                updateButtonStates.run();
            }
        });

        // 將按鈕添加到操作面板
        actionPanel.add(addButton);
        actionPanel.add(deleteButton);
        actionPanel.add(moveUpButton);
        actionPanel.add(moveDownButton);

        // 組裝面板
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(actionPanel, BorderLayout.SOUTH);

        // 顯示對話框
        int result = JOptionPane.showOptionDialog(
                toolWindowContent,
                panel,
                "設定常用埠口",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, // 使用預設圖示
                new String[] { "確定", "取消" },
                "確定");

        if (result == JOptionPane.OK_OPTION) {
            // 保存排序後的埠口列表
            favoritePorts.clear();
            for (int i = 0; i < portListModel.getSize(); i++) {
                favoritePorts.add(portListModel.get(i));
            }
            saveFavoritePorts();

            // 根據當前布局模式更新主界面
            boolean isHorizontal = LAYOUT_HORIZONTAL.equals(currentLayout);
            if (LAYOUT_AUTO.equals(currentLayout)) {
                Dimension size = toolWindow.getComponent().getSize();
                isHorizontal = size.width > size.height * 1.2;
            }
            updateFavoritePortsPanel(isHorizontal);
        }
    }

    /**
     * 埠口列表單元格渲染器
     */
    private class PortListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            // 添加內邊距和底部邊框作為分隔
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0,
                            new JBColor(new Color(230, 230, 230), new Color(60, 60, 60))), // 細微的底部邊框
                    BorderFactory.createEmptyBorder(5, 10, 5, 10) // 內邊距
            ));

            if (!isSelected) {
                // 對於未選中的項使用標準列表背景/前景
                label.setBackground(UIUtil.getListBackground());
                label.setForeground(UIUtil.getListForeground());
            } else {
                // 使用標準列表選擇顏色
                label.setBackground(UIUtil.getListSelectionBackground(true)); // true 表示焦點樣式
                label.setForeground(UIUtil.getListSelectionForeground(true));
            }

            return label;
        }
    }

    /**
     * 載入常用埠口
     */
    private void loadFavoritePorts() {
        String portsStr = propertiesComponent.getValue(FAVORITE_PORTS_KEY, "");
        if (!portsStr.isEmpty()) {
            // 保留加載的順序
            favoritePorts = new ArrayList<>(Arrays.asList(portsStr.split(",")));
        } else {
            favoritePorts = new ArrayList<>(); // 如果為空，確保初始化
        }
    }

    /**
     * 保存常用埠口
     */
    private void saveFavoritePorts() {
        // 保存當前順序
        propertiesComponent.setValue(FAVORITE_PORTS_KEY, String.join(",", favoritePorts));
    }

    /**
     * 搜尋進程行動
     */
    private void findProcessesAction() {
        String portText = ((JTextField) portInputField.getEditor().getEditorComponent()).getText().trim();
        if (portText.isEmpty()) {
            Messages.showWarningDialog(project, "請輸入埠口號。", "需要輸入");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                Messages.showErrorDialog(project, "埠口號必須在 1 到 65535 之間。", "無效的埠口");
                return;
            }
        } catch (NumberFormatException ex) {
            Messages.showErrorDialog(project, "請輸入有效的數字埠口號。", "無效的輸入");
            return;
        }

        // 添加到歷史記錄
        addToPortHistory(portText);

        // 清空之前的結果
        ApplicationManager.getApplication().invokeLater(() -> {
            tableModel.setRowCount(0);
            killButton.setEnabled(false);
        });

        // 在背景執行搜尋
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "正在搜尋埠口 " + port + " 的進程", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    List<PortProcessInfo> processes = portService.findProcessesOnPort(port);

                    // 在 EDT 線程更新 UI
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (processes.isEmpty()) {
                            // 沒有找到進程 - 顯示簡單的表格提示
                            LOG.info("未找到埠口 " + port + " 上的進程");
                            tableModel.addRow(new Object[] { "-", "未找到監聽埠口 " + port + " 的進程", portText });
                        } else {
                            // 找到進程 - 填入表格
                            LOG.info("找到 " + processes.size() + " 個進程在埠口 " + port);

                            // 填入進程數據
                            for (PortProcessInfo info : processes) {
                                tableModel.addRow(new Object[] { info.getPid(), info.getCommand(), info.getPort() });
                            }

                            // 如果有結果，自動選擇第一行並啟用 Kill 按鈕
                            if (tableModel.getRowCount() > 0 && !"-".equals(tableModel.getValueAt(0, 0))) {
                                SwingUtilities.invokeLater(() -> {
                                    processTable.setRowSelectionInterval(0, 0);
                                    processTable.requestFocus();
                                    killButton.setEnabled(true);
                                });
                            }
                        }
                    });

                } catch (UnsupportedOperationException uoe) {
                    LOG.error(uoe);
                    ApplicationManager.getApplication().invokeLater(
                            () -> Messages.showErrorDialog(project, uoe.getMessage(), "不支援的作業系統"));
                } catch (Exception ex) {
                    LOG.error("搜尋埠口 " + port + " 的進程時發生錯誤", ex);
                    ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project,
                            "查找進程時出錯: " + ex.getMessage(), "錯誤"));
                }
            }
        });
    }

    /**
     * 終止進程行動
     */
    private void killProcessAction() {
        LOG.info("開始執行終止進程操作");

        try {
            // 檢查表格行選擇
            int selectedRow = processTable.getSelectedRow();
            LOG.info("選定行索引: " + selectedRow);

            if (selectedRow == -1) {
                LOG.warn("未選擇表格行，無法執行終止操作");
                Messages.showWarningDialog(project, "請先選擇要終止的進程", "未選擇進程");
                return;
            }

            // 獲取PID和進程資訊
            Object pidObj = tableModel.getValueAt(selectedRow, 0);
            LOG.info("獲取到PID物件：" + (pidObj != null ? pidObj.toString() : "null"));

            Object commandObj = tableModel.getValueAt(selectedRow, 1);
            Object portObj = tableModel.getValueAt(selectedRow, 2);

            // 處理表格中可能存在的佔位符 "-"
            if (!(pidObj instanceof String) || "-".equals(pidObj)) {
                LOG.warn("無效的PID值：" + pidObj);
                Messages.showInfoMessage(project, "無法終止無效的進程ID", "操作取消");
                return;
            }

            String pid = (String) pidObj;
            String command = (commandObj instanceof String) ? (String) commandObj : "";
            String port = (portObj instanceof String) ? (String) portObj : "";

            LOG.info("準備終止進程，PID: " + pid + ", 命令: " + command + ", 埠口: " + port);

            // 確認對話框
            int confirmation = Messages.showYesNoDialog(project,
                    "您確定要終止進程 PID: " + pid + " (" + command + ") 在埠口 " + port + "?",
                    "確認終止進程", Messages.getWarningIcon());

            if (confirmation != Messages.YES) {
                LOG.info("使用者取消了終止操作");
                return;
            }

            // 直接執行終止進程
            try {
                boolean success = portService.killProcess(pid);
                LOG.info("終止進程結果: " + (success ? "成功" : "失敗"));

                if (success) {
                    showNotification("成功終止進程 PID: " + pid, NotificationType.INFORMATION);
                    // 從表格中移除行
                    tableModel.removeRow(selectedRow);

                    // 如果表格為空或沒有選擇行，禁用終止按鈕
                    if (tableModel.getRowCount() == 0 || processTable.getSelectedRow() == -1) {
                        LOG.info("禁用終止按鈕");
                        killButton.setEnabled(false);
                    }
                } else {
                    LOG.error("無法終止進程：" + pid);
                    Messages.showErrorDialog(project,
                            "無法終止進程 PID: " + pid + "。\n" +
                                    "可能的原因：\n" +
                                    "1. 需要管理員權限\n" +
                                    "2. 進程已經退出\n" +
                                    "3. 操作被系統安全策略阻止\n\n" +
                                    "查看IDE日誌了解更多詳情 (Help > Show Log in...)",
                            "終止失敗");
                }
            } catch (Exception e) {
                LOG.error("終止進程時發生錯誤", e);
                Messages.showErrorDialog(project,
                        "終止進程時發生錯誤: " + e.getMessage() + "\n" +
                                "可能需要以管理員權限運行IDE",
                        "執行錯誤");
            }
        } catch (Exception e) {
            LOG.error("執行終止進程操作時出現異常", e);
            Messages.showErrorDialog(project,
                    "終止進程操作出錯:\n" + e.getMessage() + "\n\n" +
                            "請檢查IDE日誌以獲取詳細資訊",
                    "嚴重錯誤");
        }
    }

    /**
     * 加載埠口歷史記錄
     */
    private void loadPortHistory() {
        String historyStr = propertiesComponent.getValue(PORT_HISTORY_KEY, "");
        if (!historyStr.isEmpty()) {
            String[] historyItems = historyStr.split(",");
            for (String item : historyItems) {
                portInputField.addItem(item);
            }
        }
    }

    /**
     * 添加埠口到歷史記錄
     */
    private void addToPortHistory(String port) {
        // 檢查是否已存在
        for (int i = 0; i < portInputField.getItemCount(); i++) {
            if (port.equals(portInputField.getItemAt(i))) {
                portInputField.removeItemAt(i);
                break;
            }
        }

        // 添加到最前面
        portInputField.insertItemAt(port, 0);
        portInputField.setSelectedIndex(0);

        // 限制數量
        if (portInputField.getItemCount() > MAX_HISTORY_SIZE) {
            portInputField.removeItemAt(portInputField.getItemCount() - 1);
        }

        // 保存
        savePortHistory();
    }

    /**
     * 保存埠口歷史記錄
     */
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

    /**
     * 顯示通知
     */
    private void showNotification(String content, NotificationType type) {
        LOG.info("顯示通知: " + content);
        try {
            Notification notification = new Notification(NOTIFICATION_GROUP_ID,
                    "埠口管理器",
                    content,
                    type);
            Notifications.Bus.notify(notification, project);
        } catch (Exception e) {
            LOG.error("顯示通知時發生錯誤", e);
        }
    }

    /**
     * 獲取內容面板
     */
    public JPanel getContent() {
        return toolWindowContent;
    }

    /**
     * 流式佈局類，用於卡片換行
     */
    public class WrapLayout extends FlowLayout {
        public WrapLayout() {
            super();
        }

        public WrapLayout(int align) {
            super(align);
        }

        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getSize().width;
                // 使用容器的寬度，如果尚未設定則使用最大值
                if (targetWidth == 0) {
                    targetWidth = Integer.MAX_VALUE;
                }

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                int nmembers = target.getComponentCount();
                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);
                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();
                        // 如果當前行放不下新元件，並且當前行已有元件，則換行
                        if (rowWidth + d.width > maxWidth && rowWidth > 0) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }
                        // 如果不是行的第一個元件，則添加水平間隙
                        if (rowWidth != 0) {
                            rowWidth += hgap;
                        }
                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }
                // 添加最後一行
                addRow(dim, rowWidth, rowHeight);

                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;

                // 考慮父級滾動視圖
                Container scrollPane = SwingUtilities.getAncestorOfClass(JViewport.class, target);
                if (scrollPane != null && target.isValid()) {
                    // 保持計算出的寬度，允許水平滾動條在需要時出現
                }

                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);
            // 如果已有行，則添加垂直間隙
            if (dim.height > 0) {
                dim.height += getVgap();
            }
            dim.height += rowHeight;
        }
    }
}