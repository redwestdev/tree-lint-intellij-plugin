package dev.redwest.treelint.runner

import com.intellij.openapi.util.SystemInfo
import java.io.File

data class ResolvedCommand(
    val program: String,
    val arguments: List<String> = emptyList(),
)

/**
 * Resolves what to run for a scan, in priority order:
 * 1. explicitly configured executable (a .js file is run through Node.js);
 * 2. node_modules/@redwestdev/tree-lint/dist/cli.js (walking up for monorepos);
 * 3. node_modules/.bin/tree-lint;
 * 4. tree-lint from PATH.
 */
object CommandResolver {
    fun resolve(
        basePath: String,
        configuredExecutable: String,
        configuredNode: String,
        isWindows: Boolean = SystemInfo.isWindows,
    ): ResolvedCommand {
        if (configuredExecutable.isNotEmpty()) {
            return if (configuredExecutable.endsWith(".js", ignoreCase = true)) {
                ResolvedCommand(
                    program = configuredNode.ifEmpty { "node" },
                    arguments = listOf(File(configuredExecutable).absolutePath),
                )
            } else {
                ResolvedCommand(configuredExecutable)
            }
        }

        val cliJs = findCliJs(File(basePath))
        if (cliJs != null) {
            return ResolvedCommand(
                program = configuredNode.ifEmpty { "node" },
                arguments = listOf(cliJs.absolutePath),
            )
        }

        val localBin = File(
            basePath,
            "node_modules/.bin/${if (isWindows) "tree-lint.cmd" else "tree-lint"}",
        )
        if (localBin.exists()) {
            return ResolvedCommand(localBin.absolutePath)
        }

        return ResolvedCommand("tree-lint")
    }

    /**
     * Looks for the package in the project and its parents to support monorepos.
     */
    private fun findCliJs(start: File): File? {
        var directory: File? = start.absoluteFile
        while (directory != null) {
            val candidate = File(directory, "node_modules/@redwestdev/tree-lint/dist/cli.js")
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        return null
    }
}
