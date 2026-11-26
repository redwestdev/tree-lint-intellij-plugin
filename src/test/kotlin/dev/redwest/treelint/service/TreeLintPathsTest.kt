package dev.redwest.treelint.service

import dev.redwest.treelint.model.TreeLintSeverity
import dev.redwest.treelint.model.TreeLintViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TreeLintPathsTest {

    private val root = "/project"

    private fun path(relative: String) = TreeLintPaths.normalize(relative, projectRoot = root)

    @Test
    fun `normalizes absolute paths`() {
        assertEquals("/project/src/components/ui", path("/project/src/components/ui"))
        assertEquals("/project/src/b", TreeLintPaths.normalize("/project/src/a/../b", root))
    }

    @Test
    fun `resolves relative paths against project root`() {
        assertEquals("/project/src/App.tsx", path("src/App.tsx"))
    }

    @Test
    fun `normalizes windows separators`() {
        assertEquals("/project/src/app", TreeLintPaths.normalize("src\\app", "/project"))
    }

    @Test
    fun `marks all ancestors of a violating file`() {
        val violations = mapOf(
            path("src/components/ui/Ui.tsx") to listOf(
                TreeLintViolation(type = "error", path = "src/components/ui/Ui.tsx", message = "bad"),
            ),
        )

        val ancestors = TreeLintPaths.buildAncestorIssues(violations, root)

        assertEquals(TreeLintSeverity.ERROR, ancestors[path("src")]?.severity)
        assertEquals(TreeLintSeverity.ERROR, ancestors[path("src/components")]?.severity)
        assertEquals(TreeLintSeverity.ERROR, ancestors[path("src/components/ui")]?.severity)
        assertEquals(1, ancestors[path("src/components/ui")]?.count)
        assertNull(ancestors[root])
    }

    @Test
    fun `merges severities and counts across siblings`() {
        val violations = mapOf(
            path("src/a/bad.ts") to listOf(
                TreeLintViolation(type = "error", path = "src/a/bad.ts", message = "e"),
            ),
            path("src/b/meh.ts") to listOf(
                TreeLintViolation(type = "warning", path = "src/b/meh.ts", message = "w"),
            ),
            path("src/b/note.ts") to listOf(
                TreeLintViolation(type = "info", path = "src/b/note.ts", message = "n"),
            ),
        )

        val ancestors = TreeLintPaths.buildAncestorIssues(violations, root)

        assertEquals(TreeLintSeverity.ERROR, ancestors[path("src")]?.severity)
        assertEquals(3, ancestors[path("src")]?.count)
        assertEquals(TreeLintSeverity.WARNING, ancestors[path("src/b")]?.severity)
        assertEquals(2, ancestors[path("src/b")]?.count)
    }

    @Test
    fun `directory violations also mark their parents`() {
        val violations = mapOf(
            path("src/components/ui") to listOf(
                TreeLintViolation(type = "warning", path = "src/components/ui", message = "w"),
            ),
        )

        val ancestors = TreeLintPaths.buildAncestorIssues(violations, root)

        assertEquals(TreeLintSeverity.WARNING, ancestors[path("src/components")]?.severity)
        assertEquals(TreeLintSeverity.WARNING, ancestors[path("src")]?.severity)
        assertNull(ancestors[path("src/components/ui")])
    }

    @Test
    fun `exact violation path is not treated as its own ancestor`() {
        val violations = mapOf(
            path("src/App.tsx") to listOf(
                TreeLintViolation(type = "error", path = "src/App.tsx", message = "e"),
            ),
        )

        val ancestors = TreeLintPaths.buildAncestorIssues(violations, root)

        assertNull(ancestors[path("src/App.tsx")])
        assertEquals(1, ancestors[path("src")]?.count)
    }

    @Test
    fun `nothing is marked outside the project root`() {
        val violations = mapOf(
            "/other/place/file.ts" to listOf(
                TreeLintViolation(type = "error", path = "/other/place/file.ts", message = "e"),
            ),
        )

        assertTrue(TreeLintPaths.buildAncestorIssues(violations, root).isEmpty())
    }
}
