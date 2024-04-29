package org.wso2.lsp4intellij.contributors.annotator;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ProjectViewNodeDecorator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packageDependencies.ui.PackageDependenciesNode;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.awt.*;
import java.util.Objects;
import java.util.Set;

public class LSPFileHighlight implements ProjectViewNodeDecorator {

    private final float[] errorColorCodes = Color.RGBtoHSB(211, 65, 64, null);
    private final Color errorColor = Color.getHSBColor(errorColorCodes[0], errorColorCodes[1], errorColorCodes[2]);

    @Override
    public void decorate(ProjectViewNode<?> projectViewNode, PresentationData presentationData) {
        VirtualFile file = projectViewNode.getVirtualFile();

        if (file == null) {
            return;
        }

        Color foregroundColor = presentationData.getForcedTextForeground();
        Project project = projectViewNode.getProject();

        if (file.isDirectory() && !Objects.equals(Objects.requireNonNull(project).getBasePath(), file.getPath())) {
            if (directoryHasErrors(file)) {
                SimpleTextAttributes directoryAttributes =
                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, foregroundColor, errorColor);
                presentationData.addText(projectViewNode.getName(), directoryAttributes);
            } else {
                SimpleTextAttributes directoryAttributes =
                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foregroundColor, null);
                presentationData.addText(projectViewNode.getName(), directoryAttributes);
            }
        } else if (IntellijLanguageClient.isExtensionSupported(file)) {
            if (fileHasErrors(file)) {
                SimpleTextAttributes fileAttributes =
                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_WAVED, foregroundColor, errorColor);
                presentationData.addText(file.getName(), fileAttributes);
            } else {
                SimpleTextAttributes fileAttributes =
                        new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, foregroundColor, null);
                presentationData.addText(file.getName(), fileAttributes);
            }
        }
    }

    @Override
    public void decorate(PackageDependenciesNode packageDependenciesNode,
                         ColoredTreeCellRenderer coloredTreeCellRenderer) {
    }

    private boolean fileHasErrors(VirtualFile file) {
        if (!IntellijLanguageClient.isExtensionSupported(file)) {
            return false;
        }
        Set<EditorEventManager> managers = EditorEventManagerBase.managersForUri(FileUtils.sanitizeURI(file.getUrl()));
        for (EditorEventManager manager : managers) {
            if (manager != null) {
                return !manager.getAnnotations().isEmpty();
            }
        }
        return false;
    }

    private boolean directoryHasErrors(VirtualFile file) {
        try {
            VirtualFile[] children = file.getChildren();
            if (children == null) {
                return false;
            }
            for (VirtualFile child : children) {
                if (child.isDirectory() && directoryHasErrors(child)) {
                    return true;
                } else if (fileHasErrors(child)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
