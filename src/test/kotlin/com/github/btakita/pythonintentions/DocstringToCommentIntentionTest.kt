package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class DocstringToCommentIntentionTest : BasePlatformTestCase() {

    private val intentionText = "Convert docstring to comment"

    fun testSingleLineDocstring() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            ${"\"\"\""}This is a <caret>docstring${"\"\"\""}
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            # This is a docstring
            """.trimIndent()
        )
    }

    fun testMultiLineDocstring() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            ${"\"\"\""}
            Line <caret>one
            Line two
            Line three
            ${"\"\"\""}
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            #
            # Line one
            # Line two
            # Line three
            #
            """.trimIndent()
        )
    }

    fun testIndentedDocstring() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            def foo():
                ${"\"\"\""}A <caret>docstring${"\"\"\""}
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def foo():
                # A docstring
            """.trimIndent()
        )
    }

    fun testDocstringWithBlankLine() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            ${"\"\"\""}Line <caret>one

            Line three${"\"\"\""}
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            # Line one
            #
            # Line three
            """.trimIndent()
        )
    }

    fun testNotAvailableOnAssignedString() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x = "hel<caret>lo"
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }

    fun testNotAvailableOnFunctionArgString() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            print("hel<caret>lo")
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }
}
