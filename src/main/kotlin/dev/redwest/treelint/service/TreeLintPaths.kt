package dev.redwest.treelint.service

import dev.redwest.treelint.model.TreeLintSeverity
import dev.redwest.treelint.model.TreeLintViolation
import dev.redwest.treelint.model.maxSeverity
import dev.redwest.treelint.model.severityOf
import java.io.File

data class AncestorIssue(val severity: TreeLintSeverity, val count: Int)

/**
 * Pure path logic: normalization of violation paths and building the ancestor
 * chain used to highlight parent directories in the Project view.
 */
object TreeLintPaths {
    fun normalize(path: String, projectRoot: String? = null, projectBase: String? = null): String {
        val file = File(path)
        val resolved = when {
            file.isAbsolute -> file
            projectRoot != null -> File(projectRoot, path)
            projectBase != null -> File(projectBase, path)
            else -> file
        }
        return resolved.absoluteFile.normalize().path.replace('\\', '/')
    }

    /**
     * Marks every parent directory of each violation (up to, but not
     * including, the project root) with the most severe descendant severity.
     * Keys of [violationsByPath] must already be normalized paths.
     */
    fun buildAncestorIssues(
        violationsByPath: Map<String, List<TreeLintViolation>>,
        projectRoot: String,
    ): Map<String, AncestorIssue> {
        val rootPath = File(projectRoot).absoluteFile.normalize().path.replace('\\', '/')
        val result = mutableMapOf<String, AncestorIssue>()

        for ((key, violations) in violationsByPath) {
            val severity = violations.map { severityOf(it.type) }.reduce(::maxSeverity)
            var parent = File(key).parentFile ?: continue
            while (parent != null) {
                val parentPath = parent.absoluteFile.normalize().path.replace('\\', '/')
                if (parentPath == rootPath || !parentPath.startsWith("$rootPath/")) break
                result.merge(
                    parentPath,
                    AncestorIssue(severity, violations.size),
                ) { old, new ->
                    AncestorIssue(maxSeverity(old.severity, new.severity), old.count + new.count)
                }
                parent = parent.parentFile
            }
        }
        return result
    }
}
