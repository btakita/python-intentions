package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class CommentToDocstringIntentionTest : BasePlatformTestCase() {

    private val intentionText = "Convert comment to docstring"

    fun testSingleLineComment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            # This is a <caret>comment
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            ${"\"\"\""}This is a comment${"\"\"\""}
            """.trimIndent()
        )
    }

    fun testMultiLineComment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            # Line <caret>one
            # Line two
            # Line three
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            ${"\"\"\""}
            Line one
            Line two
            Line three
            ${"\"\"\""}
            """.trimIndent()
        )
    }

    fun testIndentedComment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            def foo():
                # A <caret>comment
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def foo():
                ${"\"\"\""}A comment${"\"\"\""}
            """.trimIndent()
        )
    }

    fun testIndentedMultiLineComment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            def foo():
                # Line <caret>one
                # Line two
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def foo():
                ${"\"\"\""}
                Line one
                Line two
                ${"\"\"\""}
            """.trimIndent()
        )
    }

    fun testCommentNoSpace() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            #no<caret>space
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            ${"\"\"\""}nospace${"\"\"\""}
            """.trimIndent()
        )
    }

    fun testEmptyComment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            # Line one
            #<caret>
            # Line three
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            ${"\"\"\""}
            Line one

            Line three
            ${"\"\"\""}
            """.trimIndent()
        )
    }

    fun testNotAvailableOnNonComment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x = <caret>1
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }
}
