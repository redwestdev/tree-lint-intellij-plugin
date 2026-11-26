package dev.redwest.treelint.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic
import java.util.EventListener

fun interface TreeLintSettingsListener : EventListener {
    fun settingsChanged()
}

enum class TreeLintMode {
    DISABLED,
    AUTOMATIC,
    MANUAL,
}

@State(name = "TreeLintSettings", storages = [Storage("tree-lint.xml")])
@Service(Service.Level.APP)
class TreeLintSettings : PersistentStateComponent<TreeLintSettings.State> {
    data class State(
        var mode: TreeLintMode = TreeLintMode.AUTOMATIC,
        var scanOnSave: Boolean = true,
        var showToolWindowOnViolations: Boolean = true,
        var executablePath: String = "",
        var nodePath: String = "",
        var configPath: String = "",
        var timeoutSeconds: Int = 120,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        @JvmStatic
        val SETTINGS_TOPIC: Topic<TreeLintSettingsListener> =
            Topic("Tree-lint settings changed", TreeLintSettingsListener::class.java)

        fun getInstance(): TreeLintSettings = service()
    }
}
