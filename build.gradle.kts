plugins {
    kotlin("js") version "1.5.10"
}

group = "com.soywiz"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    js(IR) {
        binaries.executable()
        browser {

        }
    }
}

val wwwFolder = File(buildDir, "www")
val esbuildFolder = File(buildDir, "esbuild")
val isWindows get() = org.apache.tools.ant.taskdefs.condition.Os.isFamily(org.apache.tools.ant.taskdefs.condition.Os.FAMILY_WINDOWS)
val esbuildCmd = if (isWindows) File(esbuildFolder, "esbuild.cmd") else File(esbuildFolder, "esbuild")
val env by lazy { org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin.apply(project.rootProject).requireConfigured() }
val npmCmd by lazy { File(env.nodeDir, if (env.isWindows) "npm.cmd" else "npm") }

val npmInstallEsbuild by tasks.creating(Exec::class) {
    onlyIf { !esbuildCmd.exists() }
    val esbuildVersion = "0.12.20"
    commandLine(npmCmd, "-g", "install", "esbuild@$esbuildVersion", "--prefix", esbuildFolder)
}

val browserEsbuildResources by tasks.creating(Copy::class) {
    for (sourceSet in kotlin.js().compilations.flatMap { it.kotlinSourceSets }) {
        from(sourceSet.resources)
    }
    into(wwwFolder)
}

val browserPrepareEsbuildPrepare by tasks.creating(Task::class) {
    dependsOn(browserEsbuildResources)
    dependsOn(npmInstallEsbuild)
}

val browserPrepareEsbuildDebug by tasks.creating(Task::class) {
    dependsOn("compileDevelopmentExecutableKotlinJs")
    dependsOn(browserPrepareEsbuildPrepare)
}

val browserPrepareEsbuildRelease by tasks.creating(Task::class) {
    dependsOn("compileProductionExecutableKotlinJs")
    dependsOn(browserPrepareEsbuildPrepare)
}

for (debug in listOf(false, true)) {
    val debugPrefix = if (debug) "Debug" else "Release"
    val browserPrepareEsbuild = when {
        debug -> browserPrepareEsbuildDebug
        else -> browserPrepareEsbuildRelease
    }

    for (run in listOf(false, true)) {
        val runSuffix = if (run) "Run" else ""

        // browserDebugEsbuild
        // browserDebugEsbuildRun
        // browserReleaseEsbuild
        // browserReleaseEsbuildRun
        tasks.create("browser${debugPrefix}Esbuild${runSuffix}", Exec::class) {
            dependsOn(browserPrepareEsbuild)

            commandLine(ArrayList<Any>().apply {
                add(esbuildCmd)
                //add("--watch",)
                add("--bundle")
                add("--minify")
                add(File(buildDir, "js/node_modules/${project.name}/kotlin/${project.name}.js"))
                add("--outfile=${File(wwwFolder, "${project.name}.js")}")
                // @TODO: Close this command on CTRL+C
                if (run) {
                    add("--servedir=$wwwFolder")
                }
            })
        }
    }
}
