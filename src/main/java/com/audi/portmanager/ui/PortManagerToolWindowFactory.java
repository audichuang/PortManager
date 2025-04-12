package com.audi.portmanager.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class PortManagerToolWindowFactory implements ToolWindowFactory, DumbAware {

    /**
     * Creates the tool window content.
     *
     * @param project    current project
     * @param toolWindow current tool window
     */
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        // Create an instance of our tool window UI content
        PortManagerToolWindow portManagerToolWindow = new PortManagerToolWindow(project, toolWindow);

        // Create the content for the tool window
        ContentFactory contentFactory = ContentFactory.getInstance();
        Content content = contentFactory.createContent(portManagerToolWindow.getContent(), "", false);

        // Add the content to the tool window
        toolWindow.getContentManager().addContent(content);
    }

    /**
     * Specifies whether the tool window should be available during indexing.
     * DumbAware allows the tool window to be active even when indexes are being
     * updated.
     */
    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return ToolWindowFactory.super.shouldBeAvailable(project);
    }
}