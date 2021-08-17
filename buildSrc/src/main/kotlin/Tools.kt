import org.gradle.api.*
import java.io.*

val webBindAddress = "0.0.0.0"
val webBindPort = 0
val Project.wwwFolder get() = File(buildDir, "www")

val isWindows get() = org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS)
val isMacos get() = org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_MAC)
val isLinux get() = !isWindows && !isMacos
