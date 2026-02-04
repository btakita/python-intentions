package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

class OptionalToUnionIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Replace Optional[X] with X | None"

    override fun getText(): String = "Replace with X | None (PEP 604)"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val subscriptionExpr = PsiTreeUtil.getParentOfType(
            element, PySubscriptionExpression::class.java, false
        ) ?: return false
        return isTypingOptional(subscriptionExpr)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val subscriptionExpr = PsiTreeUtil.getParentOfType(
            element, PySubscriptionExpression::class.java, false
        ) ?: return
        if (!isTypingOptional(subscriptionExpr)) return

        val indexExpr = subscriptionExpr.indexExpression ?: return
        val innerTypes = flattenInnerType(indexExpr)
        val newText = (innerTypes + "None").joinToString(" | ")

        val generator = PyElementGenerator.getInstance(project)
        val newExpr = generator.createExpressionFromText(
            LanguageLevel.forElement(element), newText
        )
        val replaced = subscriptionExpr.replace(newExpr)

        // Remove unused Optional import
        removeUnusedImport(replaced.containingFile as? PyFile ?: return, "Optional", "typing")
        // Also remove Union import if it was used in Optional[Union[...]] and is now unused
        removeUnusedImport(replaced.containingFile as? PyFile ?: return, "Union", "typing")
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

    private fun flattenInnerType(expr: PyExpression): List<String> {
        if (expr is PySubscriptionExpression) {
            val operand = expr.operand
            if (operand is PyReferenceExpression && operand.name == "Union") {
                val indexExpr = expr.indexExpression
                if (indexExpr is PyTupleExpression) {
                    return indexExpr.elements.flatMap { flattenInnerType(it) }
                }
            }
        }
        return listOf(expr.text)
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
