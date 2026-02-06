package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class PipeToUnionSubscriptionIntentionTest : BasePlatformTestCase() {

    private val intentionText = "Replace with Union[X, Y]"

    fun testBasicPipeToUnion() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: int <caret>| str = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import Union

            x: Union[int, str] = None
            """.trimIndent()
        )
    }

    fun testTriplePipeToUnion() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: int | str <caret>| float = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            from typing import Union

            x: Union[int, str, float] = None
            """.trimIndent()
        )
    }

    fun testPipeWithNoneToUnion() {
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
            from typing import Union

            x: Union[int, None] = None
            """.trimIndent()
        )
    }
}
