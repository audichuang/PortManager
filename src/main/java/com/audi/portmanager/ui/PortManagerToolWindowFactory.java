package com.audi.portmanager.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

/**
 * 端口管理器工具窗口工廠類
 * 負責創建並初始化端口管理器工具窗口
 * 實現DumbAware接口以確保在索引更新期間仍可使用
 */
public class PortManagerToolWindowFactory implements ToolWindowFactory, DumbAware {

    /**
     * 創建工具窗口內容
     *
     * @param project    當前項目實例
     * @param toolWindow 當前工具窗口實例
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // 創建端口管理器工具窗口UI內容實例
        PortManagerToolWindow portManagerToolWindow = new PortManagerToolWindow(project, toolWindow);

        // 使用ContentFactory創建工具窗口的內容
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(portManagerToolWindow.getContent(), "", false);

        // 將創建的內容添加到工具窗口中
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * 指定工具窗口在索引過程中是否應該可用
     * 由於實現了DumbAware接口，即使在索引更新期間，此工具窗口也能保持活躍狀態
     *
     * @param project 當前項目實例
     * @return 返回工具窗口是否可用
     */
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return ToolWindowFactory.super.shouldBeAvailable(project);
    }
}
