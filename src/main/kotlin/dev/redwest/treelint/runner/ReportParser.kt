package dev.redwest.treelint.runner

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import dev.redwest.treelint.model.TreeLintReport
import dev.redwest.treelint.model.TreeLintViolation

/**
 * Parses the JSON report produced by `tree-lint scan --json`. Missing fields
 * fall back to defaults because Gson bypasses Kotlin default parameters.
 */
object ReportParser {
    private val gson = Gson()

    fun parse(stdout: String): TreeLintReport {
        val raw = try {
            gson.fromJson(stdout, TreeLintReport::class.java)
        } catch (e: JsonSyntaxException) {
            throw TreeLintException("Cannot parse tree-lint report: ${stdout.take(300)}", e)
        } ?: throw TreeLintException("tree-lint produced no report")

        return TreeLintReport(
            generatedAt = raw.generatedAt.orEmpty(),
            projectRoot = raw.projectRoot.orEmpty(),
            errors = raw.errors,
            warnings = raw.warnings,
            violations = raw.violations.orEmpty().mapNotNull { violation ->
                violation?.let {
                    TreeLintViolation(
                        type = it.type.orEmpty().ifBlank { "warning" },
                        path = it.path.orEmpty(),
                        message = it.message.orEmpty(),
                    )
                }
            },
        )
    }
}
