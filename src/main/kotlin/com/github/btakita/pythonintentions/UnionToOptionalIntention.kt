package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class UnionToOptionalIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Replace X | None with Optional[X]"

    override fun getText(): String = "Replace with Optional[X]"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val binaryExpr = findOutermostBinaryOr(element) ?: return false
        val operands = collectPipeOperands(binaryExpr)
        return operands.any { isNone(it) } && operands.any { !isNone(it) }
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val binaryExpr = findOutermostBinaryOr(element) ?: return
        val operands = collectPipeOperands(binaryExpr)
        val nonNoneTypes = operands.filter { !isNone(it) }
        if (nonNoneTypes.isEmpty()) return

        val generator = PyElementGenerator.getInstance(project)
        val langLevel = LanguageLevel.forElement(element)
        val file = element.containingFile as? PyFile ?: return

        val newText: String
        val needsUnion: Boolean

        if (nonNoneTypes.size == 1) {
            newText = "Optional[${nonNoneTypes[0].text}]"
            needsUnion = false
        } else {
            val innerText = nonNoneTypes.joinToString(", ") { it.text }
            newText = "Optional[Union[$innerText]]"
            needsUnion = true
        }

        val newExpr = generator.createExpressionFromText(langLevel, newText)
        binaryExpr.replace(newExpr)

        addImportIfNeeded(file, "Optional", "typing")
        if (needsUnion) {
            addImportIfNeeded(file, "Union", "typing")
        }
    }

    private fun findOutermostBinaryOr(element: PsiElement): PyBinaryExpression? {
        var candidate = PsiTreeUtil.getParentOfType(
            element, PyBinaryExpression::class.java, false
        ) ?: return null

        if (!isBitwiseOr(candidate)) return null

        // Walk up to outermost contiguous | expression
        while (true) {
            val parent = candidate.parent
            if (parent is PyBinaryExpression && isBitwiseOr(parent)) {
                candidate = parent
            } else {
                break
            }
        }

        // Must contain None
        val operands = collectPipeOperands(candidate)
        if (!operands.any { isNone(it) }) return null

        return candidate
    }

    private fun isBitwiseOr(expr: PyBinaryExpression): Boolean {
        return expr.psiOperator?.text == "|"
    }

    private fun collectPipeOperands(expr: PyExpression): List<PyExpression> {
        if (expr is PyBinaryExpression && expr.psiOperator?.text == "|") {
            val left = expr.leftExpression
            val right = expr.rightExpression ?: return collectPipeOperands(left)
            return collectPipeOperands(left) + collectPipeOperands(right)
        }
        return listOf(expr)
    }

    private fun isNone(expr: PyExpression): Boolean {
        if (expr is PyNoneLiteralExpression) return true
        if (expr is PyReferenceExpression && expr.name == "None") return true
        return false
    }

    private fun addImportIfNeeded(file: PyFile, name: String, module: String) {
        val existingImport = file.importBlock?.any { statement ->
            statement is PyFromImportStatement &&
                    statement.importSourceQName?.toString() == module &&
                    statement.importElements.any { it.importedQName?.toString() == name }
        } ?: false

        if (!existingImport) {
            val generator = PyElementGenerator.getInstance(file.project)
            val importStatement = generator.createFromImportStatement(
                LanguageLevel.forElement(file), module, name, null
            )
            file.addBefore(importStatement, file.statements.firstOrNull() ?: file.lastChild)
        }
    }
}
