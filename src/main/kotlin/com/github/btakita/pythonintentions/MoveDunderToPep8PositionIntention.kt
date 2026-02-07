package com.github.btakita.pythonintentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.jetbrains.python.psi.*

class MoveDunderToPep8PositionIntention : PsiElementBaseIntentionAction() {

    @Volatile
    private var detectedDunder: String = ""

    override fun getFamilyName(): String = "Move module dunder to PEP 8 position"

    override fun getText(): String =
        if (detectedDunder.isNotEmpty()) "Move $detectedDunder to PEP 8 position"
        else "Move module dunder to PEP 8 position"

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val file = element.containingFile as? PyFile ?: return false
        val dunderName = DunderAllUtil.isOnDunderAssignment(element) ?: return false
        val dunderAssignment = DunderAllUtil.findDunderAssignment(file, dunderName) ?: return false

        val statements = file.statements
        val currentIndex = statements.indexOf(dunderAssignment)
        if (currentIndex < 0) return false

        val pep8Index = DunderAllUtil.findPep8InsertionIndex(file)

        // Only available if there's a regular import between pep8Index and currentIndex
        for (i in pep8Index until currentIndex) {
            val stmt = statements[i]
            if (stmt is PyImportStatement || stmt is PyFromImportStatement) {
                detectedDunder = dunderName
                return true
            }
        }
        return false
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val file = element.containingFile as? PyFile ?: return
        val dunderName = DunderAllUtil.isOnDunderAssignment(element) ?: return
        val dunderAssignment = DunderAllUtil.findDunderAssignment(file, dunderName) ?: return

        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val text = document.text
        val dunderLineText = dunderAssignment.text

        // Find the line number of the dunder assignment
        val dunderStartOffset = dunderAssignment.textRange.startOffset
        val dunderLineNumber = document.getLineNumber(dunderStartOffset)

        // Work with lines
        val lines = text.lines().toMutableList()

        // Remove the dunder line
        lines.removeAt(dunderLineNumber)

        // Remove blank lines around the removed position (collapse to at most one blank line)
        // After removal, dunderLineNumber now points to the line that was below the dunder
        while (dunderLineNumber < lines.size && dunderLineNumber > 0 &&
            lines[dunderLineNumber].isBlank() && lines[dunderLineNumber - 1].isBlank()
        ) {
            lines.removeAt(dunderLineNumber)
        }

        // Now find where to insert: compute the pep8 position in the cleaned-up lines
        // Re-parse would be complex, so let's find the PEP 8 insertion line by scanning
        var insertLineIndex = 0

        // Skip docstring (first non-blank line if it starts with a string literal quote)
        val firstNonBlank = lines.indexOfFirst { it.isNotBlank() }
        if (firstNonBlank >= 0) {
            val firstLine = lines[firstNonBlank].trimStart()
            if (firstLine.startsWith("\"\"\"") || firstLine.startsWith("'''") ||
                firstLine.startsWith("\"") || firstLine.startsWith("'")
            ) {
                // Find end of docstring
                insertLineIndex = firstNonBlank + 1
                // Skip blank lines after docstring
                while (insertLineIndex < lines.size && lines[insertLineIndex].isBlank()) {
                    insertLineIndex++
                }
            }
        }

        // Skip from __future__ imports
        while (insertLineIndex < lines.size) {
            val line = lines[insertLineIndex].trimStart()
            if (line.startsWith("from __future__")) {
                insertLineIndex++
                // Skip blank lines after future imports
                while (insertLineIndex < lines.size && lines[insertLineIndex].isBlank()) {
                    insertLineIndex++
                }
            } else {
                break
            }
        }

        // Skip existing dunder assignments (to group them)
        while (insertLineIndex < lines.size) {
            val line = lines[insertLineIndex].trimStart()
            if (line.matches(Regex("^__\\w+__\\s*=.*"))) {
                insertLineIndex++
                // Skip blank lines after dunder
                while (insertLineIndex < lines.size && lines[insertLineIndex].isBlank()) {
                    insertLineIndex++
                }
            } else {
                break
            }
        }

        // Insert: dunder line, then blank line, at insertLineIndex
        // But first check if we need a blank line before (if there's content before)
        lines.add(insertLineIndex, dunderLineText)

        // Ensure blank line after the dunder (before imports/other code)
        if (insertLineIndex + 1 < lines.size && lines[insertLineIndex + 1].isNotBlank()) {
            lines.add(insertLineIndex + 1, "")
        }

        // Ensure no extra blank lines before the dunder (between previous dunder/docstring/future and this)
        if (insertLineIndex > 0 && lines[insertLineIndex - 1].isBlank()) {
            // Check if there's already a blank line — that's ok for after docstring/future
            // But if there are multiple blank lines, collapse
            var blankStart = insertLineIndex - 1
            while (blankStart > 0 && lines[blankStart - 1].isBlank()) {
                blankStart--
            }
            // Remove extra blank lines, keep at most 0 (dunders should be right after previous dunder)
            // Actually, check what's above: if it's a dunder, keep 0 blank lines; otherwise keep 1
            val prevContentLine = if (blankStart > 0) lines[blankStart - 1].trimStart() else ""
            val keepBlanks = if (prevContentLine.matches(Regex("^__\\w+__\\s*=.*")) ||
                prevContentLine.startsWith("from __future__")
            ) 0 else 1
            val currentBlanks = insertLineIndex - blankStart
            val toRemove = currentBlanks - keepBlanks
            if (toRemove > 0) {
                repeat(toRemove) { lines.removeAt(blankStart) }
            }
        }

        // Join and set
        val newText = lines.joinToString("\n")
        document.setText(newText)
        PsiDocumentManager.getInstance(project).commitDocument(document)
    }
}
