package org.wso2.lsp4intellij.work;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PreloadingActivity;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BallerinaPreloadingActivity extends PreloadingActivity {
    @Override
    public void preload(@NotNull ProgressIndicator progressIndicator) {
        final MessageBusConnection connect = ApplicationManager.getApplication().getMessageBus().connect();
        connect.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
            @Override
            public void projectOpened(@Nullable final Project project) {
                if (project == null) {
                    return;
                }
                watchForBalFiles(project);
            }
        });
    }

    private void watchForBalFiles(Project project) {
        EditorFactory.getInstance().addEditorFactoryListener(new org.wso2.lsp4intellij.work.BallerinaEditorFactoryListener(project), project);
    }
}