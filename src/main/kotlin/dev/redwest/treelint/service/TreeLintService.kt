package dev.redwest.treelint.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import dev.redwest.treelint.model.TreeLintReport
import dev.redwest.treelint.model.TreeLintSeverity
import dev.redwest.treelint.model.TreeLintViolation
import dev.redwest.treelint.model.maxSeverity
import dev.redwest.treelint.model.severityOf
import dev.redwest.treelint.runner.TreeLintRunner
import java.util.concurrent.CopyOnWriteArrayList

fun interface TreeLintScanListener {
    fun scanFinished()
}

@Service(Service.Level.PROJECT)
class TreeLintService(private val project: Project) {
    private val listeners = CopyOnWriteArrayList<TreeLintScanListener>()

    @Volatile
    var lastReport: TreeLintReport? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var projectRoot: String? = null

    @Volatile
    private var violationsByPath: Map<String, List<TreeLintViolation>> = emptyMap()

    @Volatile
    private var ancestorIssues: Map<String, AncestorIssue> = emptyMap()

    fun scan(indicator: ProgressIndicator): TreeLintReport {
        try {
            val report = TreeLintRunner.scan(project, indicator)
            lastReport = report
            lastError = null
            projectRoot = report.projectRoot.ifBlank { project.basePath }.orEmpty()
            violationsByPath = report.violations.groupBy { normalize(it.path) }
            ancestorIssues = if (projectRoot.isNullOrBlank()) {
                emptyMap()
            } else {
                TreeLintPaths.buildAncestorIssues(violationsByPath, projectRoot.orEmpty())
            }
            return report
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: Exception) {
            clearState()
            lastError = e.message ?: e.javaClass.simpleName
            throw e
        } finally {
            listeners.forEach { it.scanFinished() }
        }
    }

    fun violationsFor(path: String): List<TreeLintViolation> =
        violationsByPath[normalize(path)].orEmpty()

    fun severityFor(path: String): TreeLintSeverity? {
        val key = normalize(path)
        val violations = violationsByPath[key]
        if (!violations.isNullOrEmpty()) {
            return violations.map { severityOf(it.type) }.reduce(::maxSeverity)
        }
        return ancestorIssues[key]?.severity
    }

    fun messagesFor(path: String): List<String> {
        val key = normalize(path)
        val exact = violationsByPath[key]?.map { it.message }.orEmpty()
        if (exact.isNotEmpty()) return exact
        val issue = ancestorIssues[key] ?: return emptyList()
        return listOf("${issue.count} tree-lint violation(s) inside")
    }

    fun clear() {
        clearState()
        listeners.forEach { it.scanFinished() }
    }

    fun addListener(listener: TreeLintScanListener): Disposable {
        listeners.add(listener)
        return Disposable { listeners.remove(listener) }
    }

    private fun clearState() {
        lastReport = null
        lastError = null
        projectRoot = null
        violationsByPath = emptyMap()
        ancestorIssues = emptyMap()
    }

    private fun normalize(path: String): String =
        TreeLintPaths.normalize(path, projectRoot, project.basePath)
}
