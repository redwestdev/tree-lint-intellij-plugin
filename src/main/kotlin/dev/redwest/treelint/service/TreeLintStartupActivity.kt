package dev.redwest.treelint.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Instantiates the scan manager when a project opens so that save/config-change
 * listeners are registered even if the tool window or Project view was never shown.
 */
class TreeLintStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.service<TreeLintScanManager>().scheduleInitialScan()
    }
}
