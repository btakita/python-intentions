package com.github.btakita.pythonintentions

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.python.PythonFileType

class MoveDunderToPep8PositionIntentionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        val typingStub = javaClass.classLoader.getResource("typing.pyi")!!.readText()
        myFixture.addFileToProject("typing.pyi", typingStub)
    }

    fun testMoveVersionToPep8Position() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            import os
            import sys

            __ver<caret>sion__ = "1.0.0"

            def foo():
                pass
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention("Move __version__ to PEP 8 position")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            __version__ = "1.0.0"

            import os
            import sys

            def foo():
                pass
            """.trimIndent()
        )
    }

    fun testMoveAuthorToPep8Position() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            import os

            __aut<caret>hor__ = "Test Author"

            def foo():
                pass
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention("Move __author__ to PEP 8 position")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            __author__ = "Test Author"

            import os

            def foo():
                pass
            """.trimIndent()
        )
    }

    fun testMoveAllToPep8Position() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            import os
            import sys

            __al<caret>l__ = ["foo", "bar"]

            def foo():
                pass
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention("Move __all__ to PEP 8 position")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            __all__ = ["foo", "bar"]

            import os
            import sys

            def foo():
                pass
            """.trimIndent()
        )
    }

    fun testNotAvailableWhenAlreadyInPep8Position() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            __ver<caret>sion__ = "1.0.0"

            import os

            def foo():
                pass
            """.trimIndent()
        )
        val intentions = myFixture.filterAvailableIntentions("Move __version__ to PEP 8 position")
        assertTrue("Intention should not be available when already in PEP 8 position", intentions.isEmpty())
    }

    fun testGroupingWithExistingDunders() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            __all__ = ["foo"]

            import os

            __ver<caret>sion__ = "1.0.0"

            def foo():
                pass
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention("Move __version__ to PEP 8 position")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            __all__ = ["foo"]
            __version__ = "1.0.0"

            import os

            def foo():
                pass
            """.trimIndent()
        )
    }

    fun testMoveWithDocstringAndFutureImport() {
        myFixture.configureByText(
            PythonFileType.INSTANCE,
            """
            '''Module docstring.'''

            from __future__ import annotations

            import os

            __ver<caret>sion__ = "1.0.0"

            def foo():
                pass
            """.trimIndent()
        )
        val intention = myFixture.findSingleIntention("Move __version__ to PEP 8 position")
        myFixture.launchAction(intention)
        myFixture.checkResult(
            """
            '''Module docstring.'''

            from __future__ import annotations
            __version__ = "1.0.0"

            import os

            def foo():
                pass
            """.trimIndent()
        )
    }
}
