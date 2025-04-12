package com.audi.portmanager.service;

import com.audi.portmanager.model.PortProcessInfo;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PortService {

    private static final Logger LOG = Logger.getInstance(PortService.class);
    // Patterns for parsing command output
    // Windows: Matches lines like "TCP 0.0.0.0:8080 0.0.0.0:0 LISTENING 12345"
    private static final Pattern WINDOWS_NETSTAT_PATTERN = Pattern
            .compile("^\\s*TCP\\s+\\S+\\:(\\d+)\\s+\\S+\\s+LISTENING\\s+(\\d+).*$", Pattern.MULTILINE);
    // macOS (lsof): Matches lines like "java 12345 audi 50u IPv6 0x... 0t0 TCP
    // *:8080 (LISTEN)"
    // or "node 67890 audi 20u IPv4 0x... 0t0 TCP localhost:3000 (LISTEN)"
    private static final Pattern MACOS_LSOF_PATTERN = Pattern.compile(
            "^(\\S+)\\s+(\\d+)\\s+\\S+\\s+\\S+\\s+(?:IPv[46]|\\*)\\s+\\S+\\s+\\S+\\s+TCP\\s+(?:\\S*:|\\*:)(\\d+)\\s+\\(LISTEN\\).*$",
            Pattern.MULTILINE);

    /**
     * Finds processes listening on the specified port.
     *
     * @param port The port number.
     * @return A list of PortProcessInfo objects.
     * @throws IOException        If an I/O error occurs during command execution.
     * @throws ExecutionException If the command execution fails.
     */
    public List<PortProcessInfo> findProcessesOnPort(int port) throws IOException, ExecutionException {
        List<PortProcessInfo> processes = new ArrayList<>();
        String commandOutput;
        Pattern pattern;

        if (SystemInfo.isWindows) {
            // Windows command: netstat -ano | findstr "LISTENING" | findstr ":<port>"
            // We run netstat -ano first, then filter in Java for better control
            GeneralCommandLine commandLine = new GeneralCommandLine("netstat", "-ano");
            commandOutput = executeCommand(commandLine);
            pattern = WINDOWS_NETSTAT_PATTERN;
            parseWindowsOutput(processes, commandOutput, pattern, port);
        } else if (SystemInfo.isMac) {
            // macOS command: lsof -i tcp:<port> -sTCP:LISTEN -P -n
            GeneralCommandLine commandLine = new GeneralCommandLine("lsof", "-i", "tcp:" + port, "-sTCP:LISTEN", "-P",
                    "-n");
            commandOutput = executeCommand(commandLine);
            pattern = MACOS_LSOF_PATTERN;
            parseMacOutput(processes, commandOutput, pattern, port);
        } else {
            LOG.warn("Unsupported operating system: " + SystemInfo.OS_NAME);
            // Consider adding Linux support here (e.g., using ss or netstat)
            throw new UnsupportedOperationException("Operating system not supported yet: " + SystemInfo.OS_NAME);
        }

        return processes;
    }

    /**
     * Kills the process with the specified PID.
     *
     * @param pid The Process ID.
     * @return true if the kill command was executed successfully, false otherwise.
     */
    public boolean killProcess(String pid) {
        GeneralCommandLine commandLine;
        if (SystemInfo.isWindows) {
            // Windows command: taskkill /PID <pid> /F
            commandLine = new GeneralCommandLine("taskkill", "/PID", pid, "/F");
        } else if (SystemInfo.isMac) {
            // macOS command: kill -9 <pid>
            commandLine = new GeneralCommandLine("kill", "-9", pid);
        } else {
            LOG.warn("Unsupported operating system for killing process: " + SystemInfo.OS_NAME);
            return false;
        }

        try {
            ProcessOutput output = ExecUtil.execAndGetOutput(commandLine);
            if (output.getExitCode() == 0) {
                LOG.info("Successfully killed process with PID: " + pid);
                return true;
            } else {
                LOG.warn("Failed to kill process with PID: " + pid + ". Exit code: " + output.getExitCode()
                        + ", Error: " + output.getStderr());
                return false;
            }
        } catch (ExecutionException e) {
            LOG.error("Error executing kill command for PID: " + pid, e);
            return false;
        }
    }

    private String executeCommand(GeneralCommandLine commandLine) throws ExecutionException {
        // Use ExecUtil for simpler command execution and output retrieval
        ProcessOutput output = ExecUtil.execAndGetOutput(commandLine);
        if (output.getExitCode() != 0) {
            String error = "Command execution failed (Exit code: " + output.getExitCode() + ") for: "
                    + commandLine.getCommandLineString() + "\\nStderr: " + output.getStderr();
            // On macOS, lsof might return exit code 1 if no process is found, which is not
            // an error in our case.
            if (SystemInfo.isMac && commandLine.getExePath().contains("lsof") && output.getExitCode() == 1
                    && output.getStdout().isEmpty()) {
                LOG.info("lsof found no processes on the specified port.");
                return ""; // Return empty string, not an error
            }
            LOG.error(error);
            throw new ExecutionException(error);
        }
        return output.getStdout();
    }

    private void parseWindowsOutput(List<PortProcessInfo> processes, String output, Pattern pattern, int targetPort) {
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            String portStr = matcher.group(1);
            String pid = matcher.group(2);
            if (String.valueOf(targetPort).equals(portStr)) {
                // On Windows, netstat doesn't easily give the command name.
                // We could try 'tasklist /FI "PID eq <pid>"' but it's slow to run for each PID.
                // For simplicity, we'll leave the command blank for now.
                String commandName = getCommandNameForPidWindows(pid); // Helper method to get command name
                processes.add(new PortProcessInfo(pid, commandName, portStr));
                LOG.info("Found process on port " + portStr + ": PID=" + pid + ", Command=" + commandName);
            }
        }
    }

    private void parseMacOutput(List<PortProcessInfo> processes, String output, Pattern pattern, int targetPort) {
        Matcher matcher = pattern.matcher(output);
        while (matcher.find()) {
            String command = matcher.group(1);
            String pid = matcher.group(2);
            String portStr = matcher.group(3);
            // Double check the port, though lsof -i tcp:<port> should already filter
            if (String.valueOf(targetPort).equals(portStr)) {
                // lsof usually gives the command name directly
                String fullCommand = getFullCommandForPidMac(pid, command); // Refine command if needed
                processes.add(new PortProcessInfo(pid, fullCommand, portStr));
                LOG.info("Found process on port " + portStr + ": PID=" + pid + ", Command=" + fullCommand);
            }
        }
    }

    // Helper method to get process command name on Windows (Best effort)
    private String getCommandNameForPidWindows(String pid) {
        try {
            GeneralCommandLine cmd = new GeneralCommandLine("wmic", "process", "where", "ProcessId=" + pid, "get",
                    "ExecutablePath", "/format:list");
            ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 500); // Timeout 500ms
            if (output.getExitCode() == 0 && !output.getStdout().isEmpty()) {
                String[] lines = output.getStdout().split("[\\r\\n]+");
                for (String line : lines) {
                    if (line.startsWith("ExecutablePath=")) {
                        return line.substring("ExecutablePath=".length()).trim();
                    }
                }
            }
            // Fallback using tasklist
            cmd = new GeneralCommandLine("tasklist", "/FI", "PID eq " + pid, "/NH", "/FO", "CSV");
            output = ExecUtil.execAndGetOutput(cmd, 500);
            if (output.getExitCode() == 0 && !output.getStdout().isEmpty()) {
                String[] csvParts = output.getStdout().split(",");
                if (csvParts.length > 0) {
                    return csvParts[0].replace("\\\"", "").trim(); // Get image name
                }
            }

        } catch (Exception e) {
            LOG.warn("Could not get command name for PID " + pid + " on Windows", e);
        }
        return "(Unknown)"; // Default if lookup fails
    }

    // Helper method to potentially get full command path on macOS
    private String getFullCommandForPidMac(String pid, String defaultCommand) {
        try {
            // Use 'ps' to get the full command argument list
            GeneralCommandLine cmd = new GeneralCommandLine("ps", "-p", pid, "-o", "command=");
            ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 500); // Timeout 500ms
            if (output.getExitCode() == 0 && !output.getStdout().isEmpty()) {
                return output.getStdout().trim(); // Return the full command line
            }
        } catch (Exception e) {
            LOG.warn("Could not get full command for PID " + pid + " on macOS", e);
        }
        return defaultCommand; // Fallback to the command name from lsof
    }

}