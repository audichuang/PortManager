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

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class PortManagerToolWindow {

    private static final Logger LOG = Logger.getInstance(PortManagerToolWindow.class);
    private static final String NOTIFICATION_GROUP_ID = "PortManagerNotifications"; // Define a notification group ID

    private final Project project;
    private final ToolWindow toolWindow;
    private final PortService portService;
    private JPanel toolWindowContent;
    private JTextField portInputField;
    private JButton findButton;
    private JButton killButton;
    private JTable processTable;
    private DefaultTableModel tableModel;

    public PortManagerToolWindow(Project project, ToolWindow toolWindow) {
        this.project = project;
        this.toolWindow = toolWindow;
        this.portService = new PortService(); // Instantiate the service
        initializeUI();
        setupListeners();
    }

    private void initializeUI() {
        toolWindowContent = new JPanel(new BorderLayout(JBUI.scale(5), JBUI.scale(5)));
        toolWindowContent.setBorder(JBUI.Borders.empty(10));

        // --- Top Panel (Input and Find Button) ---
        JPanel topPanel = new JPanel(new BorderLayout(JBUI.scale(5), 0));
        portInputField = new JBTextField(10);
        portInputField.setToolTipText("Enter port number (e.g., 8080)");
        findButton = new JButton("Find Processes");

        topPanel.add(new JLabel("Port:"), BorderLayout.WEST);
        topPanel.add(portInputField, BorderLayout.CENTER);
        topPanel.add(findButton, BorderLayout.EAST);

        // --- Center Panel (Table) ---
        String[] columnNames = { "PID", "Command/Name", "Port" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table cells non-editable
            }
        };
        processTable = new JBTable(tableModel);
        processTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        processTable.getTableHeader().setReorderingAllowed(false);
        processTable.getColumnModel().getColumn(0).setPreferredWidth(JBUI.scale(80));
        processTable.getColumnModel().getColumn(1).setPreferredWidth(JBUI.scale(350));
        processTable.getColumnModel().getColumn(2).setPreferredWidth(JBUI.scale(60));

        JBScrollPane scrollPane = new JBScrollPane(processTable);

        // --- Bottom Panel (Kill Button) ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        killButton = new JButton("Kill Selected Process");
        killButton.setEnabled(false); // Initially disabled
        bottomPanel.add(killButton);

        // --- Add panels to main content panel ---
        toolWindowContent.add(topPanel, BorderLayout.NORTH);
        toolWindowContent.add(scrollPane, BorderLayout.CENTER);
        toolWindowContent.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void setupListeners() {
        // Find Button Listener
        findButton.addActionListener(e -> findProcessesAction());
        // Allow pressing Enter in the input field to trigger find
        portInputField.addActionListener(e -> findProcessesAction());

        // Kill Button Listener
        killButton.addActionListener(e -> killProcessAction());

        // Table Selection Listener (to enable/disable Kill button)
        processTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                killButton.setEnabled(processTable.getSelectedRow() != -1);
            }
        });
    }

    private void findProcessesAction() {
        String portText = portInputField.getText().trim();
        if (portText.isEmpty()) {
            Messages.showWarningDialog(project, "Please enter a port number.", "Input Required");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
            if (port <= 0 || port > 65535) {
                Messages.showErrorDialog(project, "Port number must be between 1 and 65535.", "Invalid Port");
                return;
            }
        } catch (NumberFormatException ex) {
            Messages.showErrorDialog(project, "Please enter a valid numeric port number.", "Invalid Input");
            return;
        }

        // Clear previous results before starting search
        ApplicationManager.getApplication().invokeLater(() -> {
            tableModel.setRowCount(0);
            killButton.setEnabled(false);
        });

        // Run the potentially long-running task in the background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Finding Processes on Port " + port, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    List<PortProcessInfo> processes = portService.findProcessesOnPort(port);

                    // Update UI on the Event Dispatch Thread
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (processes.isEmpty()) {
                            showNotification("No processes found listening on port " + port + ".",
                                    NotificationType.INFORMATION);
                        } else {
                            for (PortProcessInfo info : processes) {
                                tableModel.addRow(new Object[] { info.getPid(), info.getCommand(), info.getPort() });
                            }
                            showNotification("Found " + processes.size() + " process(es) on port " + port + ".",
                                    NotificationType.INFORMATION);
                        }
                    });

                } catch (UnsupportedOperationException uoe) {
                    LOG.error(uoe);
                    ApplicationManager.getApplication().invokeLater(
                            () -> Messages.showErrorDialog(project, uoe.getMessage(), "Unsupported Operation System"));
                } catch (Exception ex) {
                    LOG.error("Error finding processes on port " + port, ex);
                    ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project,
                            "Error finding processes: " + ex.getMessage(), "Error"));
                }
            }
        });
    }

    private void killProcessAction() {
        int selectedRow = processTable.getSelectedRow();
        if (selectedRow == -1) {
            return; // Should not happen
        }

        String pid = (String) tableModel.getValueAt(selectedRow, 0);
        String command = (String) tableModel.getValueAt(selectedRow, 1);
        String port = (String) tableModel.getValueAt(selectedRow, 2);

        // Confirmation dialog
        int confirmation = Messages.showYesNoDialog(project,
                "Are you sure you want to kill process PID: " + pid + " (" + command + ") on port " + port + "?",
                "Confirm Kill Process", Messages.getWarningIcon());

        if (confirmation != Messages.YES) {
            return;
        }

        // Run kill operation in background
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Killing Process " + pid, false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                boolean success = portService.killProcess(pid);

                // Show result notification and update table on EDT
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (success) {
                        showNotification("Successfully killed process PID: " + pid, NotificationType.INFORMATION);
                        // Remove the row from the table
                        // Need to be careful if multiple rows were selected or table changed
                        // Re-find the row index based on PID before removing, or iterate and remove
                        for (int i = tableModel.getRowCount() - 1; i >= 0; i--) {
                            if (pid.equals(tableModel.getValueAt(i, 0))) {
                                tableModel.removeRow(i);
                                break; // Assume unique PID per port search
                            }
                        }

                        // Ensure kill button is disabled if no row is selected after removal
                        if (processTable.getSelectedRow() == -1) {
                            killButton.setEnabled(false);
                        }
                        // Optionally, refresh the list automatically after killing
                        // findProcessesAction();
                    } else {
                        showNotification("Failed to kill process PID: " + pid + ". Check logs for details.",
                                NotificationType.ERROR);
                        Messages.showErrorDialog(project, "Failed to kill process PID: " + pid
                                + ".\\nSee logs for more details (Help > Show Log in ...).", "Kill Failed");
                    }
                });
            }
        });
    }

    // Helper method to show notifications
    private void showNotification(String content, NotificationType type) {
        Notification notification = new Notification(NOTIFICATION_GROUP_ID,
                "Port Manager", // Title
                content,
                type);
        Notifications.Bus.notify(notification, project);
    }

    public JPanel getContent() {
        return toolWindowContent;
    }
}