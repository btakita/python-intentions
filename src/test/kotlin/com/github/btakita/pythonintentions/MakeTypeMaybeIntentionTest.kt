package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class MakeTypeMaybeIntentionTest : BasePlatformTestCase() {

    private val intentionText = "Make type maybe (add | None)"

    override fun setUp() {
        super.setUp()
        val typingStub = javaClass.classLoader.getResource("typing.pyi")!!.readText()
        myFixture.addFileToProject("typing.pyi", typingStub)
    }

    fun testSimpleType() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: in<caret>t = 0
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            x: int | None = 0
            """.trimIndent()
        )
    }

    fun testFunctionParameter() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            def foo(x: in<caret>t):
                pass
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def foo(x: int | None):
                pass
            """.trimIndent()
        )
    }

    fun testReturnType() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            def foo() -> in<caret>t:
                pass
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            def foo() -> int | None:
                pass
            """.trimIndent()
        )
    }

    fun testPipeUnionType() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: int <caret>| str = 0
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            x: int | str | None = 0
            """.trimIndent()
        )
    }

    fun testNotAvailableOnPipeNone() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: int <caret>| None = None
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }

    fun testNotAvailableOnOptional() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            from typing import Optional
            x: Opt<caret>ional[int] = None
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }

    fun testNotAvailableOutsideAnnotation() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x = in<caret>t(5)
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }
}
