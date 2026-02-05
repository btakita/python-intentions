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

        val generator = PyElementGenerator.getInstance(element.project)
        val langLevel = LanguageLevel.forElement(element)

        when (value) {
            is PyListLiteralExpression -> {
                val remaining = value.elements.filter {
                    !(it is PyStringLiteralExpression && it.stringValue == name)
                }
                val newAllText = if (remaining.isEmpty()) {
                    "__all__ = []"
                } else {
                    "__all__ = [${remaining.joinToString(", ") { it.text }}]"
                }
                val newStatement = generator.createFromText(langLevel, PyAssignmentStatement::class.java, newAllText)
                allAssignment.replace(newStatement)
            }
            is PyTupleExpression -> {
                val remaining = value.elements.filter {
                    !(it is PyStringLiteralExpression && it.stringValue == name)
                }
                val newAllText = if (remaining.isEmpty()) {
                    "__all__ = ()"
                } else {
                    "__all__ = (${remaining.joinToString(", ") { it.text }})"
                }
                val newStatement = generator.createFromText(langLevel, PyAssignmentStatement::class.java, newAllText)
                allAssignment.replace(newStatement)
            }
            is PyParenthesizedExpression -> {
                val inner = value.containedExpression
                if (inner is PyTupleExpression) {
                    val remaining = inner.elements.filter {
                        !(it is PyStringLiteralExpression && it.stringValue == name)
                    }
                    val newAllText = if (remaining.isEmpty()) {
                        "__all__ = ()"
                    } else {
                        "__all__ = (${remaining.joinToString(", ") { it.text }})"
                    }
                    val newStatement = generator.createFromText(langLevel, PyAssignmentStatement::class.java, newAllText)
                    allAssignment.replace(newStatement)
                }
            }
        }
    }
}
