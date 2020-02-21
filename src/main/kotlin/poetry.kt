import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationGroup
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.PyPackageManagerImpl
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.createSdkByGenerateTask
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.annotations.SystemDependent
import java.nio.file.Path

object Poetry {

    const val PYPROJECT_TOML = "pyproject.toml"
    const val POETRY_EXECUTABLE = "poetry"

    fun importProject(project: Project) { // configure or install new venv
        try {
            val poetrySdk: Sdk? = createSdkFromVenvUnderProgress(project)
            if (poetrySdk != null && poetrySdk.homePath != project.pythonSdk?.homePath) {
                setProjectSdk(project, poetrySdk)
            } else {
                updatePoetryDepsUnderProgress(project)
            }
        } catch (e: PoetryNotIntalledException) {
            showNotification(project, "Poetry not installed")
        }
    }

    private fun createSdkFromVenvUnderProgress(project: Project?, install: Boolean = true): Sdk? {
        val projectPath = project?.basePath
        val sdksModel = ProjectSdksModel().apply { reset(project) }
        val existingSdks = sdksModel.sdks.toList().filter { it.sdkType is PythonSdkType }

        val task = object : Task.WithResult<String, ExecutionException>(
            project,
            "Setting up poetry environment",
            true
        ) {
            override fun compute(indicator: ProgressIndicator): String {
                indicator.isIndeterminate = true
                val venvPath = if (install) {
                    getPoetryVenvOrInstall(FileUtil.toSystemDependentName(projectPath!!))
                } else {
                    getPoetryVenv(FileUtil.toSystemDependentName(projectPath!!))
                }
                return PythonSdkUtil.getPythonExecutable(venvPath) ?: FileUtil.join(venvPath, "bin", "python")
            }
        }
        val suggestedName = "Poetry (${projectPath?.let { PathUtil.getFileName(it) }})"
        return createSdkByGenerateTask(task, existingSdks, null, projectPath, suggestedName)
    }

    private fun updatePoetryDepsUnderProgress(project: Project?) {
        val projectPath = project?.basePath
        val task = object : Task.Backgroundable(project, "Updating dependencies", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                projectPath?.let { updatePoetryDeps(it) }
            }
        }
        ProgressManager.getInstance().run(task)
    }

    private fun getPoetryVenv(projectPath: String) = runPoetry(projectPath, "env","info","-p")


    private fun setProjectSdk(project: Project, sdk: Sdk) {
        SdkConfigurationUtil.addSdk(sdk)
        SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk)
        showNotification(project, "Configured venv to: ${sdk.homePath}")
    }

    private fun getPoetryVenvOrInstall(projectPath: String): String {
        return try {
            runPoetry(projectPath, "env","info","-p")
        } catch (e: PyExecutionException) {
            runPoetry(projectPath, "install")
            runPoetry(projectPath, "env","info","-p")
        }
    }

    private fun updatePoetryDeps(projectPath: String): String =
        runPoetry(projectPath, "update")


    private fun runPoetry(projectPath: @SystemDependent String, vararg args: String): String {
        val executable = getPoetryExecutable()
        val command = listOf(executable) + args
        val commandLine = GeneralCommandLine(command).withWorkDirectory(projectPath)
        val handler = CapturingProcessHandler(commandLine)
        val indicator = ProgressManager.getInstance().progressIndicator
        val result = with(handler) {
            when {
                indicator != null -> {
                    addProcessListener(PyPackageManagerImpl.IndicatedProcessOutputListener(indicator))
                    runProcessWithProgressIndicator(indicator)
                }
                else ->
                    runProcess()
            }
        }

        return with(result) {
            when {
                isCancelled ->
                    throw RunCanceledByUserException()
                exitCode != 0 ->
                    throw PyExecutionException("Error running ", executable, args.asList(),
                        stdout, stderr, exitCode, emptyList()
                    )
                else -> stdout
            }
        }
    }

    private fun getPoetryExecutable(): String {
        return PathEnvironmentVariableUtil.findInPath(POETRY_EXECUTABLE)?.path
                ?: throw PoetryNotIntalledException("")
    }

    private fun showNotification(project: Project?, message: String?) {
        val notificationGroup = NotificationGroup.balloonGroup("Poetry notifications")
        message?.let { notificationGroup.createNotification(it, MessageType.INFO) }?.notify(project)
    }
}