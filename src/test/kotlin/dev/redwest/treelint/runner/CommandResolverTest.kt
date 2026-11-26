package dev.redwest.treelint.runner

import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class CommandResolverTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `configured js executable runs through node`() {
        val resolved = CommandResolver.resolve(
            basePath = tempDir.toString(),
            configuredExecutable = "/tools/custom.js",
            configuredNode = "/usr/bin/node",
            isWindows = false,
        )

        assertEquals("/usr/bin/node", resolved.program)
        assertEquals(listOf("/tools/custom.js"), resolved.arguments)
    }

    @Test
    fun `configured non-js executable runs directly`() {
        val resolved = CommandResolver.resolve(
            basePath = tempDir.toString(),
            configuredExecutable = "/tools/tree-lint-bin",
            configuredNode = "",
            isWindows = false,
        )

        assertEquals("/tools/tree-lint-bin", resolved.program)
        assertEquals(emptyList(), resolved.arguments)
    }

    @Test
    fun `defaults node executable to bare node`() {
        val resolved = CommandResolver.resolve(
            basePath = tempDir.toString(),
            configuredExecutable = "/tools/custom.js",
            configuredNode = "",
            isWindows = false,
        )

        assertEquals("node", resolved.program)
    }

    @Test
    fun `finds package cli js in project node_modules`() {
        val cliJs = tempDir.resolve("node_modules/@redwestdev/tree-lint/dist/cli.js")
        Files.createDirectories(cliJs.parent)
        Files.writeString(cliJs, "#!/usr/bin/env node")

        val resolved = CommandResolver.resolve(tempDir.toString(), "", "", isWindows = false)

        assertEquals("node", resolved.program)
        assertEquals(listOf(cliJs.toFile().absolutePath), resolved.arguments)
    }

    @Test
    fun `finds package cli js in parent directory for monorepos`() {
        val cliJs = tempDir.resolve("node_modules/@redwestdev/tree-lint/dist/cli.js")
        Files.createDirectories(cliJs.parent)
        Files.writeString(cliJs, "#!/usr/bin/env node")
        val nestedProject = Files.createDirectories(tempDir.resolve("apps/web"))

        val resolved = CommandResolver.resolve(nestedProject.toString(), "", "", isWindows = false)

        assertEquals(listOf(cliJs.toFile().absolutePath), resolved.arguments)
    }

    @Test
    fun `falls back to local bin shim`() {
        val bin = tempDir.resolve("node_modules/.bin/tree-lint")
        Files.createDirectories(bin.parent)
        Files.writeString(bin, "#!/bin/sh")

        val resolved = CommandResolver.resolve(tempDir.toString(), "", "", isWindows = false)

        assertEquals(bin.toFile().absolutePath, resolved.program)
        assertEquals(emptyList(), resolved.arguments)
    }

    @Test
    fun `uses cmd shim on windows`() {
        val bin = tempDir.resolve("node_modules/.bin/tree-lint.cmd")
        Files.createDirectories(bin.parent)
        Files.writeString(bin, "@echo off")

        val resolved = CommandResolver.resolve(tempDir.toString(), "", "", isWindows = true)

        assertEquals(bin.toFile().absolutePath, resolved.program)
    }

    @Test
    fun `falls back to tree-lint from PATH`() {
        val resolved = CommandResolver.resolve(tempDir.toString(), "", "", isWindows = false)

        assertEquals("tree-lint", resolved.program)
        assertEquals(emptyList(), resolved.arguments)
    }
}
