package org.wso2.lsp4intellij.contributors.annotator;

import com.intellij.openapi.fileEditor.impl.EditorTabColorProvider;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class LSPTabColorProvider implements EditorTabColorProvider {

    private static final Color ERROR_COLOR = JBColor.RED;
    private static final Color WARNING_COLOR = JBColor.YELLOW;
    private static final Color INFO_COLOR = JBColor.BLUE;

    @Nullable
    @Override
    public Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file) {
        return null;
    }

    @Nullable
    @Override
    public Color getEditorTabColor(@NotNull Project project, @NotNull VirtualFile file, @Nullable EditorWindow editorWindow) {
        return null;
    }
}