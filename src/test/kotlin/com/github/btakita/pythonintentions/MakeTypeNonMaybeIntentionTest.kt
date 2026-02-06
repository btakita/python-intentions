package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class MakeTypeNonMaybeIntentionTest : BasePlatformTestCase() {

    private val intentionText = "Make type non-maybe (remove None)"

    override fun setUp() {
        super.setUp()
        val typingStub = javaClass.classLoader.getResource("typing.pyi")!!.readText()
        myFixture.addFileToProject("typing.pyi", typingStub)
    }

    fun testOptionalToBase() {
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
            x: int = None
            """.trimIndent()
        )
    }

    fun testPipeNoneToBase() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: int <caret>| None = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            x: int = None
            """.trimIndent()
        )
    }

    fun testMultiplePipeNoneRemoval() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: int | str <caret>| None = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            x: int | str = None
            """.trimIndent()
        )
    }

    fun testOptionalUnionToUnion() {
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
            x: Union[int, str] = None
            """.trimIndent()
        )
    }

    fun testNotAvailableOnNonMaybeType() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: in<caret>t = 0
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }

    fun testNotAvailableOnPipeWithoutNone() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: int <caret>| str = 0
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }
}
