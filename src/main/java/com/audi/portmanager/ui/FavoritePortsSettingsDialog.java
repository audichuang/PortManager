package com.audi.portmanager.ui;

import com.audi.portmanager.service.FavoritePortsService;
import com.audi.portmanager.service.FavoritePortsService.FavoritePort;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 現代化的常用埠口設定對話框
 * <p>
 * 使用 IntelliJ DialogWrapper 和 ToolbarDecorator 提供專業的 IDE 風格 UI。
 * 功能包括：
 * <ul>
 * <li>美觀的列表渲染（Port 粗體 + Label 淡色）</li>
 * <li>雙擊編輯</li>
 * <li>右鍵選單（Edit / Delete / Move）</li>
 * <li>拖放排序</li>
 * <li>工具列按鈕（Add / Remove / Move Up / Move Down）</li>
 * </ul>
 */
public class FavoritePortsSettingsDialog extends DialogWrapper {

    private static final Logger LOG = Logger.getInstance(FavoritePortsSettingsDialog.class);

    // --- UI 組件 ---
    private JBList<FavoritePort> portList;
    private DefaultListModel<FavoritePort> listModel;

    // --- 資料 ---
    private final List<FavoritePort> originalFavorites;
    private boolean modified = false;

    /**
     * 建立設定對話框
     *
     * @param parent 父組件
     */
    public FavoritePortsSettingsDialog(@Nullable Component parent) {
        super(parent, true);
        this.originalFavorites = FavoritePortsService.getInstance().getFavorites();
        init();
        setTitle("Favorite Ports Settings");
        setSize(JBUI.scale(420), JBUI.scale(500));
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(0, JBUI.scale(10)));
        mainPanel.setBorder(JBUI.Borders.empty(5));

        // === 頂部說明區域 ===
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // === 中央列表區域 ===
        JPanel listPanel = createListPanel();
        mainPanel.add(listPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    /**
     * 建立頂部說明面板
     */
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(JBUI.scale(8), 0));
        panel.setBorder(JBUI.Borders.emptyBottom(8));

        // 圖標
        JBLabel iconLabel = new JBLabel(AllIcons.General.Settings);
        panel.add(iconLabel, BorderLayout.WEST);

        // 說明文字
        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, JBUI.scale(2)));
        textPanel.setOpaque(false);

        JBLabel titleLabel = new JBLabel("Manage Favorite Ports");
        titleLabel.setFont(JBUI.Fonts.label().asBold());
        textPanel.add(titleLabel);

        JBLabel descLabel = new JBLabel("Double-click to edit. Drag to reorder.");
        descLabel.setFont(JBUI.Fonts.smallFont());
        descLabel.setForeground(UIUtil.getContextHelpForeground());
        textPanel.add(descLabel);

        panel.add(textPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * 建立列表面板（含工具列）
     */
    private JPanel createListPanel() {
        // 初始化列表模型
        listModel = new DefaultListModel<>();
        for (FavoritePort fav : originalFavorites) {
            listModel.addElement(new FavoritePort(fav.port, fav.label));
        }

        // 初始化列表
        portList = new JBList<>(listModel);
        portList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        portList.setCellRenderer(new FavoritePortCellRenderer());
        portList.setDragEnabled(true);
        portList.setDropMode(DropMode.INSERT);
        portList.setTransferHandler(new FavoritePortTransferHandler());

        // 雙擊編輯
        portList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && portList.getSelectedIndex() != -1) {
                    editSelectedPort();
                }
            }
        });

        // 使用 ToolbarDecorator 建立帶工具列的列表
        ToolbarDecorator decorator = ToolbarDecorator.createDecorator(portList)
                .setAddAction(button -> addNewPort())
                .setRemoveAction(button -> removeSelectedPort())
                .setEditAction(button -> editSelectedPort())
                .setMoveUpAction(button -> moveSelectedPort(-1))
                .setMoveDownAction(button -> moveSelectedPort(1))
                .setAddActionName("Add Favorite Port")
                .setRemoveActionName("Remove Selected Port")
                .setEditActionName("Edit Selected Port")
                .setMoveUpActionName("Move Up")
                .setMoveDownActionName("Move Down");

        JPanel decoratedPanel = decorator.createPanel();
        decoratedPanel.setBorder(IdeBorderFactory.createTitledBorder("Ports", false, JBUI.insets(5, 0)));

        // 設定右鍵選單
        setupContextMenu();

        return decoratedPanel;
    }

    /**
     * 設定右鍵選單
     */
    private void setupContextMenu() {
        DefaultActionGroup contextMenuGroup = new DefaultActionGroup();

        // Edit 動作
        contextMenuGroup.add(new AnAction("Edit", "Edit selected port", AllIcons.Actions.Edit) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                editSelectedPort();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(portList.getSelectedIndex() != -1);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });

        // Delete 動作
        contextMenuGroup.add(new AnAction("Delete", "Delete selected port", AllIcons.General.Remove) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                removeSelectedPort();
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(portList.getSelectedIndex() != -1);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });

        contextMenuGroup.addSeparator();

        // Move Up 動作
        contextMenuGroup.add(new AnAction("Move Up", "Move port up", AllIcons.Actions.MoveUp) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                moveSelectedPort(-1);
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(portList.getSelectedIndex() > 0);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });

        // Move Down 動作
        contextMenuGroup.add(new AnAction("Move Down", "Move port down", AllIcons.Actions.MoveDown) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e) {
                moveSelectedPort(1);
            }

            @Override
            public void update(@NotNull AnActionEvent e) {
                e.getPresentation().setEnabled(
                        portList.getSelectedIndex() != -1 &&
                                portList.getSelectedIndex() < listModel.getSize() - 1);
            }

            @Override
            public @NotNull ActionUpdateThread getActionUpdateThread() {
                return ActionUpdateThread.EDT;
            }
        });

        // 綁定右鍵選單
        portList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    // 選中右鍵點擊的項目
                    int index = portList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        portList.setSelectedIndex(index);
                    }
                    // 顯示選單
                    ActionPopupMenu popupMenu = ActionManager.getInstance()
                            .createActionPopupMenu(ActionPlaces.POPUP, contextMenuGroup);
                    popupMenu.getComponent().show(portList, e.getX(), e.getY());
                }
            }
        });
    }

    // === 操作方法 ===

    /**
     * 新增 Port
     */
    private void addNewPort() {
        Set<String> existingPorts = getExistingPortNumbers();
        FavoritePortEditDialog dialog = new FavoritePortEditDialog(getContentPane(), existingPorts);

        if (dialog.showAndGet()) {
            FavoritePort newPort = dialog.getResult();
            listModel.addElement(newPort);
            portList.setSelectedIndex(listModel.getSize() - 1);
            portList.ensureIndexIsVisible(listModel.getSize() - 1);
            modified = true;
            LOG.info("Added new favorite port: " + newPort.getDisplayText());
        }
    }

    /**
     * 編輯選中的 Port
     */
    private void editSelectedPort() {
        int selectedIndex = portList.getSelectedIndex();
        if (selectedIndex == -1)
            return;

        FavoritePort current = listModel.getElementAt(selectedIndex);
        Set<String> existingPorts = getExistingPortNumbers();
        existingPorts.remove(current.port); // 排除當前編輯的 Port

        FavoritePortEditDialog dialog = new FavoritePortEditDialog(getContentPane(), current, existingPorts);

        if (dialog.showAndGet()) {
            FavoritePort updated = dialog.getResult();
            listModel.setElementAt(updated, selectedIndex);
            modified = true;
            LOG.info("Updated favorite port: " + updated.getDisplayText());
        }
    }

    /**
     * 移除選中的 Port
     */
    private void removeSelectedPort() {
        int selectedIndex = portList.getSelectedIndex();
        if (selectedIndex == -1)
            return;

        FavoritePort toRemove = listModel.getElementAt(selectedIndex);
        int confirm = Messages.showYesNoDialog(
                getContentPane(),
                "Remove port " + toRemove.getDisplayText() + "?",
                "Confirm Removal",
                Messages.getQuestionIcon());

        if (confirm == Messages.YES) {
            listModel.remove(selectedIndex);
            if (listModel.getSize() > 0) {
                portList.setSelectedIndex(Math.min(selectedIndex, listModel.getSize() - 1));
            }
            modified = true;
            LOG.info("Removed favorite port: " + toRemove.getDisplayText());
        }
    }

    /**
     * 移動選中的 Port
     *
     * @param direction -1 表示向上，1 表示向下
     */
    private void moveSelectedPort(int direction) {
        int selectedIndex = portList.getSelectedIndex();
        if (selectedIndex == -1)
            return;

        int targetIndex = selectedIndex + direction;
        if (targetIndex < 0 || targetIndex >= listModel.getSize())
            return;

        FavoritePort item = listModel.remove(selectedIndex);
        listModel.add(targetIndex, item);
        portList.setSelectedIndex(targetIndex);
        modified = true;
    }

    /**
     * 取得現有 Port 號碼集合
     */
    private Set<String> getExistingPortNumbers() {
        Set<String> ports = new HashSet<>();
        for (int i = 0; i < listModel.getSize(); i++) {
            ports.add(listModel.getElementAt(i).port);
        }
        return ports;
    }

    // === 對話框按鈕 ===

    @Override
    protected @NotNull Action @NotNull [] createActions() {
        return new Action[] { getOKAction(), getCancelAction() };
    }

    @Override
    protected void doOKAction() {
        // 保存變更到 Service
        List<FavoritePort> newFavorites = new ArrayList<>();
        for (int i = 0; i < listModel.getSize(); i++) {
            newFavorites.add(listModel.getElementAt(i));
        }
        FavoritePortsService.getInstance().setFavorites(newFavorites);
        LOG.info("Saved " + newFavorites.size() + " favorite ports.");
        super.doOKAction();
    }

    @Override
    public void doCancelAction() {
        if (modified) {
            int confirm = Messages.showYesNoDialog(
                    getContentPane(),
                    "You have unsaved changes. Discard them?",
                    "Unsaved Changes",
                    Messages.getWarningIcon());
            if (confirm != Messages.YES) {
                return;
            }
        }
        super.doCancelAction();
    }

    // === 內部類別 ===

    /**
     * 美化的列表項目渲染器
     */
    private static class FavoritePortCellRenderer extends JPanel implements ListCellRenderer<FavoritePort> {

        private final JBLabel iconLabel;
        private final JBLabel portLabel;
        private final JBLabel labelLabel;

        public FavoritePortCellRenderer() {
            setLayout(new BorderLayout(JBUI.scale(8), 0));
            setBorder(JBUI.Borders.empty(8, 10));
            setOpaque(true);

            // 圖標
            iconLabel = new JBLabel(AllIcons.Nodes.Favorite);
            add(iconLabel, BorderLayout.WEST);

            // 文字區域
            JPanel textPanel = new JPanel(new BorderLayout(JBUI.scale(6), 0));
            textPanel.setOpaque(false);

            portLabel = new JBLabel();
            portLabel.setFont(JBUI.Fonts.label().asBold());
            textPanel.add(portLabel, BorderLayout.WEST);

            labelLabel = new JBLabel();
            labelLabel.setFont(JBUI.Fonts.label());
            textPanel.add(labelLabel, BorderLayout.CENTER);

            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends FavoritePort> list,
                FavoritePort value,
                int index,
                boolean isSelected,
                boolean cellHasFocus) {
            // 設定 Port 號碼
            portLabel.setText(value.port);

            // 設定 Label（如果有的話）
            if (value.label != null && !value.label.isEmpty()) {
                labelLabel.setText("— " + value.label);
                labelLabel.setVisible(true);
            } else {
                labelLabel.setText("");
                labelLabel.setVisible(false);
            }

            // 設定選中狀態的顏色
            if (isSelected) {
                setBackground(list.getSelectionBackground());
                portLabel.setForeground(list.getSelectionForeground());
                labelLabel.setForeground(list.getSelectionForeground());
            } else {
                setBackground(index % 2 == 0 ? UIUtil.getListBackground()
                        : new JBColor(new Color(245, 247, 250), new Color(50, 52, 55)));
                portLabel.setForeground(UIUtil.getLabelForeground());
                labelLabel.setForeground(UIUtil.getContextHelpForeground());
            }

            return this;
        }
    }

    /**
     * 拖放排序的 TransferHandler
     */
    private class FavoritePortTransferHandler extends TransferHandler {

        private final DataFlavor indexFlavor = new DataFlavor(Integer.class, "Index");
        private int sourceIndex = -1;

        @Override
        public int getSourceActions(JComponent c) {
            return MOVE;
        }

        @Override
        protected Transferable createTransferable(JComponent c) {
            if (c instanceof JList) {
                sourceIndex = ((JList<?>) c).getSelectedIndex();
                if (sourceIndex != -1) {
                    return new Transferable() {
                        @Override
                        public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[] { indexFlavor };
                        }

                        @Override
                        public boolean isDataFlavorSupported(DataFlavor flavor) {
                            return flavor.equals(indexFlavor);
                        }

                        @NotNull
                        @Override
                        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                            if (!isDataFlavorSupported(flavor))
                                throw new UnsupportedFlavorException(flavor);
                            return sourceIndex;
                        }
                    };
                }
            }
            return null;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(indexFlavor))
                return false;
            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            return dl.getIndex() != sourceIndex;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support))
                return false;

            int draggedIdx;
            try {
                draggedIdx = (Integer) support.getTransferable().getTransferData(indexFlavor);
            } catch (UnsupportedFlavorException | IOException e) {
                LOG.error("Failed data transfer during drag and drop", e);
                return false;
            }

            JList.DropLocation dl = (JList.DropLocation) support.getDropLocation();
            int dropIdx = dl.getIndex();

            try {
                FavoritePort element = listModel.getElementAt(draggedIdx);
                listModel.remove(draggedIdx);

                // 調整目標索引（如果拖動位置在原位置之後）
                if (dropIdx > draggedIdx) {
                    dropIdx--;
                }

                listModel.add(dropIdx, element);

                if (support.getComponent() instanceof JList) {
                    ((JList<?>) support.getComponent()).setSelectedIndex(dropIdx);
                }
                modified = true;
                return true;
            } catch (Exception e) {
                LOG.error("Error moving list element during drag and drop", e);
                return false;
            }
        }

        @Override
        protected void exportDone(JComponent source, Transferable data, int action) {
            sourceIndex = -1;
        }
    }
}
