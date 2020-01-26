import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;


class PoetryAction : AnAction() {

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val visible = file != null && file.name == "pyproject.toml"
        e.presentation.isEnabledAndVisible = visible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val envCmd: MutableList<String> = mutableListOf("poetry","env","info","-p")
        val installCmd: MutableList<String> = mutableListOf("poetry","install")

        val envPath = try {
            CmdExecutor.execute(envCmd, e.project)
        } catch (ex: ExecutionException) {
            CmdExecutor.execute(installCmd, e.project, withWindow = true)
            CmdExecutor.execute(envCmd, e.project)
        }

        println("set env to $envPath")

//        val sdk = SdkConfigurationUtil.createAndAddSDK(pythonExecutable, PythonSdkType.getInstance());

    }



}