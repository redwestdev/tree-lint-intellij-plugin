package dev.redwest.treelint.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class TreeLintConfigResolverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `finds config in the project directory`() {
        Files.writeString(tempDir.resolve("tree-lint.config.json"), "{}")

        assertEquals(
            tempDir.resolve("tree-lint.config.json").toFile().absolutePath,
            TreeLintConfigResolver.findConfig(tempDir.toFile())?.absolutePath,
        )
    }

    @Test
    fun `walks up to parent directories like monorepos`() {
        Files.writeString(tempDir.resolve("tree-lint.config.json"), "{}")
        val nested = Files.createDirectories(tempDir.resolve("apps/web"))

        assertEquals(
            tempDir.resolve("tree-lint.config.json").toFile().absolutePath,
            TreeLintConfigResolver.findConfig(nested.toFile())?.absolutePath,
        )
    }

    @Test
    fun `supports other config extensions`() {
        Files.writeString(tempDir.resolve("tree-lint.config.ts"), "export default {}")

        assertEquals(
            tempDir.resolve("tree-lint.config.ts").toFile().absolutePath,
            TreeLintConfigResolver.findConfig(tempDir.toFile())?.absolutePath,
        )
    }

    @Test
    fun `returns null when no config exists`() {
        assertNull(TreeLintConfigResolver.findConfig(tempDir.toFile()))
    }

    @Test
    fun `ignores directories named like configs`() {
        Files.createDirectory(tempDir.resolve("tree-lint.config.json"))

        assertNull(TreeLintConfigResolver.findConfig(tempDir.toFile()))
    }
}
