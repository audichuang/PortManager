package com.audi.portmanager.ui;

import com.audi.portmanager.model.PortProcessInfo;
import com.audi.portmanager.service.PortService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.*;
import com.intellij.ui.components.panels.VerticalLayout;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 埠口管理工具視窗
 * 主要功能:
 * 1. 顯示及管理常用埠口列表 (Favorites)。
 * 2. 允許用戶輸入埠號以查詢佔用該埠口的進程。
 * 3. 在表格中清晰展示查詢結果，包括 PID、進程命令/名稱及埠號。
 * 4. 提供便捷的進程終止功能，可透過工具列按鈕或直接雙擊表格行來操作。
 * 5. 提供常用埠口的管理介面，支援新增、刪除及拖放排序。
 */
public class PortManagerToolWindow {

    // 日誌記錄器，用於記錄插件運行時的詳細資訊和潛在錯誤。
    private static final Logger LOG = Logger.getInstance(PortManagerToolWindow.class);
    // 定義通知的分組 ID，確保通知來源清晰。
    private static final String NOTIFICATION_GROUP_ID = "PortManagerNotifications";
    // 用於在 PropertiesComponent 中持久化儲存常用埠口列表的鍵名。
    private static final String FAVORITE_PORTS_KEY = "PortManager.FavoritePorts";

    // 當前 IntelliJ 的專案對象，用於訪問專案相關服務。
    private final Project project;
    // 代表此插件的 IntelliJ 工具視窗實例。
    private final ToolWindow toolWindow;
    // 封裝了埠口查詢和進程操作核心邏輯的服務類。
    private final PortService portService;
    // IntelliJ 提供的組件，用於讀寫插件的持久化設置（例如常用埠口）。
    private final PropertiesComponent propertiesComponent;

    // --- UI 組件聲明 ---
    // 工具視窗根面板，容納所有 UI 元素。
    private JPanel toolWindowContent;
    // 主分割面板，分隔左側常用埠口列表和右側的進程表格。
    private JSplitPane mainSplitPane;
    // 允許用戶輸入要查詢的埠號的文字欄位。
    private JBTextField portInputField;
    // 觸發埠口查詢操作的按鈕。
    private JButton findButton;
    // 打開常用埠口管理對話框的按鈕。
    private JButton settingsButton;
    // 用於展示查詢到的進程信息的表格。
    private JBTable processTable;
    // processTable 的數據模型，管理表格數據。
    private DefaultTableModel tableModel;
    // 左側用於顯示常用埠口的列表組件。
    private JBList<String> favoritePortsList;
    // favoritePortsList 的數據模型，管理常用埠口列表數據。
    private DefaultListModel<String> favoritePortsListModel;
    // IntelliJ Action，代表表格工具列上的 "Kill Process" 操作。
    private AnAction killProcessAction;

    // 記憶體中儲存的常用埠口號列表。
    private List<String> favoritePorts = new ArrayList<>();

    /**
     * 構造函數：初始化 PortManagerToolWindow。
     *
     * @param project    當前活動的 IntelliJ 專案。
     * @param toolWindow 此插件對應的 ToolWindow 實例。
     */
    public PortManagerToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        this.portService = new PortService(); // 實例化埠口處理服務。
        this.propertiesComponent = PropertiesComponent.getInstance(project); // 獲取專案級別的設置存儲器。
        loadFavoritePorts();      // 從持久化設置中加載之前保存的常用埠口。
        initializeUI();           // 構建和初始化用戶界面元素。
        setupListeners();         // 為 UI 組件設置必要的事件監聽器。
        updateFavoritePortsList(); // 將加載的常用埠口顯示在左側列表中。
    }

    /**
     * 初始化並佈局用戶界面的所有組件。
     */
    private void initializeUI() {
        // 創建根面板，使用邊界佈局，並設定組件間的視覺間距。
        toolWindowContent = new JBPanel<>(new BorderLayout(JBUI.scale(5), JBUI.scale(5)));
        // 為根面板設定內邊距，增加視覺舒適度。
        toolWindowContent.setBorder(JBUI.Borders.empty(8));

        // 創建界面頂部的工具列（包含輸入框和按鈕）。
        JPanel topToolbarPanel = createTopToolbarPanel();
        // 創建界面左側的面板（用於顯示常用埠口）。
        JPanel leftPanel = createLeftPanel();
        // 創建界面右側的面板（用於顯示進程表格和相關操作）。
        JPanel rightPanel = createRightPanel();

        // 創建水平分割面板，將左側和右側面板分開。
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        mainSplitPane.setDividerSize(JBUI.scale(5)); // 設定分隔條的視覺寬度。
        mainSplitPane.setResizeWeight(0.25);         // 設定初始狀態下，左側面板佔總寬度的 25%。
        mainSplitPane.setBorder(BorderFactory.createEmptyBorder()); // 移除分割面板自身的邊框，使界面更簡潔。

        // 將頂部工具列放置在根面板的北部區域。
        toolWindowContent.add(topToolbarPanel, BorderLayout.NORTH);
        // 將包含左右兩部分的分割面板放置在根面板的中央區域。
        toolWindowContent.add(mainSplitPane, BorderLayout.CENTER);

        // 設定進程表格在沒有數據時顯示的初始提示文字（英文）。
        processTable.getEmptyText().setText("Enter a port and click 'Find', or click a favorite. Double-click a process to terminate.");
    }

    /**
     * 創建並返回包含埠口輸入框和主要操作按鈕的頂部工具列面板。
     *
     * @return 頂部工具列的 JPanel 實例。
     */
    private JPanel createTopToolbarPanel() {
        // 創建工具列主面板，使用邊界佈局，設定組件間的水平間距。
        JPanel panel = new JBPanel<>(new BorderLayout(JBUI.scale(8), 0));
        panel.setBorder(JBUI.Borders.emptyBottom(5)); // 在面板底部添加外邊距。

        // 創建容納 "Port:" 標籤和輸入框的子面板。
        JPanel inputPanel = new JBPanel<>(new BorderLayout(JBUI.scale(5), 0));
        // 創建 "Port:" 標籤。
        JLabel portLabel = new JBLabel("Port:");
        portLabel.setFont(JBUI.Fonts.label().asBold()); // 設置標籤文字為粗體。

        // 創建埠口輸入框 (JBTextField)。
        portInputField = new JBTextField();
        portInputField.setBorder(IdeBorderFactory.createBorder(SideBorder.ALL)); // 設置標準 IntelliJ 風格的邊框。
        portInputField.setToolTipText("Enter port number (1-65535)"); // 設定滑鼠懸停時的提示文字（英文）。

        // 將標籤添加到輸入面板的左側（西部）。
        inputPanel.add(portLabel, BorderLayout.WEST);
        // 將輸入框添加到輸入面板的中央。
        inputPanel.add(portInputField, BorderLayout.CENTER);

        // 創建容納 "Find" 和 "Settings" 按鈕的子面板，使用流式佈局並靠左對齊。
        JPanel buttonsPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0));
        buttonsPanel.setOpaque(false); // 使按鈕面板背景透明。

        // 創建 "Find" 按鈕，並設置圖標。
        findButton = new JButton("Find", AllIcons.Actions.Find);
        styleMiniButton(findButton); // 應用統一的迷你按鈕樣式。

        // 創建 "Settings" 按鈕，並設置圖標。
        settingsButton = new JButton("Settings", AllIcons.General.Settings);
        styleMiniButton(settingsButton); // 應用統一的迷你按鈕樣式。

        // 將按鈕添加到按鈕面板。
        buttonsPanel.add(findButton);
        buttonsPanel.add(settingsButton);

        // 將輸入面板（標籤+輸入框）添加到主工具列面板的中央。
        panel.add(inputPanel, BorderLayout.CENTER);
        // 將按鈕面板添加到主工具列面板的右側（東部）。
        panel.add(buttonsPanel, BorderLayout.EAST);

        return panel;
    }


    /**
     * 創建並返回用於顯示常用埠口列表的左側面板。
     *
     * @return 左側面板的 JPanel 實例。
     */
    private JPanel createLeftPanel() {
        // 創建左側面板，使用邊界佈局，設定組件垂直間距。
        JPanel panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(3)));
        // 為面板設置帶標題的邊框（英文標題 "Favorites"）。
        panel.setBorder(IdeBorderFactory.createTitledBorder("Favorites", false, JBUI.insets(5, 0, 0, 0)));

        // 初始化常用埠口列表的數據模型。
        favoritePortsListModel = new DefaultListModel<>();
        // 創建列表組件 (JBList) 並應用數據模型。
        favoritePortsList = new JBList<>(favoritePortsListModel);
        favoritePortsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // 限制用戶只能單選。
        favoritePortsList.setCellRenderer(new FavoritePortListCellRenderer());   // 使用自定義渲染器來美化列表項（例如添加圖標）。
        favoritePortsList.setBackground(UIUtil.getListBackground());             // 設置列表背景色，使其與 IntelliJ 主題一致。
        favoritePortsList.setToolTipText("Click to search this favorite port");   // 設定滑鼠懸停在列表項上時的提示（英文）。

        // 將列表放入一個可滾動的面板中，以便在列表項過多時可以滾動查看。
        JBScrollPane scrollPane = new JBScrollPane(favoritePortsList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder()); // 移除滾動面板自身的邊框，保持界面簡潔。
        // 將包含列表的滾動面板添加到左側面板的中央區域。
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 創建並返回包含進程表格和上方操作工具列的右側面板。
     *
     * @return 右側面板的 JPanel 實例。
     */
    private JPanel createRightPanel() {
        // 創建右側面板，使用邊界佈局，設定組件垂直間距。
        JPanel panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(3)));

        // 初始化表格的數據模型 (DefaultTableModel)。
        // 定義表格的列名："PID", "Process/Command", "Port"。
        // 重寫 isCellEditable 方法，使所有單元格預設為不可編輯。
        tableModel = new DefaultTableModel(new String[]{"PID", "Process/Command", "Port"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false; // 確保用戶不能直接在表格中修改數據。
            }
        };

        // 創建表格組件 (JBTable) 並應用數據模型。
        processTable = new JBTable(tableModel);
        processTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // 設置表格為單行選擇模式。
        processTable.getTableHeader().setReorderingAllowed(false);          // 禁止用戶拖動列頭來改變列的順序。
        processTable.setShowGrid(true);                                     // 顯示單元格之間的網格線。
        processTable.setGridColor(JBColor.border());                        // 設定網格線的顏色，使其與 IntelliJ 主題的邊框顏色一致。
        processTable.setIntercellSpacing(new Dimension(0, 0));              // 移除單元格之間的額外間距。
        processTable.setFillsViewportHeight(true);                          // 當表格數據不足以填滿視圖高度時，自動擴展背景。
        processTable.setRowHeight(JBUI.scale(24));                          // 設置表格中每一行的高度。
        processTable.setDefaultRenderer(Object.class, new ProcessTableCellRenderer()); // 為所有數據類型設置自定義的單元格渲染器。

        // 為表格設置滑鼠懸停提示，告知用戶雙擊可以終止進程（英文）。
        processTable.setToolTipText("Double-click a process in the table to try terminating it.");

        // 獲取表格的列模型，以設置各列的寬度屬性。
        TableColumnModel columnModel = processTable.getColumnModel();
        // 設定 PID 列的寬度。
        columnModel.getColumn(0).setPreferredWidth(JBUI.scale(80));
        columnModel.getColumn(0).setMinWidth(JBUI.scale(60));
        // 設定 Process/Command 列的寬度。
        columnModel.getColumn(1).setPreferredWidth(JBUI.scale(300));
        columnModel.getColumn(1).setMinWidth(JBUI.scale(150));
        // 設定 Port 列的寬度。
        columnModel.getColumn(2).setPreferredWidth(JBUI.scale(60));
        columnModel.getColumn(2).setMaxWidth(JBUI.scale(80));

        // 將表格放入可滾動面板中。
        JBScrollPane scrollPane = new JBScrollPane(processTable);
        scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.ALL)); // 為滾動面板添加標準邊框。

        // 創建位於表格上方的操作工具列（包含 "Kill Process" 按鈕）。
        ActionToolbar actionToolbar = createTableToolbar();
        // 將工具列添加到右側面板的北部區域。
        panel.add(actionToolbar.getComponent(), BorderLayout.NORTH);
        // 將包含表格的滾動面板添加到右側面板的中央區域。
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 創建並配置位於進程表格上方的 ActionToolbar。
     *
     * @return 配置好的 ActionToolbar 實例。
     */
    private ActionToolbar createTableToolbar() {
        // 創建一個預設的 Action 組，用於容納工具列上的按鈕。
        DefaultActionGroup actionGroup = new DefaultActionGroup();

        // 定義 "Kill Process" 這個 Action。
        killProcessAction = new AnAction("Kill Process", "Terminate selected process", AllIcons.Actions.Cancel) {
            // 當用戶點擊此 Action 對應的按鈕時執行的邏輯。
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                killSelectedProcessAction(); // 調用實際處理進程終止的方法。
            }

            // IntelliJ 定期調用此方法來更新 Action 的狀態（例如是否啟用）。
            @Override
            public void update(@NotNull AnActionEvent e) {
                // 調用輔助方法來根據當前表格的選擇狀態更新按鈕是否可用。
                updateKillButtonState(e.getPresentation());
            }

            // 指定此 Action 的 update 方法應在哪個線程執行。
            // EDT (Event Dispatch Thread) 是 Swing UI 操作的安全線程。
            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        };
        // 將定義好的 "Kill Process" Action 添加到 Action 組中。
        actionGroup.add(killProcessAction);

        // 使用 IntelliJ 的 ActionManager 根據 Action 組創建一個 ActionToolbar。
        ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(
                ActionPlaces.TOOLWINDOW_TOOLBAR_BAR, // 指定此工具列在 UI 中的上下文位置。
                actionGroup,                         // 提供包含 Actions 的組。
                true                                 // true 表示工具列按鈕水平排列。
        );
        // 將工具列的目標組件設置為進程表格。
        // 這使得 Action 的狀態（通過 update 方法）能自動響應表格的選擇變化。
        toolbar.setTargetComponent(processTable);
        // 為工具列組件本身添加一個底部的邊框線，以在視覺上將其與下面的表格分開。
        toolbar.getComponent().setBorder(JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0));

        return toolbar;
    }

    /**
     * 根據當前表格的選中狀態，更新 "Kill Process" Action 的啟用/禁用狀態。
     * 此方法由 killProcessAction 的 update 方法調用。
     *
     * @param presentation Action 的表示對象，用於控制其外觀和行為（例如 enabled 狀態）。
     */
    private void updateKillButtonState(Presentation presentation) {
        int selectedRow = processTable.getSelectedRow(); // 獲取表格中當前選中的行的索引。
        boolean enabled = false; // 預設將按鈕設置為禁用狀態。
        // 檢查是否有行被選中 (selectedRow != -1) 並且選中的行索引在有效範圍內。
        if (selectedRow != -1 && selectedRow < tableModel.getRowCount()) {
            // 獲取選中行第一列（PID 列）的數據。
            Object pidObj = tableModel.getValueAt(selectedRow, 0);
            // 判斷 PID 是否是一個有效的字串，並且不是用於表示無進程的占位符 "-"。
            enabled = pidObj instanceof String && !"-".equals(pidObj);
        }
        // 將計算出的 enabled 狀態應用到 Action 的 presentation 上。
        presentation.setEnabled(enabled);
    }

    /**
     * 為頂部工具列上的按鈕（Find, Settings）應用統一的迷你樣式。
     * 主要目的是移除按鈕獲得焦點時的視覺邊框，使界面更簡潔。
     *
     * @param button 需要設置樣式的 JButton。
     */
    private void styleMiniButton(JButton button) {
        button.setFocusPainted(false); // 禁止繪製焦點邊框。
    }

    /**
     * 更新左側常用埠口列表 (favoritePortsList) 的顯示內容。
     * 通常在加載或保存常用埠口後調用。
     */
    private void updateFavoritePortsList() {
        favoritePortsListModel.clear(); // 首先清空列表模型中的所有現有項。
        // 遍歷記憶體中的 favoritePorts 列表，將每個埠號添加到列表模型中。
        favoritePorts.forEach(favoritePortsListModel::addElement);
        // 記錄日誌，說明列表已更新及其包含的項目數量（英文日誌）。
        LOG.info("Favorite ports list updated with " + favoritePorts.size() + " items.");
    }

    // --- 事件監聽器設置 ---

    /**
     * 集中設置所有用戶界面組件所需的事件監聽器。
     */
    private void setupListeners() {
        // 為 "Find" 按鈕添加 ActionListener，點擊時觸發 findProcessesAction 方法。
        findButton.addActionListener(e -> findProcessesAction());
        // 為 "Settings" 按鈕添加 ActionListener，點擊時觸發 showFavoritePortsDialog 方法。
        settingsButton.addActionListener(e -> showFavoritePortsDialog());

        // 為埠口輸入框 (portInputField) 添加 KeyListener。
        portInputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                // 監聽 Enter 鍵按下事件。
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    findProcessesAction(); // 如果按下 Enter，觸發查找操作。
                }
            }
        });

        // 為左側常用埠口列表 (favoritePortsList) 添加 ListSelectionListener。
        favoritePortsList.addListSelectionListener(e -> {
            // 確保事件是在選擇穩定後觸發的（避免在拖動選擇過程中觸發多次）。
            if (!e.getValueIsAdjusting()) {
                String selectedPort = favoritePortsList.getSelectedValue(); // 獲取當前選中的埠號。
                if (selectedPort != null) { // 確保確實有選中的項。
                    LOG.info("Favorite port selected: " + selectedPort); // 記錄選中事件（英文日誌）。
                    portInputField.setText(selectedPort); // 將選中的埠號自動填入頂部的輸入框。
                    findProcessesAction(); // 自動觸發對該埠口的查詢。
                }
            }
        });

        // 為進程表格 (processTable) 的選擇模型添加 ListSelectionListener。
        // 主要目的是為了觸發 ActionToolbar 的狀態更新。
        processTable.getSelectionModel().addListSelectionListener(e -> {
            // 注意：由於 ActionToolbar 的 targetComponent 已設置為 processTable，
            // IntelliJ 的 ActionSystem 會自動處理大部分狀態更新。
            // 因此，此處通常不需要編寫額外的代碼來手動更新 killProcessAction 的狀態。
            // Action 的 update 方法會被自動調用。
        });

        // 為進程表格 (processTable) 添加 MouseListener，以處理雙擊事件。
        processTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                // 判斷是否是滑鼠雙擊事件。
                if (e.getClickCount() == 2) {
                    int selectedRow = processTable.getSelectedRow(); // 獲取雙擊發生的行。
                    // 檢查是否有行被選中，並且該行是有效的數據行。
                    if (selectedRow != -1 && selectedRow < tableModel.getRowCount()) {
                        Object pidObj = tableModel.getValueAt(selectedRow, 0); // 獲取該行的 PID。
                        // 檢查該行的 PID 是否是有效的、可終止的進程標識。
                        if (pidObj instanceof String && !"-".equals(pidObj)) {
                            LOG.info("Double-click detected on killable process row, triggering kill action."); // 記錄雙擊事件（英文）。
                            killSelectedProcessAction(); // 觸發終止進程的操作。
                        } else {
                            // 如果雙擊在無效行或 PID 為 "-" 的行，記錄調試信息（英文）。
                            LOG.debug("Double-click on non-killable row (PID: " + pidObj + "), ignoring.");
                        }
                    } else {
                        // 如果雙擊發生在表格空白區域或無效行，記錄調試信息（英文）。
                        LOG.debug("Double-click outside valid rows or no selection, ignoring.");
                    }
                }
            }
        });
    }


    // --- 主要業務邏輯方法 ---

    /**
     * 根據輸入框中的埠號，執行查找相關進程的操作。
     */
    private void findProcessesAction() {
        // 從埠口輸入框獲取文本，並去除首尾空白字符。
        String portText = portInputField.getText().trim();

        // 驗證輸入是否為空。
        if (portText.isEmpty()) {
            // 如果為空，顯示警告對話框（英文）。
            Messages.showWarningDialog(toolWindow.getComponent(), "Please enter a port number.", "Input Required");
            // 更新表格的空狀態提示文字（英文）。
            processTable.getEmptyText().setText("Please enter a port number and click 'Find'. Double-click a process to terminate.");
            tableModel.setRowCount(0); // 清空表格現有數據。
            return; // 中止操作。
        }

        int port;
        try {
            // 嘗試將輸入的文本解析為整數。
            port = Integer.parseInt(portText);
            // 驗證埠號是否在有效範圍 (1-65535) 內。
            if (port <= 0 || port > 65535) {
                // 如果無效，顯示錯誤對話框（英文）。
                Messages.showErrorDialog(toolWindow.getComponent(), "Port number must be between 1 and 65535.", "Invalid Port");
                // 更新表格空狀態提示（英文）。
                processTable.getEmptyText().setText("Invalid port number (1-65535). Double-click a process to terminate.");
                tableModel.setRowCount(0);
                return;
            }
        } catch (NumberFormatException ex) {
            // 如果輸入的不是有效的數字格式。
            // 顯示錯誤對話框（英文）。
            Messages.showErrorDialog(toolWindow.getComponent(), "Please enter a valid number.", "Invalid Input");
            // 更新表格空狀態提示（英文）。
            processTable.getEmptyText().setText("Invalid port number format. Double-click a process to terminate.");
            tableModel.setRowCount(0);
            return;
        }

        // 將驗證通過的埠號轉換回字串形式，用於後續處理和顯示。
        final String finalPortText = String.valueOf(port);

        // 切換到 UI 線程 (EDT) 來更新界面狀態。
        ApplicationManager.getApplication().invokeLater(() -> {
            tableModel.setRowCount(0); // 清空表格。
            // 更新表格空狀態提示，顯示正在搜索（英文）。
            processTable.getEmptyText().setText("Searching for processes on port " + finalPortText + "...");
        });

        // 使用 IntelliJ 的 ProgressManager 在後台線程執行耗時的埠口查詢操作。
        // Task.Backgroundable 會在狀態欄顯示進度。
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Searching Processes on Port " + port, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                // 將進度指示器設置為不確定模式（旋轉動畫）。
                indicator.setIndeterminate(true);
                try {
                    // 調用 PortService 中的方法實際執行查詢。
                    List<PortProcessInfo> processes = portService.findProcessesOnPort(Integer.parseInt(finalPortText));

                    // 查詢完成後，回到 UI 線程更新表格內容。
                    ApplicationManager.getApplication().invokeLater(() -> {
                        tableModel.setRowCount(0); // 再次清空，以防萬一。
                        if (processes.isEmpty()) {
                            // 如果未找到任何進程。
                            // 更新表格空狀態提示（英文）。
                            processTable.getEmptyText().setText("No processes found listening on port " + finalPortText + ". Double-click a process to terminate.");
                        } else {
                            // 如果找到了進程。
                            // 更新表格空狀態提示，移除搜索提示，顯示通用操作提示（英文）。
                            processTable.getEmptyText().setText("Double-click a process in the table to try terminating it.");
                            // 將查詢到的每個進程信息添加到表格模型中。
                            processes.forEach(info -> tableModel.addRow(new Object[]{info.getPid(), info.getCommand(), info.getPort()}));
                            // 如果表格中有數據，自動選中第一行。
                            if (tableModel.getRowCount() > 0) {
                                processTable.setRowSelectionInterval(0, 0);
                            }
                        }
                    });
                } catch (Exception ex) {
                    // 如果在查詢過程中發生異常。
                    LOG.error("Error searching for processes on port " + finalPortText, ex); // 記錄錯誤日誌（英文）。
                    // 獲取錯誤消息，如果為 null 則使用通用錯誤提示（英文）。
                    final String errorMessage = ex.getMessage() != null ? ex.getMessage() : "Unknown error";
                    // 回到 UI 線程顯示錯誤。
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // 顯示錯誤對話框（英文）。
                        Messages.showErrorDialog(toolWindow.getComponent(), "Error finding processes: " + errorMessage, "Search Error");
                        // 在表格空狀態區域顯示錯誤信息（英文）。
                        processTable.getEmptyText().setText("Error during search: " + errorMessage + ". Double-click a process to terminate.");
                    });
                }
            }
        });
    }

    /**
     * 執行終止選定進程的操作。
     * 可由 "Kill Process" 按鈕或雙擊表格行觸發。
     */
    private void killSelectedProcessAction() {
        LOG.info("Kill Process action initiated."); // 記錄操作開始（英文）。
        int selectedRow = processTable.getSelectedRow(); // 獲取當前選中的行。
        if (selectedRow == -1) return; // 如果沒有選中行，則直接返回。

        Object pidObj = tableModel.getValueAt(selectedRow, 0); // 獲取選中行的 PID。
        // 再次驗證 PID 是否有效。
        if (!(pidObj instanceof String) || "-".equals(pidObj)) return;

        final String pid = (String) pidObj; // 確定要終止的 PID。
        String command = tableModel.getValueAt(selectedRow, 1).toString(); // 獲取進程命令。
        String port = tableModel.getValueAt(selectedRow, 2).toString();   // 獲取相關埠號。

        // 彈出確認對話框，詢問用戶是否確定要終止進程（英文）。
        int confirmation = Messages.showYesNoDialog(
                toolWindow.getComponent(), // 指定父組件，確保對話框模態行為正確。
                "Are you sure you want to kill PID: " + pid + " (" + command + ") on port " + port + "?",
                "Confirm Kill", // 對話框標題（英文）。
                Messages.getWarningIcon() // 顯示警告圖標。
        );

        // 如果用戶選擇的不是 "Yes"，則取消操作。
        if (confirmation != Messages.YES) {
            LOG.debug("User cancelled the kill operation."); // 記錄用戶取消（英文）。
            return;
        }

        // 使用 ProgressManager 在模態任務中執行終止操作。
        // Task.Modal 會阻塞部分 UI，並顯示一個帶進度條的對話框。
        ProgressManager.getInstance().run(new Task.Modal(project, "Killing Process " + pid, false) {
            boolean success = false; // 標記終止操作是否成功。
            String errMsg = null;    // 用於儲存執行過程中可能出現的錯誤信息。

            // 在後台線程中執行實際的進程終止邏輯。
            @Override
            public void run(@NotNull ProgressIndicator i) {
                i.setIndeterminate(true); // 設置進度指示器為不確定模式。
                try {
                    // 調用 PortService 執行終止。
                    success = portService.killProcess(pid);
                } catch (Exception e) {
                    // 如果發生異常，記錄錯誤並保存錯誤信息。
                    LOG.error("Exception occurred while killing PID " + pid, e);
                    errMsg = e.getMessage();
                }
            }

            // 當後台任務成功完成時（無論 killProcess 是否成功），在 UI 線程執行。
            @Override
            public void onSuccess() {
                super.onSuccess();
                if (success) {
                    // 如果 PortService 返回 true，表示終止成功。
                    // 顯示成功通知（英文）。
                    showNotification("PID: " + pid + " terminated successfully.", NotificationType.INFORMATION);
                    LOG.info("PID: " + pid + " terminated."); // 記錄成功日誌（英文）。
                    // 在 UI 線程中更新表格，移除已終止的進程行。
                    ApplicationManager.getApplication().invokeLater(() -> {
                        int r = -1; // 查找要移除的行的索引。
                        for (int i = 0; i < tableModel.getRowCount(); i++) {
                            if (pid.equals(tableModel.getValueAt(i, 0))) {
                                r = i;
                                break;
                            }
                        }
                        if (r != -1) { // 如果找到了對應的行。
                            tableModel.removeRow(r); // 從表格模型中移除該行。
                        }
                        // 如果移除後表格變空，更新空狀態提示（英文）。
                        if (tableModel.getRowCount() == 0) {
                            processTable.getEmptyText().setText("No running processes found. Double-click a process to terminate.");
                        }
                    });
                } else {
                    // 如果 PortService 返回 false 或發生異常。
                    LOG.warn("Failed to kill PID: " + pid + (errMsg != null ? " Error: " + errMsg : "")); // 記錄失敗日誌（英文）。
                    // 顯示失敗的錯誤對話框（英文）。
                    Messages.showErrorDialog(toolWindow.getComponent(), "Failed to kill PID: " + pid + (errMsg != null ? "\nError: " + errMsg : ". Check permissions or if the process still exists."), "Kill Failed");
                }
            }

            // 當後台任務執行過程中拋出未被捕獲的異常時，在 UI 線程執行。
            @Override
            public void onThrowable(@NotNull Throwable t) {
                super.onThrowable(t);
                LOG.error("Unexpected error occurred while killing process", t); // 記錄意外錯誤（英文）。
                // 顯示通用的錯誤對話框（英文）。
                Messages.showErrorDialog(toolWindow.getComponent(), "Error killing process: " + t.getMessage(), "Error");
            }
        });
    }

    /**
     * 顯示用於管理常用埠口的設置對話框。
     * 允許用戶新增、刪除和排序常用埠口。
     */
    private void showFavoritePortsDialog() {
        // --- 創建對話框的根面板 ---
        JPanel panel = new JBPanel<>(new BorderLayout(0, JBUI.scale(8)));
        panel.setBorder(JBUI.Borders.empty(10)); // 設置面板內邊距。
        panel.setPreferredSize(new Dimension(JBUI.scale(350), JBUI.scale(400))); // 設置對話框的建議大小。

        // --- 創建頂部的標題和說明文字 ---
        JPanel headerPanel = new JBPanel<>(new VerticalLayout(JBUI.scale(5)));
        // 標題 "Favorite Ports Settings"（英文）。
        JLabel titleLabel = new JBLabel("Favorite Ports Settings", UIUtil.ComponentStyle.LARGE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD)); // 設置為粗體。
        // 說明文字（英文）。
        JLabel descLabel = new JBLabel("Drag and drop to reorder. Use buttons to add/remove.", UIUtil.ComponentStyle.SMALL);
        descLabel.setForeground(UIUtil.getContextHelpForeground()); // 使用標準的輔助文字顏色。
        headerPanel.add(titleLabel);
        headerPanel.add(descLabel);

        // --- 創建中間的埠口列表 ---
        DefaultListModel<String> portListModel = new DefaultListModel<>(); // 創建列表的數據模型。
        favoritePorts.forEach(portListModel::addElement); // 將當前的常用埠口填充到模型中。
        JList<String> portList = new JList<>(portListModel); // 創建 JList 組件。
        portList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // 設置為單選模式。
        portList.setCellRenderer(new PortListCellRenderer()); // 使用自定義渲染器顯示列表項。
        portList.setDragEnabled(true);                        // 啟用列表項的拖放功能。
        portList.setDropMode(DropMode.INSERT);                // 設置拖放行為為插入模式。
        portList.setTransferHandler(new PortListTransferHandler(portListModel)); // 設置處理拖放數據傳輸的 Handler。
        JBScrollPane scrollPane = new JBScrollPane(portList); // 將列表放入滾動面板。
        scrollPane.setBorder(IdeBorderFactory.createBorder(SideBorder.ALL)); // 為滾動面板設置邊框。

        // --- 創建底部的操作按鈕 ---
        JPanel actionPanel = new JBPanel<>(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0));
        actionPanel.setBorder(JBUI.Borders.emptyTop(5)); // 在按鈕面板頂部添加外邊距。
        // 定義按鈕的標準大小和內邊距。
        Dimension textButtonSize = new Dimension(JBUI.scale(80), JBUI.scale(28));
        Insets buttonMargin = JBUI.insets(0, 5, 0, 5);

        // 創建 "Add" 按鈕。
        JButton addButton = new JButton("Add", AllIcons.General.Add);
        styleActionButton(addButton, textButtonSize, buttonMargin, "Add new favorite port"); // 設置樣式和提示（英文）。

        // 創建 "Remove" 按鈕。
        JButton deleteButton = new JButton("Remove", AllIcons.General.Remove);
        styleActionButton(deleteButton, textButtonSize, buttonMargin, "Remove selected favorite port"); // 設置樣式和提示（英文）。
        deleteButton.setEnabled(false); // 初始狀態下禁用移除按鈕，因為列表未選中任何項。

        // 定義一個 Runnable，用於根據列表選擇狀態更新按鈕（主要是移除按鈕）的啟用狀態。
        Runnable updateButtonStates = () -> {
            int i = portList.getSelectedIndex(); // 獲取選中項的索引。
            deleteButton.setEnabled(i != -1); // 如果有選中項 (i != -1)，則啟用移除按鈕。
        };

        // 為 "Add" 按鈕添加事件監聽器。
        addButton.addActionListener(e -> {
            // 彈出輸入對話框，讓用戶輸入新的埠號（英文提示）。
            String input = Messages.showInputDialog(panel, "Enter port number (1-65535):", "Add Favorite Port", null);
            if (input != null && !input.trim().isEmpty()) { // 驗證輸入是否有效。
                try {
                    int p = Integer.parseInt(input.trim()); // 解析輸入。
                    if (p > 0 && p <= 65535) { // 驗證埠號範圍。
                        String pt = input.trim();
                        if (!portListModel.contains(pt)) { // 檢查是否已存在。
                            portListModel.addElement(pt); // 添加到列表模型。
                            portList.setSelectedIndex(portListModel.getSize() - 1); // 自動選中新添加的項。
                            portList.ensureIndexIsVisible(portListModel.getSize() - 1); // 確保新項在視圖中可見。
                        } else {
                            // 如果已存在，顯示提示信息（英文）。
                            Messages.showInfoMessage(panel, "This port already exists.", "Duplicate Port");
                        }
                    } else {
                        // 如果範圍無效，顯示錯誤信息（英文）。
                        Messages.showErrorDialog(panel, "Port number must be between 1 and 65535.", "Range Error");
                    }
                } catch (NumberFormatException ex) {
                    // 如果格式無效，顯示錯誤信息（英文）。
                    Messages.showErrorDialog(panel, "Please enter a valid number.", "Format Error");
                }
            }
            updateButtonStates.run(); // 更新按鈕狀態。
        });

        // 為 "Remove" 按鈕添加事件監聽器。
        deleteButton.addActionListener(e -> {
            int i = portList.getSelectedIndex(); // 獲取選中項索引。
            if (i != -1) { // 如果有選中項。
                portListModel.remove(i); // 從模型中移除。
                // 如果移除後列表仍有項目，嘗試選中一個鄰近的項目。
                if (portListModel.getSize() > 0) {
                    portList.setSelectedIndex(Math.min(i, portListModel.getSize() - 1));
                }
            }
            updateButtonStates.run(); // 更新按鈕狀態。
        });

        // 為列表添加選擇監聽器，以便在選擇變化時更新按鈕狀態。
        portList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) { // 確保在選擇穩定後更新。
                updateButtonStates.run();
            }
        });

        // 將 "Add" 和 "Remove" 按鈕添加到按鈕面板。
        actionPanel.add(addButton);
        actionPanel.add(deleteButton);

        // --- 將所有子面板組裝到對話框的根面板中 ---
        panel.add(headerPanel, BorderLayout.NORTH); // 頂部說明。
        panel.add(scrollPane, BorderLayout.CENTER); // 中間列表。
        panel.add(actionPanel, BorderLayout.SOUTH); // 底部按鈕。

        // --- 顯示標準的 IntelliJ 選項對話框 ---
        int result = JOptionPane.showOptionDialog(
                toolWindow.getComponent(),   // 指定父組件。
                panel,                       // 對話框顯示的內容面板。
                "Favorite Ports Settings",   // 對話框標題（英文）。
                JOptionPane.OK_CANCEL_OPTION, // 提供 "OK" 和 "Cancel" 選項。
                JOptionPane.PLAIN_MESSAGE,    // 不顯示額外的圖標。
                null,                         // 不使用自定義圖標。
                new String[]{"Save", "Cancel"}, // 按鈕上的文字（英文）。
                "Save"                         // 預設選中的按鈕（英文）。
        );

        // 根據用戶的選擇處理結果。
        if (result == JOptionPane.OK_OPTION) { // 如果用戶點擊了 "Save"。
            LOG.info("Saving favorite ports settings..."); // 記錄保存操作（英文）。
            favoritePorts.clear(); // 清空記憶體中的舊列表。
            // 從對話框的列表模型中讀取最終的埠口順序和內容。
            for (int i = 0; i < portListModel.getSize(); i++) {
                favoritePorts.add(portListModel.get(i));
            }
            saveFavoritePorts();       // 將更新後的列表持久化保存。
            updateFavoritePortsList(); // 更新主界面左側的常用埠口列表顯示。
        } else { // 如果用戶點擊了 "Cancel" 或關閉了對話框。
            LOG.debug("User cancelled favorite ports settings."); // 記錄取消操作（英文）。
        }
    }

    /**
     * 輔助方法，用於統一設置對話框中按鈕的樣式。
     *
     * @param button  目標 JButton。
     * @param size    按鈕的首選尺寸。
     * @param margin  按鈕的內邊距。
     * @param tooltip 按鈕的滑鼠懸停提示文字。
     */
    private void styleActionButton(JButton button, Dimension size, Insets margin, String tooltip) {
        button.setPreferredSize(size);     // 設置按鈕大小。
        button.setMargin(margin);          // 設置按鈕內邊距。
        button.setFocusPainted(false);     // 移除焦點邊框。
        button.setToolTipText(tooltip);    // 設置提示文字。
    }

    // --- 加載/保存/通知/獲取內容 的輔助方法 ---

    /**
     * 從 IntelliJ 的 PropertiesComponent 加載之前保存的常用埠口列表。
     */
    private void loadFavoritePorts() {
        // 嘗試根據 FAVORITE_PORTS_KEY 讀取值，如果不存在則返回空字串。
        String portsStr = propertiesComponent.getValue(FAVORITE_PORTS_KEY, "");
        favoritePorts.clear(); // 清空當前記憶體中的列表。
        if (!portsStr.isEmpty()) { // 如果讀取到的值非空。
            // 按逗號分割字串，並將結果添加到 favoritePorts 列表中。
            favoritePorts.addAll(Arrays.asList(portsStr.split(",")));
        }
        // 記錄加載操作和數量（英文）。
        LOG.info("Loaded " + favoritePorts.size() + " favorite ports.");
    }

    /**
     * 將當前記憶體中的常用埠口列表 (favoritePorts) 保存到 PropertiesComponent。
     */
    private void saveFavoritePorts() {
        // 使用逗號將 favoritePorts 列表中的所有元素連接成一個字串。
        // 將這個字串以 FAVORITE_PORTS_KEY 為鍵，保存到 PropertiesComponent。
        propertiesComponent.setValue(FAVORITE_PORTS_KEY, String.join(",", favoritePorts));
        // 記錄保存操作和數量（英文）。
        LOG.info("Saved " + favoritePorts.size() + " favorite ports.");
    }

    /**
     * 顯示一個 IntelliJ 風格的通知彈窗。
     *
     * @param content 通知的內容文字。
     * @param type    通知的類型（例如：信息、警告、錯誤）。
     */
    private void showNotification(String content, NotificationType type) {
        // 記錄將要顯示的通知（英文）。
        LOG.info("Showing notification: [" + type + "] " + content);
        // 創建 Notification 對象，指定通知組 ID、標題（英文）和內容、類型。
        Notification notification = new Notification(NOTIFICATION_GROUP_ID, "Port Manager", content, type);
        // 使用 IntelliJ 的通知總線 (Notifications.Bus) 發送通知。
        Notifications.Bus.notify(notification, project);
    }

    /**
     * 返回此工具視窗的根 UI 組件。
     * 此方法通常由 ToolWindowFactory 調用，以將 UI 添加到 IntelliJ 界面。
     *
     * @return 工具視窗的根 JPanel。
     */
    public JPanel getContent() {
        return toolWindowContent;
    }

    // --- 內部類別定義 (渲染器, TransferHandler) ---

    /**
     * 用於渲染左側常用埠口列表 (favoritePortsList) 中每個列表項的自定義渲染器。
     * 主要功能是為每個列表項添加一個書籤圖標，並調整邊距。
     */
    private static class FavoritePortListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            // 調用父類的實現以獲取基本的 JLabel 組件和默認樣式。
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            // 設置列表項的內邊距。
            l.setBorder(JBUI.Borders.empty(4, 8));
            // 為列表項設置書籤圖標。
            l.setIcon(AllIcons.Nodes.Bookmark);
            // 設置圖標與文字之間的間距。
            l.setIconTextGap(JBUI.scale(4));
            return l;
        }
    }

    /**
     * 用於渲染常用埠口設置對話框中列表 (portList) 的自定義渲染器。
     * 主要功能是為每個列表項添加一個底部的分隔線，並設置邊距。
     */
    private static class PortListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            // 調用父類實現獲取基礎 JLabel。
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            // 設置一個複合邊框：
            // 外部邊框是一個只有底部的 1 像素線條（顏色與主題邊框一致）。
            // 內部邊框是提供上下左右內邊距的空邊框。
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
                    JBUI.Borders.empty(5, 10)
            ));
            return l;
        }
    }

    /**
     * 用於渲染右側進程表格 (processTable) 中單元格的自定義渲染器。
     * 負責處理單元格的背景色（斑馬紋）、前景色、邊框以及 PID 列的特殊樣式。
     */
    private static class ProcessTableCellRenderer extends DefaultTableCellRenderer {
        // 定義普通單元格使用的邊框（提供內邊距）。
        private final Border normalBorder = JBUI.Borders.empty(4, 6);
        // 定義 PID 列使用的特殊邊框（在右側添加一條線，並提供內邊距）。
        private final Border pidBorder = JBUI.Borders.compound(
                JBUI.Borders.customLine(JBColor.border(), 0, 0, 0, 1),
                JBUI.Borders.empty(4, 6)
        );

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean isSel, boolean hasFoc, int r, int c) {
            // 調用父類實現獲取用於渲染的基礎組件（通常是 JLabel）。
            Component comp = super.getTableCellRendererComponent(t, v, isSel, hasFoc, r, c);
            if (comp instanceof JLabel) { // 確保組件是 JLabel。
                JLabel l = (JLabel) comp;
                l.setText(v != null ? v.toString() : ""); // 設置單元格顯示的文本。
                l.setBorder(normalBorder); // 預設應用普通邊框。

                // 根據是否選中來設置背景色和前景色。
                if (!isSel) { // 如果未選中。
                    // 應用斑馬紋背景色（奇偶行不同）。
                    l.setBackground(UIUtil.getTableBackground(r % 2 != 0));
                    // 使用標準表格前景色。
                    l.setForeground(UIUtil.getTableForeground());
                } else { // 如果已選中。
                    // 使用表格的選中背景色和前景色。
                    l.setBackground(t.getSelectionBackground());
                    l.setForeground(t.getSelectionForeground());
                }

                // 對第一列（PID 列）進行特殊樣式處理。
                if (c == 0) {
                    l.setHorizontalAlignment(LEFT); // 左對齊。
                    l.setBorder(pidBorder);        // 應用 PID 特殊邊框。
                    l.setFont(t.getFont().deriveFont(Font.BOLD)); // 設置為粗體。
                    // 如果未選中，為 PID 列設置一個稍微不同的背景色以突出顯示。
                    if (!isSel) {
                        // 使用 JBColor 確保顏色在淺色和深色主題下都能適應。
                        l.setBackground(new JBColor(new Color(245, 247, 250), new Color(55, 58, 62)));
                    }
                } else { // 對於其他列。
                    l.setHorizontalAlignment(LEFT); // 左對齊。
                    l.setFont(t.getFont());        // 使用表格的預設字體。
                }
            }
            return comp;
        }
    }

    /**
     * 自定義 TransferHandler，用於處理常用埠口設置對話框中 JList 的拖放排序功能。
     */
    private static class PortListTransferHandler extends TransferHandler {
        // 定義一個 DataFlavor，用於標識正在傳輸的數據是列表項的索引（Integer 類型）。
        private final DataFlavor indexFlavor = new DataFlavor(Integer.class, "Index");
        // 持有對列表數據模型的引用，以便在拖放操作中直接修改模型。
        private final DefaultListModel<String> listModel;
        // 用於記錄拖動操作開始時，被拖動項的原始索引。
        private int sourceIndex = -1;

        /**
         * 構造函數，接收列表數據模型作為參數。
         *
         * @param m 列表數據模型 (DefaultListModel<String>)。
         */
        public PortListTransferHandler(DefaultListModel<String> m) {
            this.listModel = m;
        }

        /**
         * 指定此 TransferHandler 支持的源操作類型。
         * 這裡只支持 MOVE（移動）操作。
         */
        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        /**
         * 當拖動開始時，創建包含要傳輸數據的 Transferable 對象。
         */
        @Override
        protected Transferable createTransferable(JComponent c) {
            if (c instanceof JList) { // 確保源組件是 JList。
                sourceIndex = ((JList<?>) c).getSelectedIndex(); // 獲取並記錄被拖動項的索引。
                if (sourceIndex != -1) { // 確保有選中的項。
                    // 返回一個匿名的 Transferable 實現。
                    return new Transferable() {
                        // 返回支持的 DataFlavor 數組（只包含我們定義的 indexFlavor）。
                        @Override
                        public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[]{indexFlavor};
                        }
                        // 判斷給定的 DataFlavor 是否受支持。
                        @Override
                        public boolean isDataFlavorSupported(DataFlavor f) {
                            return f.equals(indexFlavor);
                        }
                        // 實際獲取傳輸數據的方法。
                        @NotNull
                        @Override
                        public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
                            if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
                            return sourceIndex; // 返回記錄的源索引。
                        }
                    };
                }
            }
            return null; // 如果無法創建 Transferable，返回 null。
        }

        /**
         * 判斷當前的拖放操作是否可以將數據導入（放置）到目標組件的特定位置。
         */
        @Override
        public boolean canImport(TransferSupport s) {
            // 必須是放置操作 (isDrop)，並且數據類型是我們支持的 indexFlavor。
            if (!s.isDrop() || !s.isDataFlavorSupported(indexFlavor)) return false;
            // 獲取放置位置信息。
            JList.DropLocation dl = (JList.DropLocation) s.getDropLocation();
            // 放置的目標索引不能與拖動的源索引相同（即必須有實際移動）。
            return dl.getIndex() != sourceIndex;
        }

        /**
         * 執行實際的數據導入（放置）操作。
         */
        @Override
        public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false; // 再次驗證是否可以導入。

            int draggedIdx; // 用於儲存從 Transferable 中獲取的被拖動項的原始索引。
            try {
                // 從 Transferable 對象中提取數據（即源索引）。
                draggedIdx = (Integer) s.getTransferable().getTransferData(indexFlavor);
            } catch (UnsupportedFlavorException | IOException e) {
                // 如果提取數據失敗，記錄錯誤並返回 false。
                LOG.error("Failed data transfer during drag and drop", e);
                return false;
            }

            // 獲取目標放置位置的索引。
            JList.DropLocation dl = (JList.DropLocation) s.getDropLocation();
            int dropIdx = dl.getIndex();

            try {
                // 從列表模型中獲取被拖動的元素。
                String element = listModel.getElementAt(draggedIdx);
                // 從模型的原始位置移除該元素。
                listModel.remove(draggedIdx);
                // 在目標放置索引處插入該元素。
                listModel.add(dropIdx, element);

                // 更新 JList 的 UI 狀態。
                if (s.getComponent() instanceof JList) {
                    ((JList<?>) s.getComponent()).setSelectedIndex(dropIdx); // 將新位置設置為選中狀態。
                    ((JList<?>) s.getComponent()).ensureIndexIsVisible(dropIdx); // 確保新位置在滾動視圖中可見。
                }
                return true; // 導入操作成功。
            } catch (Exception e) {
                // 如果在模型操作過程中發生異常，記錄錯誤並返回 false。
                LOG.error("Error moving list element during drag and drop", e);
                return false;
            }
        }

        /**
         * 當拖放操作完成（無論成功與否）時，在源組件上調用此方法。
         */
        @Override
        protected void exportDone(JComponent src, Transferable data, int action) {
            // 清理記錄的源索引，為下一次拖放做準備。
            sourceIndex = -1;
        }
    }
}