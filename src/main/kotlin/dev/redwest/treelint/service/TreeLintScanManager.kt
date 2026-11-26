package dev.redwest.treelint.service

import com.intellij.ide.projectView.ProjectView
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Alarm
import dev.redwest.treelint.settings.TreeLintMode
import dev.redwest.treelint.settings.TreeLintSettings
import dev.redwest.treelint.settings.TreeLintSettingsListener
import dev.redwest.treelint.ui.TreeLintToolWindowFactory
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Schedules and runs tree-lint scans automatically: on file save and when the
 * tree-lint configuration changes. Scans are debounced and only one scan runs
 * at a time (a new trigger cancels the previous one).
 */
@Service(Service.Level.PROJECT)
class TreeLintScanManager(private val project: Project) : Disposable {

    private val settings get() = TreeLintSettings.getInstance().state
    private val service = project.service<TreeLintService>()
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, project)
    private val runningIndicator = AtomicReference<ProgressIndicator>()

    @Volatile
    private var lastNotifiedError: String? = null

    init {
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
            override fun beforeDocumentSaving(document: Document) = scheduleSaveScan()

            override fun fileWithNoDocumentChanged(file: VirtualFile) = scheduleSaveScan()
        })

        VirtualFileManager.getInstance().addAsyncFileListener(
            object : AsyncFileListener {
                override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
                    // Config and create/rename/delete changes are scanned by
                    // default in AUTOMATIC mode (no settings for that).
                    if (events.any { isConfigEvent(it) || isStructureEvent(it) }) {
                        scheduleAutoScan()
                    }
                    return null
                }
            },
            this,
        )

        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(
                TreeLintSettings.SETTINGS_TOPIC,
                TreeLintSettingsListener { onSettingsChanged() },
            )
    }

    override fun dispose() = Unit

    /**
     * Runs the first scan shortly after the project is opened.
     */
    fun scheduleInitialScan() {
        if (settings.mode != TreeLintMode.AUTOMATIC) return
        alarm.cancelAllRequests()
        alarm.addRequest({ scanNow() }, 1000)
    }

    /**
     * Manual trigger for the MANUAL mode (also available in AUTOMATIC).
     */
    fun runManualScan() {
        if (settings.mode == TreeLintMode.DISABLED) return
        alarm.cancelAllRequests()
        alarm.addRequest({ scanNow() }, 0)
    }

    private fun onSettingsChanged() {
        when (settings.mode) {
            TreeLintMode.DISABLED -> {
                alarm.cancelAllRequests()
                runningIndicator.getAndSet(null)?.cancel()
                service.clear()
                refreshProjectView()
            }
            TreeLintMode.AUTOMATIC -> {
                alarm.cancelAllRequests()
                alarm.addRequest({ scanNow() }, 0)
            }
            TreeLintMode.MANUAL -> {
                alarm.cancelAllRequests()
                runningIndicator.getAndSet(null)?.cancel()
            }
        }
    }

    /**
     * File saves: controlled by the "Scan project on file save" checkbox,
     * works in both AUTOMATIC and MANUAL modes.
     */
    private fun scheduleSaveScan() {
        if (!settings.scanOnSave) return
        if (settings.mode == TreeLintMode.DISABLED) return
        schedule(800)
    }

    /**
     * Config changes and create/rename/delete events: always scanned in
     * AUTOMATIC mode.
     */
    private fun scheduleAutoScan() {
        if (settings.mode != TreeLintMode.AUTOMATIC) return
        schedule(800)
    }

    private fun schedule(delayMs: Int) {
        alarm.cancelAllRequests()
        alarm.addRequest({ scanNow() }, delayMs)
    }

    private fun scanNow() {
        runningIndicator.getAndSet(null)?.cancel()

        val task = object : Task.Backgroundable(project, "Tree-lint: scanning project", true) {
            override fun run(indicator: ProgressIndicator) {
                runningIndicator.set(indicator)
                try {
                    service.scan(indicator)
                } finally {
                    runningIndicator.compareAndSet(indicator, null)
                }
            }

            override fun onSuccess() {
                lastNotifiedError = null
                refreshProjectView()
                val report = service.lastReport
                if (settings.showToolWindowOnViolations && !report?.violations.isNullOrEmpty()) {
                    ToolWindowManager.getInstance(project)
                        .getToolWindow(TreeLintToolWindowFactory.TOOL_WINDOW_ID)
                        ?.show()
                }
            }

            override fun onThrowable(error: Throwable) {
                notifyError(error.message ?: error.javaClass.simpleName)
                refreshProjectView()
            }
        }
        task.queue()
    }

    private fun notifyError(message: String) {
        if (message == lastNotifiedError) return
        lastNotifiedError = message
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Tree-lint")
            .createNotification("Tree-lint scan failed", message, NotificationType.ERROR)
            .notify(project)
    }

    private fun refreshProjectView() {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed) {
                ProjectView.getInstance(project).refresh()
            }
        }
    }

    private fun isConfigEvent(event: VFileEvent): Boolean {
        val file = event.file ?: return false
        return isConfigFile(file)
    }

    private fun isStructureEvent(event: VFileEvent): Boolean {
        val file = event.file ?: return false
        if (!isInProject(file)) return false
        return event is VFileCreateEvent ||
            event is VFileDeleteEvent ||
            event is VFileMoveEvent ||
            event is VFileCopyEvent ||
            (event is VFilePropertyChangeEvent && event.isRename)
    }

    private fun isInProject(file: VirtualFile): Boolean {
        val base = project.basePath ?: return false
        val root = File(base).absoluteFile.normalize().path.replace('\\', '/')
        val path = file.path.replace('\\', '/')
        if (path != root && !path.startsWith("$root/")) return false
        return !path.contains("/.git/") &&
            !path.contains("/.idea/") &&
            !path.contains("/node_modules/")
    }

    private fun isConfigFile(file: VirtualFile): Boolean {
        val configured = settings.configPath.trim()
        if (configured.isNotEmpty() &&
            (file.path == File(configured).absolutePath || file.name == File(configured).name)
        ) {
            return true
        }
        return file.name.startsWith("tree-lint.config")
    }
}
