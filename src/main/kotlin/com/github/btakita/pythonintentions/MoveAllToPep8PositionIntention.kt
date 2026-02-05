package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

class MoveAllToPep8PositionIntention : PsiElementBaseIntentionAction() {

    override fun getFamilyName(): String = "Move __all__ to PEP 8 position"

    override fun getText(): String = "Move __all__ to PEP 8 position"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile as? PyFile ?: return false
        if (!DunderAllUtil.isOnAllAssignment(element)) return false
        val allAssignment = DunderAllUtil.findAllAssignment(file) ?: return false

        val statements = file.statements
        val currentIndex = statements.indexOf(allAssignment)
        if (currentIndex < 0) return false

        val pep8Index = DunderAllUtil.findPep8InsertionIndex(file)

        // Only available if there's a regular import before __all__
        // i.e., there exists a statement between pep8Index and currentIndex that is an import
        for (i in pep8Index until currentIndex) {
            val stmt = statements[i]
            if (stmt is PyImportStatement || stmt is PyFromImportStatement) {
                return true
            }
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile as? PyFile ?: return
        val allAssignment = DunderAllUtil.findAllAssignment(file) ?: return

        val generator = PyElementGenerator.getInstance(project)
        val langLevel = LanguageLevel.forElement(element)

        val pep8Index = DunderAllUtil.findPep8InsertionIndex(file)

        // Create a copy of the __all__ assignment from its text to preserve formatting
        val allText = allAssignment.text
        val newStatement = generator.createFromText(langLevel, PyAssignmentStatement::class.java, allText)

        // Remove the old __all__ assignment
        allAssignment.delete()

        // Re-read statements after deletion
        val updatedStatements = file.statements
        val newline = generator.createNewLine()

        if (pep8Index < updatedStatements.size) {
            val anchor = updatedStatements[pep8Index]
            file.addBefore(newline, anchor)
            file.addBefore(newStatement, anchor)
            file.addBefore(generator.createNewLine(), anchor)
        } else {
            file.add(generator.createNewLine())
            file.add(newStatement)
        }
    }
}
