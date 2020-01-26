import com.intellij.execution.ExecutionException
import com.intellij.execution.RunCanceledByUserException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.notification.NotificationGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.packaging.PyPackageManagerImpl
import com.jetbrains.python.sdk.*
import org.jetbrains.annotations.SystemDependent

// TODO: contextmenu actions

class PoetryAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val visible = file != null && file.name == "pyproject.toml"
        e.presentation.isEnabledAndVisible = visible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project!!
        val sdk: Sdk? = setupEnvUnderProgress(project)
        if (sdk != null) {
            SdkConfigurationUtil.addSdk(sdk)
        }
        SdkConfigurationUtil.setDirectoryProjectSdk(project, sdk)
        showNotification(project, "Updated venv for $project to: $sdk")
    }

    private fun showNotification(project: Project?, message: String?) {
        val notificationGroup = NotificationGroup.balloonGroup("SDK changed notification")
        message?.let { notificationGroup.createNotification(it, MessageType.INFO) }?.notify(project)
    }

    private fun setupEnvUnderProgress(project: Project?): Sdk? {
        val projectPath = project?.basePath
        val sdksModel = ProjectSdksModel().apply {
            reset(project)
        }
        val existingSdks = sdksModel.sdks.toList().filter { it.sdkType is PythonSdkType }
        val task = object : Task.WithResult<String, ExecutionException>(project, "Setting up poetry environment", true) {
            override fun compute(indicator: ProgressIndicator): String {
                indicator.isIndeterminate = true
                val venvPath = setupPoetry(FileUtil.toSystemDependentName(projectPath!!))
                return PythonSdkUtil.getPythonExecutable(venvPath) ?: FileUtil.join(venvPath, "bin", "python")
            }
        }
        val suggestedName = "Poetry (${projectPath?.let { PathUtil.getFileName(it) }})"
        return createSdkByGenerateTask(task, existingSdks, null, projectPath, suggestedName)
    }

    private fun setupPoetry(projectPath: String): String {
        runPoetry(projectPath, "install") // TODO: check if install is necessary
        return runPoetry(projectPath, "env","info","-p")

    }

    private fun runPoetry(projectPath: @SystemDependent String, vararg args: String): String {
        val executable = "poetry" // TODO: reliable way to get executable
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
                        stdout, stderr, exitCode, emptyList())
                else -> stdout
            }
        }
    }
}