package dev.redwest.treelint.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import dev.redwest.treelint.service.TreeLintScanManager
import dev.redwest.treelint.settings.TreeLintMode
import dev.redwest.treelint.settings.TreeLintSettings

class ScanProjectAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        project.service<TreeLintScanManager>().runManualScan()
    }

    override fun update(event: AnActionEvent) {
        val project = event.project
        event.presentation.isEnabledAndVisible = project != null
        event.presentation.isEnabled =
            project != null && TreeLintSettings.getInstance().state.mode != TreeLintMode.DISABLED
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
