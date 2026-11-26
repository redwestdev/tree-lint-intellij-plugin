package dev.redwest.treelint.model

import dev.redwest.treelint.runner.ReportParser
import dev.redwest.treelint.runner.TreeLintException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SeverityAndParsingTest {

    @Test
    fun `severity mapping covers known and unknown types`() {
        assertEquals(TreeLintSeverity.ERROR, severityOf("error"))
        assertEquals(TreeLintSeverity.ERROR, severityOf(" Error "))
        assertEquals(TreeLintSeverity.WARNING, severityOf("WARNING"))
        assertEquals(TreeLintSeverity.UNKNOWN, severityOf("info"))
        assertEquals(TreeLintSeverity.UNKNOWN, severityOf(""))
    }

    @Test
    fun `max severity prefers error then warning`() {
        assertEquals(TreeLintSeverity.ERROR, maxSeverity(TreeLintSeverity.WARNING, TreeLintSeverity.ERROR))
        assertEquals(TreeLintSeverity.WARNING, maxSeverity(TreeLintSeverity.UNKNOWN, TreeLintSeverity.WARNING))
        assertEquals(TreeLintSeverity.UNKNOWN, maxSeverity(TreeLintSeverity.UNKNOWN, TreeLintSeverity.UNKNOWN))
    }

    @Test
    fun `parses a full report`() {
        val json = """
            {
              "generatedAt": "2026-01-01T00:00:00.000Z",
              "projectRoot": "/project",
              "errors": 1,
              "warnings": 2,
              "violations": [
                {"type": "error", "path": "/project/src/a", "message": "bad"},
                {"type": "warning", "path": "/project/src/b", "message": "meh"},
                {"type": "info", "path": "/project/src/c", "message": "note"}
              ]
            }
        """.trimIndent()

        val report = ReportParser.parse(json)

        assertEquals("/project", report.projectRoot)
        assertEquals(1, report.errors)
        assertEquals(2, report.warnings)
        assertEquals(3, report.violations.size)
        assertEquals("bad", report.violations[0].message)
        assertEquals(TreeLintSeverity.UNKNOWN, severityOf(report.violations[2].type))
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val json = """{"violations": [{"path": "/p", "message": "x"}]}"""

        val report = ReportParser.parse(json)

        assertEquals("", report.projectRoot)
        assertEquals(0, report.errors)
        assertEquals(1, report.violations.size)
        assertEquals("warning", report.violations[0].type)
        assertEquals("/p", report.violations[0].path)
        assertEquals("x", report.violations[0].message)
    }

    @Test
    fun `empty violations array parses to empty list`() {
        val report = ReportParser.parse("""{"violations": null}""")
        assertTrue(report.violations.isEmpty())
    }

    @Test
    fun `invalid json throws with context`() {
        val error = assertFailsWith<TreeLintException> {
            ReportParser.parse("{not a json")
        }
        assertTrue(error.message!!.contains("Cannot parse tree-lint report"))
    }
}
