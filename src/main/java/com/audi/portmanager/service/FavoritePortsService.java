package com.audi.portmanager.service;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 全域常用埠口管理服務 (Application Service)
 * <p>
 * 此服務負責管理使用者的常用埠口列表，並透過 {@link PersistentStateComponent}
 * 實現跨專案的持久化儲存。資料會存放在 IDE 全域設定目錄中。
 * <p>
 * 支援功能：
 * <ul>
 * <li>Port + Label 組合（例如 "8080 - Spring Boot"）</li>
 * <li>新增、刪除、重新排序</li>
 * <li>跨專案共享</li>
 * </ul>
 */
@Service(Service.Level.APP)
@State(name = "PortManagerFavorites", storages = @Storage("PortManagerFavorites.xml"))
public final class FavoritePortsService implements PersistentStateComponent<FavoritePortsService.State> {

    private static final Logger LOG = Logger.getInstance(FavoritePortsService.class);

    private State myState = new State();

    /**
     * 取得 FavoritePortsService 單例實例
     *
     * @return FavoritePortsService 實例
     */
    public static FavoritePortsService getInstance() {
        return ApplicationManager.getApplication().getService(FavoritePortsService.class);
    }

    /**
     * 持久化狀態類別
     */
    public static class State {
        public List<FavoritePort> favorites = new ArrayList<>();
    }

    /**
     * 常用埠口資料類別
     */
    public static class FavoritePort {
        public String port = "";
        public String label = "";

        // 無參構造函數（XML 序列化需要）
        public FavoritePort() {
        }

        public FavoritePort(String port, String label) {
            this.port = port != null ? port : "";
            this.label = label != null ? label : "";
        }

        /**
         * 取得顯示文字
         * 
         * @return 格式為 "8080 - Spring Boot" 或純 "8080"
         */
        public String getDisplayText() {
            if (label != null && !label.isEmpty()) {
                return port + " - " + label;
            }
            return port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            FavoritePort that = (FavoritePort) o;
            return Objects.equals(port, that.port);
        }

        @Override
        public int hashCode() {
            return Objects.hash(port);
        }
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, myState);
        LOG.info("Loaded " + myState.favorites.size() + " global favorite ports.");
    }

    // ==================== CRUD 方法 ====================

    /**
     * 取得所有常用埠口（防禦性複製）
     */
    public List<FavoritePort> getFavorites() {
        return new ArrayList<>(myState.favorites);
    }

    /**
     * 取得所有 Port 號碼（不含 Label）
     */
    public List<String> getPortNumbers() {
        List<String> ports = new ArrayList<>();
        for (FavoritePort fav : myState.favorites) {
            ports.add(fav.port);
        }
        return ports;
    }

    /**
     * 新增常用埠口
     *
     * @param port  埠口號碼
     * @param label 標籤（可為空）
     * @return true 如果成功新增，false 如果已存在
     */
    public boolean addFavorite(String port, String label) {
        if (port == null || port.trim().isEmpty()) {
            return false;
        }
        port = port.trim();

        // 檢查是否已存在
        for (FavoritePort fav : myState.favorites) {
            if (fav.port.equals(port)) {
                LOG.info("Port " + port + " already exists in favorites.");
                return false;
            }
        }

        myState.favorites.add(new FavoritePort(port, label != null ? label.trim() : ""));
        LOG.info("Added favorite port: " + port + (label != null ? " (" + label + ")" : ""));
        return true;
    }

    /**
     * 移除常用埠口
     *
     * @param port 埠口號碼
     * @return true 如果成功移除
     */
    public boolean removeFavorite(String port) {
        boolean removed = myState.favorites.removeIf(fav -> fav.port.equals(port));
        if (removed) {
            LOG.info("Removed favorite port: " + port);
        }
        return removed;
    }

    /**
     * 更新常用埠口的標籤
     *
     * @param port     埠口號碼
     * @param newLabel 新標籤
     * @return true 如果成功更新
     */
    public boolean updateLabel(String port, String newLabel) {
        for (FavoritePort fav : myState.favorites) {
            if (fav.port.equals(port)) {
                fav.label = newLabel != null ? newLabel.trim() : "";
                LOG.info("Updated label for port " + port + ": " + fav.label);
                return true;
            }
        }
        return false;
    }

    /**
     * 重新排序常用埠口
     *
     * @param fromIndex 原始索引
     * @param toIndex   目標索引
     */
    public void reorder(int fromIndex, int toIndex) {
        if (fromIndex < 0 || fromIndex >= myState.favorites.size() ||
                toIndex < 0 || toIndex >= myState.favorites.size() ||
                fromIndex == toIndex) {
            return;
        }

        FavoritePort item = myState.favorites.remove(fromIndex);
        myState.favorites.add(toIndex, item);
        LOG.info("Reordered favorite from index " + fromIndex + " to " + toIndex);
    }

    /**
     * 清空所有常用埠口
     */
    public void clearAll() {
        myState.favorites.clear();
        LOG.info("Cleared all favorite ports.");
    }

    /**
     * 根據 Port 號碼查找 FavoritePort
     *
     * @param port 埠口號碼
     * @return FavoritePort 或 null
     */
    public @Nullable FavoritePort findByPort(String port) {
        for (FavoritePort fav : myState.favorites) {
            if (fav.port.equals(port)) {
                return fav;
            }
        }
        return null;
    }

    /**
     * 批量設定 Favorites（用於匯入或遷移）
     *
     * @param favorites 新的 Favorites 列表
     */
    public void setFavorites(List<FavoritePort> favorites) {
        myState.favorites.clear();
        if (favorites != null) {
            myState.favorites.addAll(favorites);
        }
        LOG.info("Set " + myState.favorites.size() + " favorite ports.");
    }

    /**
     * 從舊格式遷移（只有 Port 號碼，沒有 Label）
     *
     * @param ports Port 號碼列表
     */
    public void migrateFromLegacyFormat(List<String> ports) {
        if (ports == null || ports.isEmpty()) {
            return;
        }
        for (String port : ports) {
            addFavorite(port, "");
        }
        LOG.info("Migrated " + ports.size() + " ports from legacy format.");
    }
}
