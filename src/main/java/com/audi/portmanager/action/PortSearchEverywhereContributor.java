package com.audi.portmanager.action;

import com.audi.portmanager.model.PortProcessInfo;
import com.audi.portmanager.service.PortService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor;
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorFactory;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Search Everywhere 貢獻者：Port 搜尋
 * <p>
 * 讓使用者在按下 Shift+Shift 後直接輸入 Port 號碼，即可搜尋並終止佔用該 Port 的進程。
 */
public class PortSearchEverywhereContributor implements SearchEverywhereContributor<PortProcessInfo> {

    private static final Logger LOG = Logger.getInstance(PortSearchEverywhereContributor.class);
    private static final String NOTIFICATION_GROUP_ID = "PortManagerNotifications";

    private final Project project;

    public PortSearchEverywhereContributor(@Nullable Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String getSearchProviderId() {
        return "PortSearchEverywhereContributor";
    }

    @Override
    public @NotNull String getGroupName() {
        return "Ports";
    }

    @Override
    public int getSortWeight() {
        // 較低的權重讓此類別排在較後面，避免干擾一般搜尋
        return 100;
    }

    @Override
    public boolean showInFindResults() {
        return false;
    }

    @Override
    public boolean isShownInSeparateTab() {
        return true; // 在獨立的 Tab 中顯示
    }

    /**
     * 取得搜尋結果列表元素的渲染器
     */
    @Override
    public @NotNull ListCellRenderer<? super PortProcessInfo> getElementsRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof PortProcessInfo) {
                    PortProcessInfo info = (PortProcessInfo) value;
                    setText(String.format("[PID: %s] Port %s - %s", info.getPid(), info.getPort(), info.getCommand()));
                    setIcon(AllIcons.Debugger.KillProcess);
                }
                return this;
            }
        };
    }

    /**
     * 根據使用者輸入的模式 (pattern) 來取得搜尋結果
     */
    @Override
    public void fetchElements(
            @NotNull String pattern,
            @NotNull ProgressIndicator progressIndicator,
            @NotNull Processor<? super PortProcessInfo> consumer) {

        // 只處理純數字輸入（Port 號碼）
        if (pattern.isEmpty() || !pattern.matches("\\d+")) {
            return;
        }

        try {
            int port = Integer.parseInt(pattern);
            // 驗證 Port 範圍
            if (port <= 0 || port > 65535) {
                return;
            }

            // 調用 PortService 查詢佔用該 Port 的進程
            PortService portService = PortService.getInstance();
            List<PortProcessInfo> processes = portService.findProcessesOnPort(port);

            // 將結果傳遞給 consumer
            for (PortProcessInfo process : processes) {
                if (progressIndicator.isCanceled()) {
                    break;
                }
                consumer.process(process);
            }
        } catch (NumberFormatException e) {
            // 不是有效數字，忽略
        } catch (Exception e) {
            LOG.warn("Error fetching processes for port: " + pattern, e);
        }
    }

    /**
     * 當使用者點擊搜尋結果項目時觸發此方法
     *
     * @param selected   被選中的項目
     * @param modifiers  鍵盤修飾鍵
     * @param searchText 搜尋文字
     * @return true 表示處理完成應關閉搜尋視窗，false 表示保持開啟
     */
    @Override
    public boolean processSelectedItem(@NotNull PortProcessInfo selected, int modifiers, @NotNull String searchText) {
        String pid = selected.getPid();
        String command = selected.getCommand();
        String port = selected.getPort();

        // 彈出確認對話框
        int confirmation = Messages.showYesNoDialog(
                project,
                String.format("確定要終止以下進程嗎？\n\nPID: %s\nPort: %s\nCommand: %s", pid, port, command),
                "Confirm Kill Process",
                "Kill",
                "Cancel",
                Messages.getWarningIcon());

        if (confirmation != Messages.YES) {
            return false; // 用戶取消，保持搜尋視窗開啟
        }

        // 在背景執行終止進程
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Killing Process " + pid, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                PortService portService = PortService.getInstance();
                boolean success = portService.killProcess(pid);

                ApplicationManager.getApplication().invokeLater(() -> {
                    if (success) {
                        showNotification("PID: " + pid + " on port " + port + " terminated successfully.",
                                NotificationType.INFORMATION);
                    } else {
                        showNotification("Failed to kill PID: " + pid + ". Check permissions.",
                                NotificationType.ERROR);
                    }
                });
            }
        });

        return true; // 關閉搜尋視窗
    }

    @Override
    public @Nullable Object getDataForItem(@NotNull PortProcessInfo element, @NotNull String dataId) {
        return null;
    }

    private void showNotification(String content, NotificationType type) {
        Notification notification = new Notification(NOTIFICATION_GROUP_ID, "Port Manager", content, type);
        Notifications.Bus.notify(notification, project);
    }

    /**
     * Search Everywhere Contributor 工廠類別
     */
    public static class Factory implements SearchEverywhereContributorFactory<PortProcessInfo> {
        @Override
        public @NotNull SearchEverywhereContributor<PortProcessInfo> createContributor(
                @NotNull AnActionEvent initEvent) {
            return new PortSearchEverywhereContributor(initEvent.getProject());
        }
    }
}
