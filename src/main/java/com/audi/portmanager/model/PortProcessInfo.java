package com.audi.portmanager.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * 端口進程信息數據類
 * 用於存儲與特定端口相關的進程信息
 * 包含進程ID、命令和端口號等基本信息
 */
@Data
@RequiredArgsConstructor
public class PortProcessInfo {
    /**
     * 進程ID，用於唯一標識系統中的進程
     */
    private final String pid;

    /**
     * 進程執行的命令，表示啟動該進程的完整命令
     */
    private final String command;

    /**
     * 進程佔用的端口號
     */
    private final String port;
}
