package dev.redwest.treelint.model

enum class TreeLintSeverity { ERROR, WARNING, UNKNOWN }

fun severityOf(type: String): TreeLintSeverity = when (type.trim().lowercase()) {
    "error" -> TreeLintSeverity.ERROR
    "warning" -> TreeLintSeverity.WARNING
    else -> TreeLintSeverity.UNKNOWN
}

fun maxSeverity(a: TreeLintSeverity, b: TreeLintSeverity): TreeLintSeverity = when {
    a == TreeLintSeverity.ERROR || b == TreeLintSeverity.ERROR -> TreeLintSeverity.ERROR
    a == TreeLintSeverity.WARNING || b == TreeLintSeverity.WARNING -> TreeLintSeverity.WARNING
    else -> TreeLintSeverity.UNKNOWN
}

data class TreeLintViolation(
    val type: String = "warning",
    val path: String = "",
    val message: String = "",
)

data class TreeLintReport(
    val generatedAt: String = "",
    /** Roots from the tree-lint config the report was built for (relative or absolute). */
    val roots: List<String> = emptyList(),
    val errors: Int = 0,
    val warnings: Int = 0,
    val violations: List<TreeLintViolation> = emptyList(),
)
