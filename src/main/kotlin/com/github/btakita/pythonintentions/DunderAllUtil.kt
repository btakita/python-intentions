package com.github.btakita.pythonintentions

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.*

object DunderAllUtil {

    private val DUNDER_PATTERN = Regex("^__.*__$")

    /**
     * Returns true if the name matches the `__dunder__` pattern.
     */
    fun isDunder(name: String): Boolean = DUNDER_PATTERN.matches(name)

    /**
     * Returns the top-level name at the cursor position, or null.
     * For functions/classes, only returns the name if the cursor is on the header (not inside the body).
     * Recognizes names defined inside module-scope blocks (if/elif/else, try/except/finally, for, while, with).
     */
    fun findTopLevelName(element: PsiElement): String? {
        // Check if we're on a top-level function definition (header only)
        val function = PsiTreeUtil.getParentOfType(element, PyFunction::class.java, false)
        if (function != null && isAtModuleScope(function)) {
            if (!PsiTreeUtil.isAncestor(function.statementList, element, false)) {
                return function.name
            }
            return null
        }

        // Check if we're on a top-level class definition (header only)
        val pyClass = PsiTreeUtil.getParentOfType(element, PyClass::class.java, false)
        if (pyClass != null && isAtModuleScope(pyClass)) {
            if (!PsiTreeUtil.isAncestor(pyClass.statementList, element, false)) {
                return pyClass.name
            }
            return null
        }

        // Check if we're on a top-level assignment target (excluding __all__ itself)
        val assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement::class.java, false)
        if (assignment != null && isAtModuleScope(assignment)) {
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
     * Returns true if the given statement is at module scope — either a direct child of PyFile,
     * or nested only inside block statements (if/for/while/try/with) that are themselves at module scope.
     */
    private fun isAtModuleScope(statement: PsiElement): Boolean {
        var current = statement.parent
        while (current != null) {
            when (current) {
                is PyFile -> return true
                is PyStatementList -> {
                    val block = current.parent
                    if (block is PyFunction || block is PyClass) return false
                    // Continue up through if/for/while/try/with blocks
                    current = block?.parent
                }
                else -> current = current.parent
            }
        }
        return false
    }

    /**
     * Finds a module-level dunder assignment by name (e.g., "__all__", "__version__").
     */
    fun findDunderAssignment(file: PyFile, name: String): PyAssignmentStatement? {
        for (statement in file.statements) {
            if (statement is PyAssignmentStatement) {
                val target = statement.targets.firstOrNull()
                if (target is PyTargetExpression && target.name == name) {
                    return statement
                }
            }
        }
        return null
    }

    /**
     * Finds the `__all__ = [...]` or `__all__ = (...)` assignment statement in the file.
     */
    fun findAllAssignment(file: PyFile): PyAssignmentStatement? =
        findDunderAssignment(file, "__all__")

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
     * Computes the PEP 8 insertion index for module-level dunders in the file's top-level statements.
     * Skips the module docstring, `from __future__` imports, and existing module-level dunder assignments.
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

        // Skip existing module-level dunder assignments
        while (index < statements.size) {
            val stmt = statements[index]
            if (stmt is PyAssignmentStatement) {
                val target = stmt.targets.firstOrNull()
                if (target is PyTargetExpression && target.name != null && isDunder(target.name!!)) {
                    index++
                    continue
                }
            }
            break
        }

        return index
    }

    /**
     * If the element is within a module-scope dunder assignment, returns the dunder name.
     * Otherwise returns null.
     */
    fun isOnDunderAssignment(element: PsiElement): String? {
        val file = element.containingFile as? PyFile ?: return null
        for (statement in file.statements) {
            if (statement is PyAssignmentStatement) {
                val target = statement.targets.firstOrNull()
                if (target is PyTargetExpression && target.name != null && isDunder(target.name!!)) {
                    if (PsiTreeUtil.isAncestor(statement, element, false)) {
                        return target.name
                    }
                }
            }
        }
        return null
    }

    /**
     * Returns true if the element is within a top-level `__all__` assignment.
     */
    fun isOnAllAssignment(element: PsiElement): Boolean =
        isOnDunderAssignment(element) == "__all__"
}
