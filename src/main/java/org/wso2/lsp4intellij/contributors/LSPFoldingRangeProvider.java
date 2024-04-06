/*
 * Copyright (c) 2013, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.lsp4intellij.contributors;

import com.esotericsoftware.minlog.Log;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.CustomFoldingBuilder;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.JsonRpcException;
import org.jetbrains.annotations.NotNull;
import org.wso2.lsp4intellij.client.languageserver.wrapper.LanguageServerWrapper;
import org.wso2.lsp4intellij.requests.Timeouts;
import org.wso2.lsp4intellij.utils.DocumentUtils;
import org.wso2.lsp4intellij.utils.FileUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.wso2.lsp4intellij.requests.Timeout.getTimeout;

public class LSPFoldingRangeProvider extends CustomFoldingBuilder {

    protected Logger LOG = Logger.getInstance(LSPFoldingRangeProvider.class);
    private Editor editor;
    private LanguageServerWrapper wrapper;

    private static String collapseText = "...";

    private String collapseTextPrev = collapseText;

    @Override
    protected void buildLanguageFoldRegions(@NotNull List<FoldingDescriptor> descriptors, @NotNull PsiElement root, @NotNull Document document, boolean quick) {
        // if quick flag is set, we do nothing here
        if (quick) {
            return;
        }

        if (editor == null || wrapper == null) {
            PsiFile psiFile = root.getContainingFile();
            editor = FileUtils.editorFromPsiFile(psiFile);
            wrapper = LanguageServerWrapper.forVirtualFile(psiFile.getVirtualFile(), root.getProject());
        }

        URI fileUri = root.getContainingFile().getVirtualFile().toNioPath().toUri();

        // Convert the URI to a URL string.
        String url = fileUri.toString();
        System.out.println(url);
        TextDocumentIdentifier textDocumentIdentifier = new TextDocumentIdentifier(url);
        FoldingRangeRequestParams params = new FoldingRangeRequestParams(textDocumentIdentifier);
        CompletableFuture<List<FoldingRange>> future = wrapper.getRequestManager().foldingRange(params);

        if (future != null) {
            try {
                List<FoldingRange> foldingRanges = future.get(getTimeout(Timeouts.FOLDING), TimeUnit.MILLISECONDS);
                wrapper.notifySuccess(Timeouts.FOLDING);

                for (FoldingRange foldingRange : foldingRanges) {
//                    int start = getStartOffset(foldingRange, document);
//                    int end = getEndOffset(foldingRange, document);
                    int start = calculateStartOffset(root, foldingRange.getStartLine(), foldingRange.getStartCharacter());
                    int end = document.getLineEndOffset(foldingRange.getEndLine());
                    System.out.println(root.getContainingFile().getVirtualFile().getName());
                    System.out.println("Start: " + start + " End: " + end);
                    if (end - start <= 0) {
                        continue;
                    }
                    if (collapseText == null){
                        collapseText = foldingRange.getCollapsedText();
                    }
                    if (collapseText != null) {
                        descriptors.add(new FoldingDescriptor(root.getNode(), new TextRange(start, end), null, collapseText));
                        System.out.println(descriptors);
                    } else {
                        descriptors.add(new FoldingDescriptor(root.getNode(), new TextRange(start, end)));
                    }
                    collapseText = collapseTextPrev;
                }
            } catch (TimeoutException | InterruptedException e) {
                LOG.warn(e);
                wrapper.notifyFailure(Timeouts.FOLDING);
            } catch (JsonRpcException | ExecutionException e) {
                LOG.warn(e);
                wrapper.crashed(e);
            }
        }
    }

    private int getEndOffset(@NotNull FoldingRange foldingRange, @NotNull Document document) {
        // EndCharacter is optional. When missing, it should be set to the length of the end line.
        if (foldingRange.getEndCharacter() == null) {
            return document.getLineEndOffset(foldingRange.getEndLine());
        }

        return DocumentUtils.LSPPosToOffset(editor, new Position(foldingRange.getEndLine(), foldingRange.getEndCharacter()));
    }


    private int getStartOffset(@NotNull FoldingRange foldingRange, @NotNull Document document) {
        // StartCharacter is optional. When missing, it should be set to the length of the start line.
        if (foldingRange.getStartCharacter() == null) {
            return document.getLineEndOffset(foldingRange.getStartLine());
        } else {
            System.out.println("lsp offfset ");
            System.out.println(foldingRange.getStartLine());
            System.out.println(foldingRange.getStartCharacter());
            System.out.println(DocumentUtils.LSPPosToOffset(editor, new Position(foldingRange.getStartLine(), foldingRange.getStartCharacter())));
            return DocumentUtils.LSPPosToOffset(editor, new Position(foldingRange.getStartLine(), foldingRange.getStartCharacter()));
        }
    }

    @Override
    protected String getLanguagePlaceholderText(@NotNull ASTNode node, @NotNull TextRange range) {
        return null;
    }

    @Override
    protected boolean isRegionCollapsedByDefault(@NotNull ASTNode node) {
        return false;
    }


    public int calculateStartOffset(@NotNull PsiElement root, int lineNumber, int charNumber) {
        Document document = FileDocumentManager.getInstance().getDocument(root.getContainingFile().getVirtualFile());

        if (document == null) {
            Log.warn("Document cannot be null");
            return -1;
        }

        int lineStartOffset = document.getLineStartOffset(lineNumber);
        int lineEndOffset = document.getLineEndOffset(lineNumber);

        // Ensure the character number is within the bounds of the line.
        int offset = lineStartOffset + charNumber; // Adjusted for 0-based index

        int offsetCopy = offset;

        // Check if the character at the offset is a '{'. If not, move forward until '{' is found or the line ends.
        while (offset < lineEndOffset) {
            char currentChar = document.getCharsSequence().charAt(offset);
            if (currentChar == '{') {
                if (document.getCharsSequence().charAt(offset+1) == '|'){
                    collapseText = "{|...";
                } else {
                    collapseText = "{...";
                }
                return offset;
            }
            offset++;
        }

        int offsetCopy1 = offsetCopy;

        while (offsetCopy1 < lineEndOffset) {
            // Ensure we don't exceed the document's bounds when checking the sequence "return"
            if (offsetCopy1 + 5 < lineEndOffset) {
                CharSequence sequence = document.getCharsSequence().subSequence(offsetCopy1, offsetCopy1 + 6);
                if (sequence.toString().equals("return")) {
                    return offsetCopy1 + 6; // Move the offset to the character after "return"
                }
            }
            offsetCopy1++; // Move to the next character
        }



        return lineEndOffset;
    }
}
