package org.wso2.lsp4intellij.contributors.annotator;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.ArrayList;
import java.util.Objects;

public class HighlightFilter implements Condition<VirtualFile> {

    private Project project;

    public HighlightFilter(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public boolean value(VirtualFile virtualFile) {
        // get all opened virtual files
        VirtualFile[] openFiles = project.getBaseDir().getChildren();
        return true;
    }

}
