package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class UnionSubscriptionToPipeIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Replace Union[X, Y] with X | Y"

    override fun getText(): String = "Replace with X | Y (PEP 604)"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val subscriptionExpr = PsiTreeUtil.getParentOfType(
            element, PySubscriptionExpression::class.java, false
        ) ?: return false
        return isTypingUnion(subscriptionExpr)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val subscriptionExpr = PsiTreeUtil.getParentOfType(
            element, PySubscriptionExpression::class.java, false
        ) ?: return
        if (!isTypingUnion(subscriptionExpr)) return

        val indexExpr = subscriptionExpr.indexExpression ?: return
        val elements = if (indexExpr is PyTupleExpression) {
            indexExpr.elements.map { it.text }
        } else {
            listOf(indexExpr.text)
        }
        val newText = elements.joinToString(" | ")

        val injectionManager = InjectedLanguageManager.getInstance(project)
        if (injectionManager.isInjectedFragment(element.containingFile)) {
            val hostElement = injectionManager.getInjectionHost(element)
            if (hostElement is PyStringLiteralExpression) {
                val hostText = hostElement.text
                val content = hostElement.stringValue
                val newContent = content.replace(subscriptionExpr.text, newText)
                val newHostText = hostText.replace(content, newContent)
                val generator = PyElementGenerator.getInstance(project)
                val newStringExpr = generator.createExpressionFromText(
                    LanguageLevel.forElement(hostElement), newHostText
                )
                hostElement.replace(newStringExpr)
                val file = hostElement.containingFile as? PyFile ?: return
                removeUnusedImport(file, "Union", "typing")
                return
            }
        }

        val generator = PyElementGenerator.getInstance(project)
        val newExpr = generator.createExpressionFromText(
            LanguageLevel.forElement(element), newText
        )
        val replaced = subscriptionExpr.replace(newExpr)
        removeUnusedImport(replaced.containingFile as? PyFile ?: return, "Union", "typing")
    }

    private fun isTypingUnion(expr: PySubscriptionExpression): Boolean {
        val operand = expr.operand
        if (operand !is PyReferenceExpression) return false
        if (operand.name != "Union") return false
        val resolved = operand.reference.resolve() ?: return false
        val file = resolved.containingFile ?: return false
        return file.name == "typing.pyi" || isInTypingModule(file)
    }

    private fun isInTypingModule(file: com.intellij.psi.PsiFile): Boolean {
        val path = file.virtualFile?.path ?: return false
        return path.contains("/typing") || path.contains("\\typing")
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