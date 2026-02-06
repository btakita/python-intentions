package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class RemoveFromAllIntentionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        val typingStub = javaClass.classLoader.getResource("typing.pyi")!!.readText()
        myFixture.addFileToProject("typing.pyi", typingStub)
    }

    private fun findRemoveFromAllIntention(name: String) =
        myFixture.findSingleIntention("Remove '$name' from __all__")

    fun testRemoveLastElementDeletesAllAssignment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            __all__ = ["foo"]

            def fo<caret>o():
                pass
            """.trimIndent()
        )
        val intention = findRemoveFromAllIntention("foo")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def foo():
                pass
            """.trimIndent()
        )
    }

    fun testRemoveLastElementFromTupleDeletesAllAssignment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            __all__ = ("foo",)

            def fo<caret>o():
                pass
            """.trimIndent()
        )
        val intention = findRemoveFromAllIntention("foo")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def foo():
                pass
            """.trimIndent()
        )
    }

    fun testRemoveNonLastElementKeepsAllAssignment() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            __all__ = ["foo", "bar"]

            def fo<caret>o():
                pass

            def bar():
                pass
            """.trimIndent()
        )
        val intention = findRemoveFromAllIntention("foo")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            __all__ = ["bar"]

            def foo():
                pass

            def bar():
                pass
            """.trimIndent()
        )
    }

    fun testRemoveLastElementWithImportsBefore() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            import os

            __all__ = ["foo"]

            def fo<caret>o():
                pass
            """.trimIndent()
        )
        val intention = findRemoveFromAllIntention("foo")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            import os

            def foo():
                pass
            """.trimIndent()
        )
    }

    fun testRemoveLastElementAtEndOfFile() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            __all__ = ["foo"]

            fo<caret>o = 42
            """.trimIndent()
        )
        val intention = findRemoveFromAllIntention("foo")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            foo = 42
            """.trimIndent()
        )
    }
}