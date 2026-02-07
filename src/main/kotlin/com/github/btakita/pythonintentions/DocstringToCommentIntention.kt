package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyExpressionStatement
import com.jetbrains.python.psi.PyStringLiteralExpression

class DocstringToCommentIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Convert docstring to comment"

    override fun getText(): String = "Convert docstring to comment"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val stringExpr = PsiTreeUtil.getParentOfType(
            element, PyStringLiteralExpression::class.java, false
        ) ?: return false
        return stringExpr.parent is PyExpressionStatement
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val stringExpr = PsiTreeUtil.getParentOfType(
            element, PyStringLiteralExpression::class.java, false
        ) ?: return
        val exprStmt = stringExpr.parent as? PyExpressionStatement ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile) ?: return

        // Get indentation
        val startOffset = exprStmt.textRange.startOffset
        val lineNumber = document.getLineNumber(startOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val indent = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, startOffset))

        // Get the string content (decoded, without quotes)
        val content = stringExpr.stringValue

        // Split into lines and convert to comments
        val lines = content.split("\n")
        val commentLines = lines.map { line ->
            if (line.isBlank()) "${indent}#" else "${indent}# ${line}"
        }

        val commentText = commentLines.joinToString("\n")

        // Replace the expression statement
        val replaceStart = lineStartOffset
        val replaceEnd = exprStmt.textRange.endOffset
        document.replaceString(replaceStart, replaceEnd, commentText)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
