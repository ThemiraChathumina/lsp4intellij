package org.wso2.lsp4intellij.contributors.annotator;

import com.intellij.codeInsight.intention.actions.ShowIntentionActionsAction;
import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;

public class IntentionActionFix extends ShowIntentionActionsAction {

    public IntentionActionFix() {
        super();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        Presentation presentation = event.getPresentation();
        if (LightEdit.owns(project)) {
            presentation.setEnabledAndVisible(!ActionPlaces.EDITOR_POPUP.equals(event.getPlace()));
            return;
        }
        super.update(event);
        presentation.setIcon(AllIcons.Actions.IntentionBulb);
    }

}
