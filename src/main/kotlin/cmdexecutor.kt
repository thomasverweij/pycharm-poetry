import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager


object CmdExecutor {
    private var window: ToolWindow? = null
    private var view: ConsoleView? = null
    private const val id = "Poetry"

    fun execute(command: MutableList<String>, project: Project?): String {
        val commandLine = GeneralCommandLine(command).withWorkDirectory(project?.basePath)
        val handler = CapturingProcessHandler(commandLine)
        val result = handler.runProcess()
        return with (result)  {
            when {
                exitCode != 0 -> throw ExecutionException("Non 0 exit code")
                else -> stdout
            }
        }
    }

    fun execute(command: MutableList<String>, project: Project?, withWindow: Boolean) {

        if (!withWindow) {
            return
        }

        try {
            val handler: OSProcessHandler
            handler = try {
                OSProcessHandler(
                    GeneralCommandLine(command).withWorkDirectory(project?.basePath)
                )
            } catch (ex: ExecutionException) {
                ex.printStackTrace()
                return
            }
            if (view == null) {
                val factory = TextConsoleBuilderFactory.getInstance()
                val builder = project?.let { factory.createBuilder(it) }
                if (builder != null) {
                    view = builder.console
                }
            }
            view!!.attachToProcess(handler)
            handler.startNotify()
            if (window == null) {
                val manager = project?.let { ToolWindowManager.getInstance(it) }
                if (manager != null) {
                    window = manager.registerToolWindow("Poetry", true, ToolWindowAnchor.BOTTOM)
                }
                val contentManager = window!!.contentManager
                val content = contentManager
                    .factory
                    .createContent(view!!.component, "", false)
                contentManager.addContent(content)
                window!!.show {}
            }

        } catch (e: Exception) {
            e.printStackTrace() //To change body of catch statement use File | Settings | File Templates.
        }
        return
    }
}