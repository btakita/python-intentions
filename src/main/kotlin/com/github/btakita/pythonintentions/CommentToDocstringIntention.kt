package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace

class CommentToDocstringIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Convert comment to docstring"

    override fun getText(): String = "Convert comment to docstring"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return findComment(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val comment = findComment(element) ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(comment.containingFile) ?: return

        // Collect contiguous comment block
        val comments = mutableListOf(comment)

        // Scan upward
        var sibling = comment.prevSibling
        while (sibling != null) {
            if (sibling is PsiComment) {
                comments.add(0, sibling)
                sibling = sibling.prevSibling
            } else if (sibling is PsiWhiteSpace && isSingleLineBreak(sibling.text)) {
                val prev = sibling.prevSibling
                if (prev is PsiComment) {
                    comments.add(0, prev)
                    sibling = prev.prevSibling
                } else {
                    break
                }
            } else {
                break
            }
        }

        // Scan downward
        sibling = comment.nextSibling
        while (sibling != null) {
            if (sibling is PsiComment) {
                comments.add(sibling)
                sibling = sibling.nextSibling
            } else if (sibling is PsiWhiteSpace && isSingleLineBreak(sibling.text)) {
                val next = sibling.nextSibling
                if (next is PsiComment) {
                    comments.add(next)
                    sibling = next.nextSibling
                } else {
                    break
                }
            } else {
                break
            }
        }

        // Determine indentation from the first comment's line
        val firstOffset = comments.first().textRange.startOffset
        val lineNumber = document.getLineNumber(firstOffset)
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val indent = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, firstOffset))

        // Strip comment prefixes
        val lines = comments.map { c ->
            val text = c.text
            when {
                text.startsWith("# ") -> text.substring(2)
                text.startsWith("#") -> text.substring(1)
                else -> text
            }
        }

        // Build docstring
        val docstring = if (lines.size == 1) {
            "${indent}\"\"\"${lines[0]}\"\"\""
        } else {
            val innerLines = lines.joinToString("\n") { line ->
                if (line.isEmpty()) "" else "${indent}${line}"
            }
            "${indent}\"\"\"\n${innerLines}\n${indent}\"\"\""
        }

        // Replace the entire comment block range
        val replaceStart = lineStartOffset
        val endOffset = comments.last().textRange.endOffset
        document.replaceString(replaceStart, endOffset, docstring)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }

    private fun findComment(element: PsiElement): PsiComment? {
        if (element is PsiComment) return element
        if (element.parent is PsiComment) return element.parent as PsiComment
        // When caret is right after a comment (e.g., "#<caret>"), check previous sibling
        val prev = element.prevSibling
        if (prev is PsiComment) return prev
        return null
    }

    private fun isSingleLineBreak(text: String): Boolean {
        val newlineCount = text.count { it == '\n' }
        return newlineCount == 1 && text.all { it == '\n' || it == ' ' || it == '\t' }
    }
}
