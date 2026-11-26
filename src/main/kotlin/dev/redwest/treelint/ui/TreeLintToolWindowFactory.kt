package dev.redwest.treelint.ui

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import dev.redwest.treelint.model.TreeLintViolation
import dev.redwest.treelint.runner.TreeLintConfigResolver
import dev.redwest.treelint.service.TreeLintScanManager
import dev.redwest.treelint.service.TreeLintService
import dev.redwest.treelint.settings.TreeLintMode
import dev.redwest.treelint.settings.TreeLintSettings
import dev.redwest.treelint.settings.TreeLintSettingsListener
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

class TreeLintToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TreeLintResultsPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        Disposer.register(content, panel)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val TOOL_WINDOW_ID = "Tree-lint"
    }
}

private class TreeLintResultsPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val model = ViolationTableModel()
    private val table = JBTable(model)
    private val status = JBLabel("Tree-lint has not run yet")
    private val scanButton = JButton("Scan project")
    private val service = project.service<TreeLintService>()

    init {
        scanButton.addActionListener {
            project.service<TreeLintScanManager>().runManualScan()
        }
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4)).apply { add(scanButton) }

        add(toolbar, BorderLayout.NORTH)
        add(ScrollPaneFactory.createScrollPane(table), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)

        table.rowHeight = 24
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    openSelectedViolation()
                }
            }
        })

        Disposer.register(this, service.addListener {
            ApplicationManager.getApplication().invokeLater { refresh() }
        })

        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(TreeLintSettings.SETTINGS_TOPIC, TreeLintSettingsListener { refresh() })

        refresh()
    }

    override fun dispose() = Unit

    private fun refresh() {
        val state = TreeLintSettings.getInstance().state
        val report = service.lastReport
        model.setViolations(report?.violations.orEmpty())
        scanButton.isEnabled = state.mode != TreeLintMode.DISABLED
        status.text = buildString {
            append(
                when {
                    state.mode == TreeLintMode.DISABLED -> "Tree-lint is disabled"
                    service.lastError != null -> "Last scan failed: ${service.lastError}"
                    report == null && state.mode == TreeLintMode.MANUAL ->
                        "Tree-lint has not run yet — click Scan project"
                    report == null -> "Tree-lint has not run yet"
                    else -> "Errors: ${report.errors}, warnings: ${report.warnings}"
                },
            )
            val config = resolveConfig(state)
            append("  ·  Config: ${config ?: "not found"}")
        }
    }

    private fun resolveConfig(state: TreeLintSettings.State): String? {
        state.configPath.trim().takeIf { it.isNotEmpty() }?.let { return File(it).name }
        val base = project.basePath ?: return null
        return TreeLintConfigResolver.findConfig(File(base))?.name
    }

    private fun openSelectedViolation() {
        val violation = model.violationAt(table.selectedRow) ?: return
        val file = LocalFileSystem.getInstance().findFileByPath(violation.path) ?: return
        if (file.isDirectory) {
            ProjectView.getInstance(project).select(null, file, true)
        } else {
            OpenFileDescriptor(project, file).navigate(true)
        }
    }
}

private class ViolationTableModel : AbstractTableModel() {
    private var violations: List<TreeLintViolation> = emptyList()

    fun setViolations(violations: List<TreeLintViolation>) {
        this.violations = violations
        fireTableDataChanged()
    }

    fun violationAt(row: Int): TreeLintViolation? = violations.getOrNull(row)

    override fun getRowCount(): Int = violations.size

    override fun getColumnCount(): Int = 3

    override fun getColumnName(column: Int): String = when (column) {
        0 -> "Severity"
        1 -> "Message"
        else -> "Path"
    }

    override fun getValueAt(row: Int, column: Int): Any {
        val violation = violations[row]
        return when (column) {
            0 -> violation.type
            1 -> violation.message
            else -> violation.path
        }
    }
}
