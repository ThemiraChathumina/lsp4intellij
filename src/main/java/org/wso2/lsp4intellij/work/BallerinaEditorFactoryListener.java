package org.wso2.lsp4intellij.work;


import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.event.EditorFactoryListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFileBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.ProcessBuilderServerDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BallerinaEditorFactoryListener implements EditorFactoryListener {

    private Project project;
    private boolean balSourcesFound = false;


    public BallerinaEditorFactoryListener(Project project) {
        this.project = project;
    }


    @Override
    public void editorCreated(@NotNull EditorFactoryEvent event) {
        Project project = event.getEditor().getProject();
        if (project == null) {
            return;
        }
        VirtualFile file = FileDocumentManager.getInstance().getFile(event.getEditor().getDocument());
        System.out.println("editorCreated: ");
        if (!balSourcesFound && project.equals(this.project) && isBalFile(file)) {
            doRegister(project);
            balSourcesFound = true;
        }
    }

    private static void doRegister(@NotNull Project project) {
        System.out.println("doRegister: ");
        List<String> args = new ArrayList<>();
        args.add("C:\\Program Files\\Ballerina\\distributions\\ballerina-2201.0.0\\bin\\bal.bat");
        args.add("start-language-server");
        ProcessBuilder processBuilder = new ProcessBuilder(args);
        processBuilder.directory(new File(Objects.requireNonNull(project.getBasePath())));

        // Registers language server definition in the lsp4intellij lang-client library.
        IntellijLanguageClient.addServerDefinition(new ProcessBuilderServerDefinition("bal", processBuilder),
                project);
    }



    private static boolean isBalFile(@Nullable VirtualFile file) {
        if (file == null || file.getExtension() == null || file instanceof LightVirtualFileBase) {
            return false;
        }
        String fileUrl = file.getUrl();
        if (fileUrl.isEmpty() || fileUrl.startsWith("jar:")) {
            return false;
        }

        return file.getExtension().equals("bal");
    }
}