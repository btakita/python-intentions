package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class PipeToUnionSubscriptionIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Replace X | Y with Union[X, Y]"

    override fun getText(): String = "Replace with Union[X, Y]"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val binaryExpr = findOutermostBinaryOr(element) ?: return false
        val operands = collectPipeOperands(binaryExpr)
        return operands.size >= 2
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val binaryExpr = findOutermostBinaryOr(element) ?: return
        val operands = collectPipeOperands(binaryExpr)
        if (operands.size < 2) return

        val innerText = operands.joinToString(", ") { it.text }
        val newText = "Union[$innerText]"

        val generator = PyElementGenerator.getInstance(project)
        val injectionManager = InjectedLanguageManager.getInstance(project)
        if (injectionManager.isInjectedFragment(element.containingFile)) {
            val hostElement = injectionManager.getInjectionHost(element)
            if (hostElement is PyStringLiteralExpression) {
                val hostText = hostElement.text
                val content = hostElement.stringValue
                val newContent = content.replace(binaryExpr.text, newText)
                val newHostText = hostText.replace(content, newContent)
                val newStringExpr = generator.createExpressionFromText(
                    LanguageLevel.forElement(hostElement), newHostText
                )
                hostElement.replace(newStringExpr)
                val file = hostElement.containingFile as? PyFile ?: return
                addImportIfNeeded(file, "Union", "typing")
                return
            }
        }

        val file = element.containingFile as? PyFile ?: return
        val newExpr = generator.createExpressionFromText(
            LanguageLevel.forElement(element), newText
        )
        binaryExpr.replace(newExpr)
        addImportIfNeeded(file, "Union", "typing")
    }

    private fun findOutermostBinaryOr(element: PsiElement): PyBinaryExpression? {
        var candidate = PsiTreeUtil.getParentOfType(
            element, PyBinaryExpression::class.java, false
        ) ?: return null

        if (!isBitwiseOr(candidate)) return null

        while (true) {
            val parent = candidate.parent
            if (parent is PyBinaryExpression && isBitwiseOr(parent)) {
                candidate = parent
            } else {
                break
            }
        }

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
            val pep8Index = DunderAllUtil.findPep8InsertionIndex(file)
            val anchor = file.statements.getOrNull(pep8Index) ?: file.lastChild
            file.addBefore(importStatement, anchor)
        }
    }
}