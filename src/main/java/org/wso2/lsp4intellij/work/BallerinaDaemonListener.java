package org.wso2.lsp4intellij.work;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class BallerinaDaemonListener implements DaemonCodeAnalyzer.DaemonListener {
    private Project project;
    private Editor editor;

    public BallerinaDaemonListener(Project project, Editor editor) {
        this.project = project;
        this.editor = editor;
    }

    @Override
    public void daemonFinished() {
        // Get the VirtualFile for the document
        VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());

        // Check if the file is not null and print its name
        if (file != null) {
//            System.out.println("File name: " + file.getName());
        } else {
            System.out.println("No file associated with the current document.");
        }
    }
}
