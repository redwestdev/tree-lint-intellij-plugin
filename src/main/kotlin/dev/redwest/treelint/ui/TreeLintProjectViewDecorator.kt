package dev.redwest.treelint.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import dev.redwest.treelint.model.TreeLintSeverity
import dev.redwest.treelint.service.TreeLintService

object TreeLintColors {
    val ERROR: JBColor = JBColor(0xC75450, 0xDB5860)
    val WARNING: JBColor = JBColor(0x8C6D1F, 0xC9A34C)
    val UNKNOWN: JBColor = JBColor(0x4A7DB0, 0x6FA3D0)
}

/**
 * Marks files and directories with tree-lint violations in the Project view
 * with a wavy underline in the severity color (like a spelling check); the
 * text itself keeps its default color. The severity comes from the tree-lint
 * config (violation type).
 */
class TreeLintProjectViewDecorator(private val project: Project) : ProjectViewNodeDecorator {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val file = node.virtualFile ?: return
        val service = project.service<TreeLintService>()
        val severity = service.severityFor(file.path) ?: return

        val text = data.presentableText
            ?: data.coloredText.joinToString("") { it.text }
        if (text.isBlank()) return

        data.tooltip = service.messagesFor(file.path).joinToString("\n")
        data.clearText()
        data.addText(text, attributesFor(severity))
    }

    private fun attributesFor(severity: TreeLintSeverity): SimpleTextAttributes {
        val color = when (severity) {
            TreeLintSeverity.ERROR -> TreeLintColors.ERROR
            TreeLintSeverity.WARNING -> TreeLintColors.WARNING
            TreeLintSeverity.UNKNOWN -> TreeLintColors.UNKNOWN
        }
        // Default text color, wavy underline in the severity color.
        return SimpleTextAttributes(null, null, color, SimpleTextAttributes.STYLE_WAVED)
    }
}
