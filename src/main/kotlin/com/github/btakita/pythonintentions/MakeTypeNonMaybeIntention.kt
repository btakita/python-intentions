package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class MakeTypeNonMaybeIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Make type non-maybe (remove None)"

    override fun getText(): String = "Make type non-maybe (remove None)"

    private sealed class MaybeTypeInfo {
        class OptionalForm(val subscriptionExpr: PySubscriptionExpression) : MaybeTypeInfo()
        class PipeForm(val binaryExpr: PyBinaryExpression, val nonNoneOperands: List<PyExpression>) : MaybeTypeInfo()
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return detectMaybeType(element) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val info = detectMaybeType(element) ?: return
        val generator = PyElementGenerator.getInstance(project)
        val langLevel = LanguageLevel.forElement(element)

        when (info) {
            is MaybeTypeInfo.OptionalForm -> {
                val indexExpr = info.subscriptionExpr.indexExpression ?: return
                val newExpr = generator.createExpressionFromText(langLevel, indexExpr.text)
                val replaced = info.subscriptionExpr.replace(newExpr)
                val file = replaced.containingFile as? PyFile ?: return
                removeUnusedImport(file, "Optional", "typing")
            }
            is MaybeTypeInfo.PipeForm -> {
                val newText = info.nonNoneOperands.joinToString(" | ") { it.text }
                val newExpr = generator.createExpressionFromText(langLevel, newText)
                info.binaryExpr.replace(newExpr)
            }
        }
    }

    private fun detectMaybeType(element: PsiElement): MaybeTypeInfo? {
        // Check for Optional[X] form
        val subscriptionExpr = PsiTreeUtil.getParentOfType(
            element, PySubscriptionExpression::class.java, false
        )
        if (subscriptionExpr != null && isTypingOptional(subscriptionExpr)) {
            return MaybeTypeInfo.OptionalForm(subscriptionExpr)
        }

        // Check for X | None pipe form
        val binaryExpr = findOutermostBinaryOr(element)
        if (binaryExpr != null) {
            val operands = collectPipeOperands(binaryExpr)
            val nonNoneOperands = operands.filter { !isNone(it) }
            if (operands.any { isNone(it) } && nonNoneOperands.isNotEmpty()) {
                return MaybeTypeInfo.PipeForm(binaryExpr, nonNoneOperands)
            }
        }

        return null
    }

    private fun isTypingOptional(expr: PySubscriptionExpression): Boolean {
        val operand = expr.operand
        if (operand !is PyReferenceExpression) return false
        if (operand.name != "Optional") return false
        val resolved = operand.reference.resolve() ?: return false
        val file = resolved.containingFile ?: return false
        return file.name == "typing.pyi" || isInTypingModule(file)
    }

    private fun isInTypingModule(file: com.intellij.psi.PsiFile): Boolean {
        val path = file.virtualFile?.path ?: return false
        return path.contains("/typing") || path.contains("\\typing")
    }

    private fun findOutermostBinaryOr(element: PsiElement): PyBinaryExpression? {
        var candidate = PsiTreeUtil.getParentOfType(
            element, PyBinaryExpression::class.java, false
        ) ?: return null

        if (candidate.psiOperator?.text != "|") return null

        while (true) {
            val parent = candidate.parent
            if (parent is PyBinaryExpression && parent.psiOperator?.text == "|") {
                candidate = parent
            } else {
                break
            }
        }

        val operands = collectPipeOperands(candidate)
        if (!operands.any { isNone(it) }) return null

        return candidate
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

    private fun removeUnusedImport(file: PyFile, name: String, module: String) {
        val importStatements = file.importBlock ?: return
        for (importStatement in importStatements) {
            if (importStatement is PyFromImportStatement) {
                if (importStatement.importSourceQName?.toString() == module) {
                    val importElements = importStatement.importElements
                    val targetElement = importElements.find { it.importedQName?.toString() == name }
                    if (targetElement != null && !isNameUsedInFile(file, name, targetElement)) {
                        if (importElements.size == 1) {
                            importStatement.delete()
                        } else {
                            targetElement.delete()
                        }
                    }
                }
            }
        }
    }

    private fun isNameUsedInFile(file: PyFile, name: String, excludeElement: PsiElement): Boolean {
        var found = false
        file.accept(object : com.intellij.psi.PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (found) return
                if (element !== excludeElement && element is PyReferenceExpression &&
                    element.name == name && element.qualifier == null
                ) {
                    found = true
                }
                super.visitElement(element)
            }
        })
        return found
    }
}
