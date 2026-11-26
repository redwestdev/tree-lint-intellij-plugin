package dev.redwest.treelint.runner

import java.io.File

/**
 * Locates the root tree-lint config the same way the CLI does: looks for
 * tree-lint.config.{ts,js,mjs,mts,json,yaml,yml} starting from the given
 * directory and walking up to the filesystem root.
 */
object TreeLintConfigResolver {
    private val extensions = listOf("ts", "js", "mjs", "mts", "json", "yaml", "yml")

    fun findConfig(start: File): File? {
        var dir: File? = start.absoluteFile
        while (dir != null) {
            for (ext in extensions) {
                val candidate = File(dir, "tree-lint.config.$ext")
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        return null
    }
}
