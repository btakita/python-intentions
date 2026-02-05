package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

class AddToAllIntention : PsiElementBaseIntentionAction() {

    @Volatile
    private var detectedName: String = ""

    override fun getFamilyName(): String = "Add to __all__"

    override fun getText(): String =
        if (detectedName.isNotEmpty()) "Add '$detectedName' to __all__" else "Add to __all__"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile as? PyFile ?: return false
        val name = DunderAllUtil.findTopLevelName(element) ?: return false
        if (DunderAllUtil.isNameInAll(file, name)) return false
        detectedName = name
        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile as? PyFile ?: return
        val name = DunderAllUtil.findTopLevelName(element) ?: return
        val generator = PyElementGenerator.getInstance(project)
        val langLevel = LanguageLevel.forElement(element)

        val allAssignment = DunderAllUtil.findAllAssignment(file)
        if (allAssignment != null) {
            addToExistingAll(allAssignment, name, generator, langLevel)
        } else {
            createAllAssignment(file, name, generator, langLevel)
        }
    }

    private fun addToExistingAll(
        allAssignment: PyAssignmentStatement,
        name: String,
        generator: PyElementGenerator,
        langLevel: LanguageLevel
    ) {
        val value = allAssignment.assignedValue ?: return

        when (value) {
            is PyListLiteralExpression -> {
                val elements = value.elements
                val newAllText = if (elements.isEmpty()) {
                    "__all__ = [\"$name\"]"
                } else {
                    val existing = elements.joinToString(", ") { it.text }
                    "__all__ = [$existing, \"$name\"]"
                }
                val newStatement = generator.createFromText(langLevel, PyAssignmentStatement::class.java, newAllText)
                allAssignment.replace(newStatement)
            }
            is PyTupleExpression -> {
                val elements = value.elements
                val existing = elements.joinToString(", ") { it.text }
                val newAllText = "__all__ = ($existing, \"$name\")"
                val newStatement = generator.createFromText(langLevel, PyAssignmentStatement::class.java, newAllText)
                allAssignment.replace(newStatement)
            }
            is PyParenthesizedExpression -> {
                val inner = value.containedExpression
                if (inner is PyTupleExpression) {
                    val elements = inner.elements
                    val existing = elements.joinToString(", ") { it.text }
                    val newAllText = "__all__ = ($existing, \"$name\")"
                    val newStatement = generator.createFromText(langLevel, PyAssignmentStatement::class.java, newAllText)
                    allAssignment.replace(newStatement)
                }
            }
        }
    }

    private fun createAllAssignment(
        file: PyFile,
        name: String,
        generator: PyElementGenerator,
        langLevel: LanguageLevel
    ) {
        val newStatement = generator.createFromText(
            langLevel, PyAssignmentStatement::class.java, "__all__ = [\"$name\"]"
        )

        val statements = file.statements
        var insertionIndex = 0

        // Skip docstring (first statement if it's an expression statement containing a string literal)
        if (statements.isNotEmpty()) {
            val first = statements[0]
            if (first is PyExpressionStatement && first.expression is PyStringLiteralExpression) {
                insertionIndex = 1
            }
        }

        // Skip from __future__ imports
        while (insertionIndex < statements.size) {
            val stmt = statements[insertionIndex]
            if (stmt is PyFromImportStatement && stmt.importSourceQName?.toString() == "__future__") {
                insertionIndex++
            } else {
                break
            }
        }

        if (insertionIndex < statements.size) {
            val anchor = statements[insertionIndex]
            // Add a newline before the __all__ assignment
            val newline = generator.createNewLine()
            file.addBefore(newline, anchor)
            file.addBefore(newStatement, anchor)
            file.addBefore(generator.createNewLine(), anchor)
        } else {
            file.add(generator.createNewLine())
            file.add(newStatement)
        }
    }
}
