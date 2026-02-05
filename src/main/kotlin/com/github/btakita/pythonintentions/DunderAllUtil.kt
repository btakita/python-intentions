package com.github.btakita.pythonintentions

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

object DunderAllUtil {

    /**
     * Returns the top-level name at the cursor position, or null.
     * For functions/classes, only returns the name if the cursor is on the header (not inside the body).
     */
    fun findTopLevelName(element: PsiElement): String? {
        // Check if we're on a top-level function definition (header only)
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
        if (function != null && function.parent is PyFile) {
            if (!PsiTreeUtil.isAncestor(function.statementList, element, false)) {
                return function.name
            }
            return null
        }

        // Check if we're on a top-level class definition (header only)
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
        if (pyClass != null && pyClass.parent is PyFile) {
            if (!PsiTreeUtil.isAncestor(pyClass.statementList, element, false)) {
                return pyClass.name
            }
            return null
        }

        // Check if we're on a top-level assignment target (excluding __all__ itself)
        val assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement::class.java, false)
        if (assignment != null && assignment.parent is PyFile) {
            for (target in assignment.targets) {
                if (target is PyTargetExpression) {
                    val name = target.name ?: continue
                    if (name == "__all__") continue
                    if (PsiTreeUtil.isAncestor(target, element, false) ||
                        target == element
                    ) {
                        return name
                    }
                }
            }
        }

        return null
    }

    /**
     * Finds the `__all__ = [...]` or `__all__ = (...)` assignment statement in the file.
     */
    fun findAllAssignment(file: PyFile): PyAssignmentStatement? {
        for (statement in file.statements) {
            if (statement is PyAssignmentStatement) {
                val target = statement.targets.firstOrNull()
                if (target is PyTargetExpression && target.name == "__all__") {
                    return statement
                }
            }
        }
        return null
    }

    /**
     * Checks if the given name is present as a string literal in `__all__`.
     */
    fun isNameInAll(file: PyFile, name: String): Boolean {
        val allAssignment = findAllAssignment(file) ?: return false
        val value = allAssignment.assignedValue ?: return false
        val elements = when (value) {
            is PyListLiteralExpression -> value.elements
            is PyTupleExpression -> value.elements
            is PyParenthesizedExpression -> {
                val inner = value.containedExpression
                if (inner is PyTupleExpression) inner.elements else return false
            }
            else -> return false
        }
        return elements.any { it is PyStringLiteralExpression && it.stringValue == name }
    }
}
