package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class OptionalToUnionIntentionTest : BasePlatformTestCase() {

    private val intentionText = "Replace with X | None (PEP 604)"

    override fun setUp() {
        super.setUp()
        val typingStub = javaClass.classLoader.getResource("typing.pyi")!!.readText()
        myFixture.addFileToProject("typing.pyi", typingStub)
    }

    fun testBasicOptionalToUnion() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            from typing import Optional
            x: Opt<caret>ional[int] = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import Optional
            x: int | None = None
            """.trimIndent()
        )
    }

    fun testOptionalUnionFlattening() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            from typing import Optional, Union
            x: Opt<caret>ional[Union[int, str]] = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import Optional, Union
            x: int | str | None = None
            """.trimIndent()
        )
    }

    fun testOptionalWithoutImport() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: Opt<caret>ional[int] = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            x: int | None = None
            """.trimIndent()
        )
    }

    fun testNotAvailableOnNonOptional() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: in<caret>t = 0
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }
}
