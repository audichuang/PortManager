package com.audi.portmanager.ui;

import com.audi.portmanager.service.FavoritePortsService.FavoritePort;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * 新增/編輯常用埠口的對話框
 * <p>
 * 使用 IntelliJ 的 DialogWrapper 提供現代化 UI 體驗，
 * 支援即時驗證和標準 IntelliJ 風格外觀。
 */
public class FavoritePortEditDialog extends DialogWrapper {

    // --- UI 組件 ---
    private JBTextField portField;
    private JBTextField labelField;

    // --- 模式與資料 ---
    private final boolean isEditMode;
    private final FavoritePort existingPort;
    private final java.util.Set<String> existingPorts;

    /**
     * 建立新增模式的對話框
     *
     * @param parent        父組件
     * @param existingPorts 已存在的 Port 號碼集合（用於重複檢查）
     */
    public FavoritePortEditDialog(@Nullable Component parent,
            java.util.Set<String> existingPorts) {
        super(parent, true);
        this.isEditMode = false;
        this.existingPort = null;
        this.existingPorts = existingPorts != null ? existingPorts : new java.util.HashSet<>();
        init();
        setTitle("Add Favorite Port");
    }

    /**
     * 建立編輯模式的對話框
     *
     * @param parent        父組件
     * @param portToEdit    要編輯的 Port
     * @param existingPorts 已存在的 Port 號碼集合（用於重複檢查，不包含當前編輯的 Port）
     */
    public FavoritePortEditDialog(@Nullable Component parent,
            @NotNull FavoritePort portToEdit,
            java.util.Set<String> existingPorts) {
        super(parent, true);
        this.isEditMode = true;
        this.existingPort = portToEdit;
        this.existingPorts = existingPorts != null ? existingPorts : new java.util.HashSet<>();
        init();
        setTitle("Edit Favorite Port");
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        // 建立 Port 輸入欄位
        portField = new JBTextField(15);
        portField.setToolTipText("Enter port number (1-65535)");
        portField.getEmptyText().setText("e.g., 8080");

        // 建立 Label 輸入欄位
        labelField = new JBTextField(20);
        labelField.setToolTipText("Optional description for this port");
        labelField.getEmptyText().setText("e.g., Spring Boot, MySQL");

        // 如果是編輯模式，填入現有資料
        if (isEditMode && existingPort != null) {
            portField.setText(existingPort.port);
            portField.setEditable(false); // 編輯模式下不允許修改 Port 號碼
            portField.setBackground(UIUtil.getPanelBackground());
            labelField.setText(existingPort.label);
        }

        // 使用 FormBuilder 建立表單
        JPanel formPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(createFieldLabel("Port Number:", true), portField)
                .addComponentToRightColumn(createHintLabel("Valid range: 1-65535"))
                .addVerticalGap(JBUI.scale(8))
                .addLabeledComponent(createFieldLabel("Label:", false), labelField)
                .addComponentToRightColumn(createHintLabel("Optional description"))
                .getPanel();

        // 包裝並添加邊距
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(JBUI.Borders.empty(10, 5));
        wrapper.add(formPanel, BorderLayout.CENTER);

        return wrapper;
    }

    /**
     * 建立欄位標籤
     */
    private JBLabel createFieldLabel(String text, boolean required) {
        JBLabel label = new JBLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        if (required) {
            // 使用 HTML 添加必填標記
            label.setText("<html>" + text.replace(":", "<span style='color:red'>*</span>:") + "</html>");
        }
        return label;
    }

    /**
     * 建立提示標籤（較小、較淡的文字）
     */
    private JBLabel createHintLabel(String text) {
        JBLabel hint = new JBLabel(text);
        hint.setFont(JBUI.Fonts.smallFont());
        hint.setForeground(UIUtil.getContextHelpForeground());
        return hint;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        String portText = portField.getText().trim();

        // 檢查是否為空
        if (portText.isEmpty()) {
            return new ValidationInfo("Please enter a port number", portField);
        }

        // 檢查是否為有效數字
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            return new ValidationInfo("Port must be a valid number", portField);
        }

        // 檢查範圍
        if (port < 1 || port > 65535) {
            return new ValidationInfo("Port must be between 1 and 65535", portField);
        }

        // 檢查重複（只在新增模式下檢查）
        if (!isEditMode && existingPorts.contains(portText)) {
            return new ValidationInfo("Port " + portText + " already exists", portField);
        }

        return null; // 驗證通過
    }

    @Override
    protected @NotNull Action @NotNull [] createActions() {
        return new Action[] { getOKAction(), getCancelAction() };
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return isEditMode ? labelField : portField;
    }

    // --- 公開方法：取得結果 ---

    /**
     * 取得使用者輸入的 Port 號碼
     */
    public String getPortNumber() {
        return portField.getText().trim();
    }

    /**
     * 取得使用者輸入的 Label
     */
    public String getLabel() {
        return labelField.getText().trim();
    }

    /**
     * 取得完整的 FavoritePort 物件
     */
    public FavoritePort getResult() {
        return new FavoritePort(getPortNumber(), getLabel());
    }
}
