package dev.redwest.treelint.runner

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dev.redwest.treelint.model.TreeLintReport
import dev.redwest.treelint.model.TreeLintSeverity
import dev.redwest.treelint.model.TreeLintViolation
import dev.redwest.treelint.model.severityOf

/**
 * Parses the JSON report produced by
 * `tree-lint scan <path> --report --group-by path`: violations are grouped by
 * absolute path, each group holding `error` and `warning` messages. Missing
 * fields fall back to defaults because Gson bypasses Kotlin default parameters.
 */
object ReportParser {
    private val gson = Gson()

    fun parse(stdout: String): TreeLintReport {
        val raw = try {
            gson.fromJson(stdout, RawReport::class.java)
        } catch (e: JsonSyntaxException) {
            throw TreeLintException("Cannot parse tree-lint report: ${stdout.take(300)}", e)
        } ?: throw TreeLintException("tree-lint produced no report")

        if (raw.isEmpty) {
            throw TreeLintException("tree-lint produced no report: ${stdout.take(300)}")
        }

        val violations = raw.grouped.orEmpty().entries.flatMap { (path, group) ->
            if (path.isBlank() || group == null) emptyList() else group.toViolations(path)
        }

        return TreeLintReport(
            generatedAt = raw.generatedAt.orEmpty(),
            roots = raw.root.orEmpty().filter { it.isNotBlank() },
            errors = raw.errors ?: violations.count { severityOf(it.type) == TreeLintSeverity.ERROR },
            warnings = raw.warnings ?: violations.count { severityOf(it.type) == TreeLintSeverity.WARNING },
            violations = violations,
        )
    }

    /**
     * In report mode the CLI writes failures to stderr as `{"error": "..."}`
     * and exits with a non-zero code.
     */
    fun parseCliError(stderr: String): String? {
        val text = stderr.trim()
        if (!text.startsWith("{")) return null
        return try {
            gson.fromJson(text, RawError::class.java)?.error?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    private data class RawReport(
        val generatedAt: String? = null,
        val root: List<String>? = null,
        val errors: Int? = null,
        val warnings: Int? = null,
        val grouped: Map<String, RawSeverityGroup?>? = null,
    ) {
        val isEmpty: Boolean
            get() = generatedAt == null && root == null && errors == null &&
                warnings == null && grouped == null
    }

    private data class RawSeverityGroup(
        val error: List<String>? = null,
        val warning: List<String>? = null,
    ) {
        fun toViolations(path: String): List<TreeLintViolation> =
            error.orEmpty().map { TreeLintViolation(type = "error", path = path, message = it) } +
                warning.orEmpty().map { TreeLintViolation(type = "warning", path = path, message = it) }
    }

    private data class RawError(val error: String? = null)
}
