package org.wso2.lsp4intellij.listeners;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerEvent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.Set;

public class LSPFileEditorListener implements FileEditorManagerListener {
    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {

        System.out.println("File opened: " + file.getPath());
    }

    @Override
    public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        System.out.println("File closed: " + file.getPath());
    }

    @Override
    public void selectionChanged(@NotNull FileEditorManagerEvent event) {
        VirtualFile newFile = event.getNewFile();
        VirtualFile oldFile = event.getOldFile();

        if (newFile != null) {
            Set<LanguageServerWrapper>
                    languageServerWrappers = IntellijLanguageClient.getProjectToLanguageWrappers().get(
                    FileUtils.projectToUri(event.getManager().getProject()));
            System.out.println("Switched to file: " + newFile.getPath());
        }

        if (oldFile != null) {
            System.out.println("Switched from file: " + oldFile.getPath());
        }
    }
}
