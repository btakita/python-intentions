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

    /**
     * Computes the PEP 8 insertion index for `__all__` in the file's top-level statements.
     * Skips the module docstring and `from __future__` imports.
     */
    fun findPep8InsertionIndex(file: PyFile): Int {
        val statements = file.statements
        var index = 0

        // Skip docstring (first statement if it's an expression statement containing a string literal)
        if (statements.isNotEmpty()) {
            val first = statements[0]
            if (first is PyExpressionStatement && first.expression is PyStringLiteralExpression) {
                index = 1
            }
        }

        // Skip from __future__ imports
        while (index < statements.size) {
            val stmt = statements[index]
            if (stmt is PyFromImportStatement && stmt.importSourceQName?.toString() == "__future__") {
                index++
            } else {
                break
            }
        }

        return index
    }

    /**
     * Returns true if the element is within a top-level `__all__` assignment.
     */
    fun isOnAllAssignment(element: PsiElement): Boolean {
        val file = element.containingFile as? PyFile ?: return false
        val allAssignment = findAllAssignment(file) ?: return false
        return PsiTreeUtil.isAncestor(allAssignment, element, false)
    }
}
