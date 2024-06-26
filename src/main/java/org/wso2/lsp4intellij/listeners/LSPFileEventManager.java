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
package org.wso2.lsp4intellij.listeners;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileMoveEvent;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.FileChangeType;
import org.eclipse.lsp4j.FileEvent;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.IntellijLanguageClient;
import org.wso2.lsp4intellij.client.languageserver.ServerStatus;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.editor.EditorEventManagerBase;
import org.wso2.lsp4intellij.utils.ApplicationUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.wso2.lsp4intellij.utils.FileUtils.searchFiles;

class LSPFileEventManager {

    private static final Logger LOG = Logger.getInstance(LSPFileEventManager.class);

    /**
     * Indicates that a document will be saved
     *
     * @param doc The document
     */
    static void willSave(Document doc) {
        String uri = FileUtils.VFSToURI(FileDocumentManager.getInstance().getFile(doc));
        EditorEventManagerBase.willSave(uri);
    }

    /**
     * Indicates that all documents will be saved
     */
    static void willSaveAllDocuments() {
        EditorEventManagerBase.willSaveAll();
    }

    /**
     * Called when a file is changed. Notifies the server if this file was watched.
     *
     * @param file The file
     */
    static void fileChanged(VirtualFile file) {

        if (!FileUtils.isFileSupported(file)) {
            return;
        }
        String uri = FileUtils.VFSToURI(file);
        if (uri == null) {
            return;
        }

        ApplicationUtils.invokeAfterPsiEvents(() -> {
            EditorEventManagerBase.documentSaved(uri);
            FileUtils.findProjectsFor(file).forEach(p -> changedConfiguration(uri,
                FileUtils.projectToUri(p), FileChangeType.Changed));
        });
    }

    /**
     * Called when a file is moved. Notifies the server if this file was watched.
     *
     * @param event The file move event
     */
    static void fileMoved(VirtualFileMoveEvent event) {
        try {
            VirtualFile file = event.getFile();
            if (!FileUtils.isFileSupported(file)) {
                return;
            }

            String newFileUri = FileUtils.VFSToURI(file);
            String oldParentUri = FileUtils.VFSToURI(event.getOldParent());
            if (newFileUri == null || oldParentUri == null) {
                return;
            }
            String oldFileUri = String.format("%s/%s", oldParentUri, event.getFileName());
            closeAndReopenAffectedFile(file, oldFileUri);
        } catch (Exception e) {
            LOG.warn("LSP file move event failed due to :", e);
        }
    }

    /**
     * Called when a file is deleted. Notifies the server if this file was watched.
     *
     * @param file The file
     */
    static void fileDeleted(VirtualFile file) {
        if (!FileUtils.isFileSupported(file)) {
            return;
        }
        String uri = FileUtils.VFSToURI(file);
        if (uri == null) {
            return;
        }
        ApplicationUtils.invokeAfterPsiEvents(() -> {
            FileUtils.findProjectsFor(file).forEach(p -> changedConfiguration(uri,
                FileUtils.projectToUri(p), FileChangeType.Deleted));
        });
    }

    /**
     * Called when a file is renamed. Notifies the server if this file was watched.
     *
     * @param oldFileName The old file name
     * @param newFileName the new file name
     */
    static void fileRenamed(String oldFileName, String newFileName) {
        ApplicationUtils.invokeAfterPsiEvents(() -> {
            try {
                // Getting the right file is not trivial here since we only have the file name. Since we have to iterate over
                // all opened projects and filter based on the file name.
                Set<VirtualFile> files = Arrays.stream(ProjectManager.getInstance().getOpenProjects())
                    .flatMap(p -> Arrays.stream(searchFiles(newFileName, p)))
                    .map(PsiFile::getVirtualFile)
                    .collect(Collectors.toSet());

                for (VirtualFile file : files) {
                    if (!FileUtils.isFileSupported(file)) {
                        continue;
                    }
                    String newFileUri = FileUtils.VFSToURI(file);
                    String oldFileUri = newFileUri.replace(file.getName(), oldFileName);
                    closeAndReopenAffectedFile(file, oldFileUri);
                }
            } catch (Exception e) {
                LOG.warn("LSP file rename event failed due to : ", e);
            }
        });
    }

    private static void closeAndReopenAffectedFile(VirtualFile file, String oldFileUri) {
        String newFileUri = FileUtils.VFSToURI(file);

        // Notifies the language server.
        FileUtils.findProjectsFor(file).forEach(p -> changedConfiguration(oldFileUri,
            FileUtils.projectToUri(p), FileChangeType.Deleted));
        FileUtils.findProjectsFor(file).forEach(p -> changedConfiguration(newFileUri,
            FileUtils.projectToUri(p), FileChangeType.Created));

        FileUtils.findProjectsFor(file).forEach(p -> {
            // Detaches old file from the wrappers.
            Set<LanguageServerWrapper> wrappers = IntellijLanguageClient.getAllServerWrappersFor(FileUtils.projectToUri(p));
            wrappers.forEach(wrapper -> wrapper.disconnect(oldFileUri, FileUtils.projectToUri(p)));
            if (!newFileUri.equals(oldFileUri)) {
                // TODO: abort if the file was not opened prior to this operation
                // Re-open file to so that the new editor will be connected to the language server.
                FileEditorManager fileEditorManager = FileEditorManager.getInstance(p);
                ApplicationUtils.invokeLater(() -> {
                    fileEditorManager.closeFile(file);
                    // TODO: only focus if the file was previously already focused
                    fileEditorManager.openFile(file, true);
                });
            }
        });
    }

    /**
     * Called when a file is created. Notifies the server if needed.
     *
     * @param file The file
     */
    static void fileCreated(VirtualFile file) {
        if (!FileUtils.isFileSupported(file)) {
            return;
        }
        String uri = FileUtils.VFSToURI(file);
        if (uri != null) {
            ApplicationUtils.invokeAfterPsiEvents(() -> {
                FileUtils.findProjectsFor(file).forEach(p -> changedConfiguration(uri,
                    FileUtils.projectToUri(p), FileChangeType.Created));
            });
        }
    }

    private static void changedConfiguration(String uri, String projectUri, FileChangeType typ) {
        ApplicationUtils.pool(() -> {
            DidChangeWatchedFilesParams params = getDidChangeWatchedFilesParams(uri, typ);
            Set<LanguageServerWrapper> wrappers = IntellijLanguageClient.getAllServerWrappersFor(projectUri);
            for (LanguageServerWrapper wrapper : wrappers) {
                if (wrapper.getRequestManager() != null
                        && wrapper.getStatus() == ServerStatus.INITIALIZED) {
                    wrapper.getRequestManager().didChangeWatchedFiles(params);
                }
            }
        });
    }

    @NotNull
    private static DidChangeWatchedFilesParams getDidChangeWatchedFilesParams(String fileUri, FileChangeType typ) {
        List<FileEvent> event = new ArrayList<>();
        event.add(new FileEvent(fileUri, typ));
        return new DidChangeWatchedFilesParams(event);
    }
}
