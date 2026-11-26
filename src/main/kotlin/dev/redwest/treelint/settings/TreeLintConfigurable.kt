package dev.redwest.treelint.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import java.awt.event.ItemListener
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

class TreeLintConfigurable : SearchableConfigurable {
    private val disabledRadio = JBRadioButton("Disabled", false)
    private val automaticRadio = JBRadioButton("Automatic: scan on save and file changes", false)
    private val manualRadio = JBRadioButton("Manual: scan only on demand", false)
    private val modeGroup = ButtonGroup().apply {
        add(disabledRadio)
        add(automaticRadio)
        add(manualRadio)
    }

    private val scanOnSaveCheckBox = JBCheckBox("Scan project on file save")
    private val showToolWindowCheckBox =
        JBCheckBox("Show the Tree-lint tool window when violations are found")

    // Advanced fields: visible only in MANUAL mode. In AUTOMATIC everything is
    // auto-detected (root config search, node from PATH, CLI from node_modules/.bin).
    private val executableField = TextFieldWithBrowseButton()
    private val nodeField = TextFieldWithBrowseButton()
    private val configField = TextFieldWithBrowseButton()
    private val timeoutSpinner = JBIntSpinner(120, 1, 3600)

    private var rootPanel: JPanel? = null
    private lateinit var advancedPanel: JPanel

    override fun getId(): String = "dev.redwest.treelint.settings"

    override fun getDisplayName(): String = "Tree-lint"

    override fun createComponent(): JComponent {
        val state = TreeLintSettings.getInstance().state
        selectMode(state.mode)
        scanOnSaveCheckBox.isSelected = state.scanOnSave
        showToolWindowCheckBox.isSelected = state.showToolWindowOnViolations
        executableField.text = state.executablePath
        nodeField.text = state.nodePath
        configField.text = state.configPath
        timeoutSpinner.number = state.timeoutSeconds

        executableField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
        )
        nodeField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
        )
        configField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor(),
        )

        val modePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(disabledRadio)
            add(automaticRadio)
            add(manualRadio)
        }

        advancedPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("tree-lint executable:", executableField)
            .addLabeledComponent("Node.js executable:", nodeField)
            .addLabeledComponent("Configuration file:", configField)
            .addLabeledComponent("Scan timeout (seconds):", timeoutSpinner)
            .panel

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Lint mode:", modePanel)
            .addComponent(scanOnSaveCheckBox)
            .addComponent(showToolWindowCheckBox)
            .addComponent(advancedPanel)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        rootPanel = panel

        val modeListener = ItemListener { updateModeUi() }
        disabledRadio.addItemListener(modeListener)
        automaticRadio.addItemListener(modeListener)
        manualRadio.addItemListener(modeListener)
        updateModeUi()

        return panel
    }

    private fun updateModeUi() {
        val mode = selectedMode()
        scanOnSaveCheckBox.isEnabled = mode != TreeLintMode.DISABLED
        showToolWindowCheckBox.isEnabled = mode != TreeLintMode.DISABLED
        advancedPanel.isVisible = mode == TreeLintMode.MANUAL
        rootPanel?.revalidate()
        rootPanel?.repaint()
    }

    override fun isModified(): Boolean {
        val state = TreeLintSettings.getInstance().state
        return selectedMode() != state.mode ||
            scanOnSaveCheckBox.isSelected != state.scanOnSave ||
            showToolWindowCheckBox.isSelected != state.showToolWindowOnViolations ||
            executableField.text != state.executablePath ||
            nodeField.text != state.nodePath ||
            configField.text != state.configPath ||
            timeoutSpinner.number != state.timeoutSeconds
    }

    override fun apply() {
        val state = TreeLintSettings.getInstance().state
        state.mode = selectedMode()
        state.scanOnSave = scanOnSaveCheckBox.isSelected
        state.showToolWindowOnViolations = showToolWindowCheckBox.isSelected
        state.executablePath = executableField.text.trim()
        state.nodePath = nodeField.text.trim()
        state.configPath = configField.text.trim()
        state.timeoutSeconds = timeoutSpinner.number

        ApplicationManager.getApplication().messageBus
            .syncPublisher(TreeLintSettings.SETTINGS_TOPIC)
            .settingsChanged()
    }

    override fun reset() {
        val state = TreeLintSettings.getInstance().state
        selectMode(state.mode)
        scanOnSaveCheckBox.isSelected = state.scanOnSave
        showToolWindowCheckBox.isSelected = state.showToolWindowOnViolations
        executableField.text = state.executablePath
        nodeField.text = state.nodePath
        configField.text = state.configPath
        timeoutSpinner.number = state.timeoutSeconds
        updateModeUi()
    }

    private fun selectMode(mode: TreeLintMode) {
        when (mode) {
            TreeLintMode.DISABLED -> disabledRadio
            TreeLintMode.AUTOMATIC -> automaticRadio
            TreeLintMode.MANUAL -> manualRadio
        }.isSelected = true
    }

    private fun selectedMode(): TreeLintMode = when {
        disabledRadio.isSelected -> TreeLintMode.DISABLED
        manualRadio.isSelected -> TreeLintMode.MANUAL
        else -> TreeLintMode.AUTOMATIC
    }
}
