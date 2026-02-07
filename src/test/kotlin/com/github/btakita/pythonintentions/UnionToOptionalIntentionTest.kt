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

    fun testImportAfterDocstring() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            ${"\"\"\""}Module docstring.${"\"\"\""}
            x: int <caret>| None = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            ${"\"\"\""}Module docstring.${"\"\"\""}
            from typing import Optional

            x: Optional[int] = None
            """.trimIndent()
        )
    }

    fun testImportAfterDocstringAndDunders() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            ${"\"\"\""}Module docstring.${"\"\"\""}
            __all__ = []
            x: int <caret>| None = None
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention(intentionText)
        myFixture.launchAction(intention)
        // addBefore inserts a blank line between dunder and import
        myFixture.checkResult(
            """
            ${"\"\"\""}Module docstring.${"\"\"\""}
            __all__ = []

            from typing import Optional

            x: Optional[int] = None
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
