package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

class RemoveFromAllIntention : PsiElementBaseIntentionAction() {

    @Volatile
    private var detectedName: String = ""

    override fun getFamilyName(): String = "Remove from __all__"

    override fun getText(): String =
        if (detectedName.isNotEmpty()) "Remove '$detectedName' from __all__" else "Remove from __all__"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile as? PyFile ?: return false
        val name = DunderAllUtil.findTopLevelName(element) ?: return false
        if (!DunderAllUtil.isNameInAll(file, name)) return false
        detectedName = name
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile as? PyFile ?: return
        val name = DunderAllUtil.findTopLevelName(element) ?: return
        val allAssignment = DunderAllUtil.findAllAssignment(file) ?: return
        val value = allAssignment.assignedValue ?: return

        val remaining = when (value) {
            is PyListLiteralExpression -> value.elements.filter {
                !(it is PyStringLiteralExpression && it.stringValue == name)
            }
            is PyTupleExpression -> value.elements.filter {
                !(it is PyStringLiteralExpression && it.stringValue == name)
            }
            is PyParenthesizedExpression -> {
                val inner = value.containedExpression
                if (inner is PyTupleExpression) {
                    inner.elements.filter {
                        !(it is PyStringLiteralExpression && it.stringValue == name)
                    }
                } else return
            }
            else -> return
        }

        if (remaining.isEmpty()) {
            deleteAssignmentAndSurroundingBlanks(allAssignment)
        } else {
            val generator = PyElementGenerator.getInstance(element.project)
            val langLevel = LanguageLevel.forElement(element)
            val bracket = if (value is PyTupleExpression || value is PyParenthesizedExpression) "()" else "[]"
            val newAllText = "__all__ = ${bracket[0]}${remaining.joinToString(", ") { it.text }}${bracket[1]}"
            val newStatement = generator.createFromText(langLevel, PyAssignmentStatement::class.java, newAllText)
            allAssignment.replace(newStatement)
        }
    }

    private fun deleteAssignmentAndSurroundingBlanks(assignment: PsiElement) {
        val document = com.intellij.psi.PsiDocumentManager.getInstance(assignment.project)
            .getDocument(assignment.containingFile) ?: return
        val text = document.text
        val startOffset = assignment.textRange.startOffset
        val endOffset = assignment.textRange.endOffset
        // Expand backwards to consume the preceding blank line(s)
        var deleteStart = startOffset
        while (deleteStart > 0 && text[deleteStart - 1] == '\n') {
            deleteStart--
        }
        // But keep one newline if there's content before (to end the previous line)
        if (deleteStart > 0) {
            deleteStart++ // keep one \n
        }
        // Expand forward to consume the trailing newline
        var deleteEnd = endOffset
        while (deleteEnd < text.length && text[deleteEnd] == '\n') {
            deleteEnd++
        }
        // But keep one newline if there's content after (to start the next line)
        if (deleteEnd < text.length && deleteStart > 0) {
            deleteEnd-- // keep one \n
        }
        document.deleteString(deleteStart, deleteEnd)
        com.intellij.psi.PsiDocumentManager.getInstance(assignment.project)
            .commitDocument(document)
    }
}
