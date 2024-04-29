package org.wso2.lsp4intellij.contributors.annotator;

import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.SmartList;
import groovy.lang.Tuple3;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.contributors.fixes.LSPCodeActionFix;
import org.wso2.lsp4intellij.editor.EditorEventManager;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;

public class LSPAnnot implements Annotator {

    private static final Logger LOG = Logger.getInstance(LSPAnnotator.class);
    private static final HashMap<DiagnosticSeverity, HighlightSeverity> lspToIntellijAnnotationsMap = new HashMap<>();

    static {
        lspToIntellijAnnotationsMap.put(DiagnosticSeverity.Error, HighlightSeverity.ERROR);
        lspToIntellijAnnotationsMap.put(DiagnosticSeverity.Warning, HighlightSeverity.WARNING);

        // seem flipped, but just different semantics lsp<->intellij. Hint is rendered without any squiggle
        lspToIntellijAnnotationsMap.put(DiagnosticSeverity.Information, HighlightSeverity.WEAK_WARNING);
        lspToIntellijAnnotationsMap.put(DiagnosticSeverity.Hint, HighlightSeverity.INFORMATION);
    }

    @Override
    public void annotate(@NotNull PsiElement psiElement, @NotNull AnnotationHolder annotationHolder) {
        PsiFile file = psiElement.getContainingFile();
        LanguageServerWrapper
                languageServerWrapper = LanguageServerWrapper.forVirtualFile(file.getVirtualFile(), file.getProject());
        if (languageServerWrapper == null || languageServerWrapper.getStatus() != ServerStatus.INITIALIZED) {
            return;
        }

        VirtualFile virtualFile = file.getVirtualFile();
        if (FileUtils.isFileSupported(virtualFile) && IntellijLanguageClient.isExtensionSupported(virtualFile)) {
            String uri = FileUtils.VFSToURI(virtualFile);
            // TODO annotations are applied to a file / document not to an editor. so store them by file and not by editor..
            EditorEventManager eventManager = EditorEventManagerBase.forUri(uri);

            if (eventManager.isDiagnosticSyncRequired()) {
                try {
                    createAnnotations(annotationHolder, eventManager);
                } catch (ConcurrentModificationException e) {
                    // Todo - Add proper fix to handle concurrent modifications gracefully.
                    LOG.warn("Error occurred when updating LSP code actions due to concurrent modifications.", e);
                } catch (Throwable t) {
                    LOG.warn("Error occurred when updating LSP code actions.", t);
                }
                eventManager.requestAndShowCodeActions();
            } else {
                try {
                    updateSilentAnnotations(annotationHolder, eventManager);
                    updateAnnotations(annotationHolder, eventManager);
                } catch (ConcurrentModificationException e) {
                    // Todo - Add proper fix to handle concurrent modifications gracefully.
                    LOG.warn("Error occurred when updating LSP diagnostics due to concurrent modifications.", e);
                } catch (Throwable t) {
                    LOG.warn("Error occurred when updating LSP diagnostics.", t);
                }
            }
        }
    }

    private void updateSilentAnnotations(AnnotationHolder holder, EditorEventManager eventManager) {
        final List<Tuple3<HighlightSeverity, TextRange, LSPCodeActionFix>>
                annotations = eventManager.getSilentAnnotations();
        if (annotations == null) {
            return;
        }
        annotations.forEach(annotation -> {
            AnnotationBuilder builder = holder.newSilentAnnotation(annotation.getFirst());
            builder.range(annotation.getSecond()).withFix(annotation.getThird()).create();
        });
    }

    private void updateAnnotations(AnnotationHolder holder, EditorEventManager eventManager) {
        final List<Annotation> annotations = eventManager.getAnnotations();
        if (annotations == null) {
            return;
        }
        annotations.forEach(annotation -> {
            AnnotationBuilder builder = holder.newAnnotation(annotation.getSeverity(), annotation.getMessage());

            if (annotation.getQuickFixes() == null || annotation.getQuickFixes().isEmpty()) {
                int start = annotation.getStartOffset();
                int end = annotation.getEndOffset();
                builder.range(new TextRange(start, end)).create();
                return;
            }

            boolean range = true;
            for (Annotation.QuickFixInfo quickFixInfo : annotation.getQuickFixes()) {
                if (range) {
                    builder = builder.range(quickFixInfo.textRange);
                    range = false;
                }
                builder = builder.withFix(quickFixInfo.quickFix);
            }
            builder.create();
        });
//        eventManager.refreshAnnotations();
//        doAnnotate(RESULT);
    }

    @Nullable
    protected Annotation createAnnotation(Editor editor, AnnotationHolder holder, Diagnostic diagnostic) {
        final int start = DocumentUtils.LSPPosToOffset(editor, diagnostic.getRange().getStart());
        final int end = DocumentUtils.LSPPosToOffset(editor, diagnostic.getRange().getEnd());
        if (start > end) {
            return null;
        }
        final TextRange range = new TextRange(start, end);

        holder.newAnnotation(lspToIntellijAnnotationsMap.get(diagnostic.getSeverity()), diagnostic.getMessage())
                .range(range)
                .create();

        SmartList<Annotation> asList = (SmartList<Annotation>) holder;
        return asList.get(asList.size() - 1);
    }

    private void createAnnotations(AnnotationHolder holder, EditorEventManager eventManager) {
        final List<Diagnostic> diagnostics = eventManager.getDiagnostics();
        final Editor editor = eventManager.editor;

        List<Annotation> annotations = new ArrayList<>();
        diagnostics.forEach(d -> {
            Annotation annotation = createAnnotation(editor, holder, d);
            if (annotation != null) {
                if (d.getTags() != null && d.getTags().contains(DiagnosticTag.Deprecated)) {
                    annotation.setHighlightType(ProblemHighlightType.LIKE_DEPRECATED);
                }
                annotations.add(annotation);
            }
        });

        eventManager.setAnnotations(annotations);
        eventManager.setAnonHolder(holder);
    }
}
