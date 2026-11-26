package dev.redwest.treelint.runner

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import dev.redwest.treelint.model.TreeLintReport
import dev.redwest.treelint.settings.TreeLintSettings
import java.io.File

class TreeLintException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

object TreeLintRunner {
    private val log = logger<TreeLintRunner>()

    fun scan(project: Project, indicator: ProgressIndicator): TreeLintReport {
        val basePath = project.basePath ?: throw TreeLintException("Project has no base path")
        val command = buildCommand(basePath)
        val output = CapturingProcessHandler(command).runProcessWithProgressIndicator(
            indicator,
            TreeLintSettings.getInstance().state.timeoutSeconds.coerceAtLeast(1) * 1000,
            true,
        )

        if (output.isTimeout) {
            throw TreeLintException("tree-lint scan timed out")
        }

        val stdout = output.stdout.trim()
        if (output.exitCode != 0 && stdout.isEmpty()) {
            val stderr = output.stderr.trim()
            val message = when {
                stderr.contains("Configuration file not found", ignoreCase = true) ->
                    "tree-lint config not found: create tree-lint.config.* in the project (run `tree-lint init`)."
                stderr.isNotEmpty() -> stderr
                else -> "tree-lint exited with code ${output.exitCode}"
            }
            throw TreeLintException(message)
        }
        if (stdout.isEmpty()) {
            throw TreeLintException("tree-lint produced no report")
        }

        return ReportParser.parse(stdout)
    }

    private fun buildCommand(basePath: String): GeneralCommandLine {
        val state = TreeLintSettings.getInstance().state
        val resolved = CommandResolver.resolve(
            basePath = basePath,
            configuredExecutable = state.executablePath.trim(),
            configuredNode = state.nodePath.trim(),
        )
        val command = GeneralCommandLine(resolved.program)
        command.addParameters(resolved.arguments)

        command.withWorkDirectory(File(basePath))
        command.withEnvironment("NO_COLOR", "1")
        command.addParameters("scan", basePath, "--json", "--no-fail-on-error")

        val configPath = state.configPath.trim()
        if (configPath.isNotEmpty()) {
            command.addParameters("--config-path", configPath)
            log.info("tree-lint config (from settings): $configPath")
        } else {
            TreeLintConfigResolver.findConfig(File(basePath))?.let {
                log.info("tree-lint config (auto-detected): ${it.absolutePath}")
            }
        }

        log.info("Running tree-lint: ${command.commandLineString}")
        return command
    }
}
