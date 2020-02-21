import Poetry.importProject
import com.intellij.notification.NotificationGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.MessageType
import com.jetbrains.python.sdk.pythonSdk
import java.io.File

class PoetryStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        if (!File("${project.basePath}/pyproject.toml").exists()) {
            return
        }
        if (!project.pythonSdk?.homePath.isNullOrEmpty()) {
            return
        }
        val notificationGroup = NotificationGroup.balloonGroup("Poetry notifications")
        val msg = "Poetry project detected."
        val notification = notificationGroup.createNotification(msg, MessageType.INFO)
        val action = object : AnAction("Import") {
            override fun actionPerformed(e: AnActionEvent) {
                notification.expire()
                importProject(project)
            }
        }
        notification.addAction(action)
        notification.notify(project)
    }
}

