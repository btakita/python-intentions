package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class UnionSubscriptionToPipeIntentionTest : BasePlatformTestCase() {

    private val intentionText = "Replace with X | Y (PEP 604)"

    override fun setUp() {
        super.setUp()
        val typingStub = javaClass.classLoader.getResource("typing.pyi")!!.readText()
        myFixture.addFileToProject("typing.pyi", typingStub)
    }

    fun testBasicUnionToPipe() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            from typing import Union
            x: Uni<caret>on[int, str] = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import Union
            x: int | str = None
            """.trimIndent()
        )
    }

    fun testTripleUnionToPipe() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            from typing import Union
            x: Uni<caret>on[int, str, float] = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import Union
            x: int | str | float = None
            """.trimIndent()
        )
    }

    fun testNotAvailableOnNonUnion() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: li<caret>st = []
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }
}
