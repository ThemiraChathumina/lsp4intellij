/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.lsp4intellij.contributors.annotator;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
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
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;

public class LSPAnnotator extends ExternalAnnotator<Object, Object> {

    private static final Logger LOG = Logger.getInstance(LSPAnnotator.class);
    private static final Object RESULT = new Object();
    private static final HashMap<DiagnosticSeverity, HighlightSeverity> lspToIntellijAnnotationsMap = new HashMap<>();

    private static final HashMap<VirtualFile,Boolean> problemFiles = new HashMap<>();

    static {
        lspToIntellijAnnotationsMap.put(DiagnosticSeverity.Error, HighlightSeverity.ERROR);
        lspToIntellijAnnotationsMap.put(DiagnosticSeverity.Warning, HighlightSeverity.WARNING);

        // seem flipped, but just different semantics lsp<->intellij. Hint is rendered without any squiggle
        lspToIntellijAnnotationsMap.put(DiagnosticSeverity.Information, HighlightSeverity.WEAK_WARNING);
        lspToIntellijAnnotationsMap.put(DiagnosticSeverity.Hint, HighlightSeverity.INFORMATION);
    }

    @Nullable
    @Override
    public Object collectInformation(@NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors) {

        try {
            VirtualFile virtualFile = file.getVirtualFile();

            // If the file is not supported, we skips the annotation by returning null.
            if (!FileUtils.isFileSupported(virtualFile) || !IntellijLanguageClient.isExtensionSupported(virtualFile)) {
                return null;
            }
            EditorEventManager eventManager = EditorEventManagerBase.forEditor(editor);

            if (eventManager == null) {
                return null;
            }
//            System.out.println("annot");
            return RESULT;
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    @Override
    public Object doAnnotate(Object collectedInfo) {
        return RESULT;
    }

    @Override
    public void apply(@NotNull PsiFile file, Object annotationResult, @NotNull AnnotationHolder holder) {
//        System.out.println("apply");
        LanguageServerWrapper languageServerWrapper = LanguageServerWrapper.forVirtualFile(file.getVirtualFile(), file.getProject());
        if (languageServerWrapper == null || languageServerWrapper.getStatus() != ServerStatus.INITIALIZED) {
            return;
        }

        VirtualFile virtualFile = file.getVirtualFile();
        WolfTheProblemSolver.getInstance(file.getProject()).clearProblems(virtualFile);
        if (FileUtils.isFileSupported(virtualFile) && IntellijLanguageClient.isExtensionSupported(virtualFile)) {
            String uri = FileUtils.VFSToURI(virtualFile);
            // TODO annotations are applied to a file / document not to an editor. so store them by file and not by editor..
            EditorEventManager eventManager = EditorEventManagerBase.forUri(uri);

            if (eventManager.isDiagnosticSyncRequired()) {
                try {
                    createAnnotations(holder, eventManager);
                } catch (ConcurrentModificationException e) {
                    // Todo - Add proper fix to handle concurrent modifications gracefully.
                    LOG.warn("Error occurred when updating LSP code actions due to concurrent modifications.", e);
                } catch (Throwable t) {
                    LOG.warn("Error occurred when updating LSP code actions.", t);
                }
                eventManager.requestAndShowCodeActions();
            } else {
                try {
                    updateSilentAnnotations(holder, eventManager);
                    updateAnnotations(holder, eventManager);
                } catch (ConcurrentModificationException e) {
                    // Todo - Add proper fix to handle concurrent modifications gracefully.
                    LOG.warn("Error occurred when updating LSP diagnostics due to concurrent modifications.", e);
                } catch (Throwable t) {
                    LOG.warn("Error occurred when updating LSP diagnostics.", t);
                }
            }
//            System.out.println("size: " + eventManager.getAnnotations().size());
//            for (Annotation annotation : eventManager.getAnnotations()) {
//                System.out.println(annotation.getMessage());
//                System.out.println(annotation.getHighlightType());
//            }
        }
//        Project project = file.getProject();
//        ProjectView.getInstance(project).refresh();
        System.out.println("problemFiles: " + problemFiles);
    }

    private void updateSilentAnnotations(AnnotationHolder holder, EditorEventManager eventManager) {
        final List<Tuple3<HighlightSeverity,TextRange, LSPCodeActionFix>> annotations = eventManager.getSilentAnnotations();
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

            Editor editor = eventManager.editor;

            VirtualFile file = FileUtils.virtualFileFromEditor(editor);
//        System.out.println("file: " + file);
//            Problem problem = WolfTheProblemSolver.getInstance(editor.getProject()).convertToProblem(file,0,0,new String[]{annotation.getMessage()});
//WolfTheProblemSolver.getInstance(editor.getProject()).queue(file);
//            ArrayList<Problem> problems = new ArrayList<>();
//            problems.add(problem);
//            WolfTheProblemSolver.getInstance(editor.getProject()).weHaveGotNonIgnorableProblems(file, problems);
//            System.out.println(WolfTheProblemSolver.getInstance(editor.getProject()).isProblemFile(file));


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
        eventManager.refreshAnnotations();
        // trigger clicking alt+enter
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

        // create a new problem and report it to wolf problem solver.
//        VirtualFile file = FileUtils.virtualFileFromEditor(editor);
////        System.out.println("file: " + file);
//        Problem problem = WolfTheProblemSolver.getInstance(editor.getProject()).convertToProblem(file,0,0,new String[]{diagnostic.getMessage()});
//
//        ArrayList<Problem> problems = new ArrayList<>();
//        problems.add(problem);
//        WolfTheProblemSolver.getInstance(editor.getProject()).weHaveGotNonIgnorableProblems(file, problems);
//        System.out.println(WolfTheProblemSolver.getInstance(editor.getProject()).isProblemFile(file));
//        problemFiles.put(file, true);

        SmartList<Annotation> asList = (SmartList<Annotation>) holder;
        return asList.get(asList.size() - 1);
    }

    private void createAnnotations(AnnotationHolder holder, EditorEventManager eventManager) {
        final List<Diagnostic> diagnostics = eventManager.getDiagnostics();
        final Editor editor = eventManager.editor;

        VirtualFile file = FileUtils.virtualFileFromEditor(editor);
        if (diagnostics == null || diagnostics.isEmpty()) {
            problemFiles.put(file, false);
        } else {
            problemFiles.put(file, true);
        }

        for (VirtualFile virtualFile : problemFiles.keySet()) {
            if (!problemFiles.get(virtualFile)) {
                WolfTheProblemSolver.getInstance(editor.getProject()).clearProblems(virtualFile);
            } else {
//                Problem problem = WolfTheProblemSolver.getInstance(editor.getProject()).convertToProblem(file,0,0,new String[]{""});
//                ArrayList<Problem> problems = new ArrayList<>();
//                problems.add(problem);
//                WolfTheProblemSolver.getInstance(editor.getProject()).reportProblems(file, problems);
            }
        }

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
