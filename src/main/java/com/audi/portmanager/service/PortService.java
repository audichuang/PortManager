package com.audi.portmanager.service;

import com.audi.portmanager.model.PortProcessInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 埠口管理服務
 * <p>
 * 此服務負責查詢特定埠口上正在監聽的進程，以及提供終止這些進程的功能。
 * 支援 Windows、macOS 和 Linux 三種主要作業系統。
 */
public class PortService {

    private static final Logger LOG = Logger.getInstance(PortService.class);

    // ==================== 正則表達式模式 ====================

    /**
     * Windows netstat 輸出解析模式
     * 匹配類似 "TCP 0.0.0.0:8080 0.0.0.0:0 LISTENING 12345" 的行
     */
    private static final Pattern WINDOWS_NETSTAT_PATTERN = Pattern
            .compile("^\\s*TCP\\s+\\S+\\:(\\d+)\\s+\\S+\\s+LISTENING\\s+(\\d+).*$", Pattern.MULTILINE);

    /**
     * macOS lsof 輸出解析模式
     * 匹配類似 "java 12345 audi 50u IPv6 0x... 0t0 TCP *:8080 (LISTEN)" 的行
     * 或 "node 67890 audi 20u IPv4 0x... 0t0 TCP localhost:3000 (LISTEN)" 的行
     */
    private static final Pattern MACOS_LSOF_PATTERN = Pattern.compile(
            "^(\\S+)\\s+(\\d+)\\s+\\S+\\s+\\S+\\s+(?:IPv[46]|\\*)\\s+\\S+\\s+\\S+\\s+TCP\\s+(?:\\S*:|\\*:)(\\d+)\\s+\\(LISTEN\\).*$",
            Pattern.MULTILINE);

    /**
     * Linux ss 命令輸出解析模式
     * 匹配類似 "LISTEN 0 128 *:8080 *:* users:(("java",pid=1234,fd=12))" 的行
     */
    private static final Pattern LINUX_SS_PATTERN = Pattern.compile(
            "LISTEN\\s+\\d+\\s+\\d+\\s+(?:.*?:)?(\\d+)\\s+.*?\\s+users:\\(\\(\"([^\"]+)\",pid=(\\d+),.*?\\)\\)",
            Pattern.MULTILINE);

    /**
     * Linux netstat 命令輸出解析模式
     * 匹配類似 "tcp 0 0 0.0.0.0:8080 0.0.0.0:* LISTEN 1234/java" 的行
     */
    private static final Pattern LINUX_NETSTAT_PATTERN = Pattern.compile(
            "tcp\\s+\\d+\\s+\\d+\\s+\\S+:(\\d+)\\s+\\S+\\s+LISTEN\\s+(\\d+)(?:/([^\\s]+))?",
            Pattern.MULTILINE);

    /**
     * 查找在指定埠口上監聽的進程
     *
     * @param port 要查詢的埠口號碼
     * @return 進程資訊列表，包含PID、命令名稱和埠口號
     * @throws IOException        當命令執行過程中發生I/O錯誤時拋出
     * @throws ExecutionException 當命令執行失敗時拋出
     */
    public List<PortProcessInfo> findProcessesOnPort(int port) throws IOException, ExecutionException {
        List<PortProcessInfo> processes = new ArrayList<>();
        String commandOutput;

        if (SystemInfo.isWindows) {
            // Windows 系統: 執行 netstat -ano 命令，然後在Java中過濾結果
            GeneralCommandLine commandLine = new GeneralCommandLine("netstat", "-ano");
            commandOutput = executeCommand(commandLine);
            parseWindowsOutput(processes, commandOutput, WINDOWS_NETSTAT_PATTERN, port);
        } else if (SystemInfo.isMac) {
            // macOS 系統: 執行 lsof -i tcp:埠口 -sTCP:LISTEN -P -n 命令
            GeneralCommandLine commandLine = new GeneralCommandLine("lsof", "-i", "tcp:" + port, "-sTCP:LISTEN", "-P", "-n");
            commandOutput = executeCommand(commandLine);
            parseMacOutput(processes, commandOutput, MACOS_LSOF_PATTERN, port);
        } else if (SystemInfo.isLinux) {
            // Linux 系統: 首先嘗試使用 ss 命令，如果失敗則嘗試 netstat 命令
            try {
                GeneralCommandLine commandLine = new GeneralCommandLine("ss", "-tuln", "sport", "=" + port);
                commandOutput = executeCommand(commandLine);
                parseLinuxSsOutput(processes, commandOutput, LINUX_SS_PATTERN, port);
            } catch (ExecutionException e) {
                LOG.info("ss 命令執行失敗，嘗試使用 netstat 命令", e);
                // 備用方案：使用 netstat 命令
                GeneralCommandLine commandLine = new GeneralCommandLine("netstat", "-tuln");
                commandOutput = executeCommand(commandLine);
                parseLinuxNetstatOutput(processes, commandOutput, LINUX_NETSTAT_PATTERN, port);
            }
        } else {
            LOG.warn("不支援的作業系統: " + SystemInfo.OS_NAME);
            throw new UnsupportedOperationException("暫不支援的作業系統: " + SystemInfo.OS_NAME);
        }

        return processes;
    }

    /**
     * 終止指定PID的進程
     *
     * @param pid 進程ID
     * @return 如果成功終止進程則返回true，否則返回false
     */
    public boolean killProcess(String pid) {
        GeneralCommandLine commandLine;

        if (SystemInfo.isWindows) {
            // Windows 系統: taskkill /PID <pid> /F
            commandLine = new GeneralCommandLine("taskkill", "/PID", pid, "/F");
        } else if (SystemInfo.isMac || SystemInfo.isLinux) {
            // macOS 和 Linux 系統: kill -9 <pid>
            commandLine = new GeneralCommandLine("kill", "-9", pid);
        } else {
            LOG.warn("不支援在此作業系統上終止進程: " + SystemInfo.OS_NAME);
            return false;
        }

        try {
            ProcessOutput output = ExecUtil.execAndGetOutput(commandLine);
            if (output.getExitCode() == 0) {
                LOG.info("成功終止PID為 " + pid + " 的進程");
                return true;
            } else {
                LOG.warn("無法終止PID為 " + pid + " 的進程。退出碼: " + output.getExitCode()
                        + "，錯誤訊息: " + output.getStderr());
                return false;
            }
        } catch (ExecutionException e) {
            LOG.error("執行終止命令時發生錯誤，PID: " + pid, e);
            return false;
        }
    }

    /**
     * 執行系統命令並返回輸出結果
     *
     * @param commandLine 要執行的命令行
     * @return 命令的標準輸出內容
     * @throws ExecutionException 當命令執行失敗時拋出
     */
    private String executeCommand(GeneralCommandLine commandLine) throws ExecutionException {
        LOG.info("執行命令: " + commandLine.getCommandLineString());

        ProcessOutput output = ExecUtil.execAndGetOutput(commandLine);
        if (output.getExitCode() != 0) {
            String error = "命令執行失敗（退出碼: " + output.getExitCode() + "）: "
                    + commandLine.getCommandLineString() + "\n錯誤訊息: " + output.getStderr();

            // 在macOS上，如果沒有找到進程，lsof可能返回退出碼1，這種情況不應視為錯誤
            if (SystemInfo.isMac && commandLine.getExePath().contains("lsof") && output.getExitCode() == 1
                    && output.getStdout().isEmpty()) {
                LOG.info("lsof未在指定埠口找到進程。");
                return ""; // 返回空字串，不是錯誤
            }

            LOG.error(error);
            throw new ExecutionException(error);
        }

        return output.getStdout();
    }

    /**
     * 解析Windows系統命令輸出
     */
    private void parseWindowsOutput(List<PortProcessInfo> processes, String output, Pattern pattern, int targetPort) {
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            String portStr = matcher.group(1);
            String pid = matcher.group(2);

            if (String.valueOf(targetPort).equals(portStr)) {
                String commandName = getCommandNameForPidWindows(pid);
                processes.add(new PortProcessInfo(pid, commandName, portStr));
                LOG.info("在埠口 " + portStr + " 上找到進程: PID=" + pid + ", 命令=" + commandName);
            }
        }
    }

    /**
     * 解析macOS系統命令輸出
     */
    private void parseMacOutput(List<PortProcessInfo> processes, String output, Pattern pattern, int targetPort) {
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            String command = matcher.group(1);
            String pid = matcher.group(2);
            String portStr = matcher.group(3);

            if (String.valueOf(targetPort).equals(portStr)) {
                String fullCommand = getFullCommandForPidMac(pid, command);
                processes.add(new PortProcessInfo(pid, fullCommand, portStr));
                LOG.info("在埠口 " + portStr + " 上找到進程: PID=" + pid + ", 命令=" + fullCommand);
            }
        }
    }

    /**
     * 解析Linux系統ss命令輸出
     */
    private void parseLinuxSsOutput(List<PortProcessInfo> processes, String output, Pattern pattern, int targetPort) {
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            String portStr = matcher.group(1);
            String command = matcher.group(2);
            String pid = matcher.group(3);

            if (String.valueOf(targetPort).equals(portStr)) {
                String fullCommand = getFullCommandForPidLinux(pid, command);
                processes.add(new PortProcessInfo(pid, fullCommand, portStr));
                LOG.info("在埠口 " + portStr + " 上找到進程: PID=" + pid + ", 命令=" + fullCommand);
            }
        }
    }

    /**
     * 解析Linux系統netstat命令輸出
     */
    private void parseLinuxNetstatOutput(List<PortProcessInfo> processes, String output, Pattern pattern, int targetPort) {
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            String portStr = matcher.group(1);
            String pid = matcher.group(2);
            String command = matcher.groupCount() >= 3 ? matcher.group(3) : "(Unknown)";

            if (String.valueOf(targetPort).equals(portStr)) {
                String fullCommand = getFullCommandForPidLinux(pid, command);
                processes.add(new PortProcessInfo(pid, fullCommand, portStr));
                LOG.info("在埠口 " + portStr + " 上找到進程: PID=" + pid + ", 命令=" + fullCommand);
            }
        }
    }

    /**
     * 獲取Windows系統上指定PID的進程命令名稱（盡力而為）
     */
    private String getCommandNameForPidWindows(String pid) {
        try {
            // 嘗試使用wmic獲取可執行文件路徑
            GeneralCommandLine cmd = new GeneralCommandLine("wmic", "process", "where", "ProcessId=" + pid, "get",
                    "ExecutablePath", "/format:list");
            ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 500); // 超時500毫秒

            if (output.getExitCode() == 0 && !output.getStdout().isEmpty()) {
                String[] lines = output.getStdout().split("[\\r\\n]+");
                for (String line : lines) {
                    if (line.startsWith("ExecutablePath=")) {
                        return line.substring("ExecutablePath=".length()).trim();
                    }
                }
            }

            // 備用方案：使用tasklist
            cmd = new GeneralCommandLine("tasklist", "/FI", "PID eq " + pid, "/NH", "/FO", "CSV");
            output = ExecUtil.execAndGetOutput(cmd, 500);

            if (output.getExitCode() == 0 && !output.getStdout().isEmpty()) {
                String[] csvParts = output.getStdout().split(",");
                if (csvParts.length > 0) {
                    return csvParts[0].replace("\\\"", "").trim(); // 獲取映像名稱
                }
            }
        } catch (Exception e) {
            LOG.warn("無法在Windows上獲取PID " + pid + " 的命令名稱", e);
        }

        return "(未知)"; // 查詢失敗時的預設值
    }

    /**
     * 獲取macOS系統上指定PID的完整命令路徑
     */
    private String getFullCommandForPidMac(String pid, String defaultCommand) {
        try {
            // 使用ps獲取完整命令參數列表
            GeneralCommandLine cmd = new GeneralCommandLine("ps", "-p", pid, "-o", "command=");
            ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 500); // 超時500毫秒

            if (output.getExitCode() == 0 && !output.getStdout().isEmpty()) {
                return output.getStdout().trim(); // 返回完整命令行
            }
        } catch (Exception e) {
            LOG.warn("無法在macOS上獲取PID " + pid + " 的完整命令", e);
        }

        return defaultCommand; // 回退到lsof提供的命令名稱
    }

    /**
     * 獲取Linux系統上指定PID的完整命令路徑
     */
    private String getFullCommandForPidLinux(String pid, String defaultCommand) {
        try {
            // 使用ps獲取完整命令參數列表
            GeneralCommandLine cmd = new GeneralCommandLine("ps", "-p", pid, "-o", "cmd=");
            ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 500); // 超時500毫秒

            if (output.getExitCode() == 0 && !output.getStdout().isEmpty()) {
                return output.getStdout().trim(); // 返回完整命令行
            }

            // 或者嘗試從/proc讀取
            GeneralCommandLine cmdCat = new GeneralCommandLine("cat", "/proc/" + pid + "/cmdline");
            output = ExecUtil.execAndGetOutput(cmdCat, 500);

            if (output.getExitCode() == 0 && !output.getStdout().isEmpty()) {
                // /proc/PID/cmdline使用null字元分隔參數，將其替換為空格
                return output.getStdout().replace('\0', ' ').trim();
            }
        } catch (Exception e) {
            LOG.warn("無法在Linux上獲取PID " + pid + " 的完整命令", e);
        }

        return defaultCommand; // 回退到預設命令名稱
    }
}