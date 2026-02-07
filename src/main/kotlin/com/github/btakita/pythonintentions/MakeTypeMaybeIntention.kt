package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class MakeTypeMaybeIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Make type maybe (add | None)"

    override fun getText(): String = "Make type maybe (add | None)"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val annotation = PsiTreeUtil.getParentOfType(
            element, PyAnnotation::class.java, false
        ) ?: return false
        val typeExpr = annotation.value ?: return false
        if (!PsiTreeUtil.isAncestor(typeExpr, element, false)) return false
        return !containsNone(typeExpr)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val annotation = PsiTreeUtil.getParentOfType(
            element, PyAnnotation::class.java, false
        ) ?: return
        val typeExpr = annotation.value ?: return

        val generator = PyElementGenerator.getInstance(project)
        val newText = "${typeExpr.text} | None"
        val newExpr = generator.createExpressionFromText(
            LanguageLevel.forElement(element), newText
        )
        typeExpr.replace(newExpr)
    }

    private fun containsNone(expr: PyExpression): Boolean {
        if (expr is PyNoneLiteralExpression) return true
        if (expr is PyReferenceExpression && expr.name == "None") return true
        if (expr is PySubscriptionExpression) {
            val operand = expr.operand
            if (operand is PyReferenceExpression) {
                if (operand.name == "Optional") {
                    return true
                }
                if (operand.name == "Union") {
                    val indexExpr = expr.indexExpression
                    if (indexExpr is PyTupleExpression) {
                        return indexExpr.elements.any { containsNone(it) }
                    }
                }
            }
        }
        if (expr is PyBinaryExpression && expr.psiOperator?.text == "|") {
            val left = expr.leftExpression
            val right = expr.rightExpression
            return containsNone(left) || (right != null && containsNone(right))
        }
        return false
    }
}
