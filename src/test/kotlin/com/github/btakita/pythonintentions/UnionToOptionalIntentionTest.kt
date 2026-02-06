package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class UnionToOptionalIntentionTest : BasePlatformTestCase() {

    private val intentionText = "Replace with Optional[X]"

    fun testBasicUnionToOptional() {
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
            from typing import Optional

            x: Optional[int] = None
            """.trimIndent()
        )
    }

    fun testMultipleTypesToOptional() {
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
            from typing import Union
            from typing import Optional

            x: Optional[Union[int, str]] = None
            """.trimIndent()
        )
    }

    fun testNotAvailableWithoutNone() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            x: int <caret>| str = None
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions(intentionText)
        assertEmpty(intentions)
    }
}
