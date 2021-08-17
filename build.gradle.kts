plugins {
    kotlin("js") version "1.5.21"
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

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// WEBPACK
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

// @TODO: HACK for webpack: https://youtrack.jetbrains.com/issue/KT-48273#focus=Comments-27-5122487.0-0
rootProject.plugins.withType(org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin::class.java) {
    rootProject.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>().versions.webpackDevServer.version = "4.0.0-rc.0"
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// ESBUILD
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

val esbuildFolder = File(rootProject.buildDir, "esbuild")
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
    val productionInfix = if (debug) "Development" else "Production"
    val browserPrepareEsbuild = when {
        debug -> browserPrepareEsbuildDebug
        else -> browserPrepareEsbuildRelease
    }

    // browserDebugEsbuild
    // browserReleaseEsbuild
    // browserDebugEsbuildRun
    // browserReleaseEsbuildRun
    tasks.create("browser${debugPrefix}Esbuild", Exec::class) {
        dependsOn(browserPrepareEsbuild)

        val jsPath = tasks.getByName("compile${productionInfix}ExecutableKotlinJs").outputs.files.first {
            it.extension.toLowerCase() == "js"
        }

        commandLine(ArrayList<Any>().apply {
            add(esbuildCmd)
            //add("--watch",)
            add("--bundle")
            add("--minify")
            add("--sourcemap=external")
            add(jsPath)
            add("--outfile=${File(wwwFolder, "${project.name}.js")}")
            // @TODO: Close this command on CTRL+C or use another webserver for this
            //if (run) add("--servedir=$wwwFolder")
        })
    }
}

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// RUN WITH A WEBBROWSER
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

configureWebserver()
