plugins {
    kotlin("jvm") version "1.9.22"
    id("com.gradleup.shadow") version "9.3.1"
    id("net.minecrell.plugin-yml.paper") version "0.6.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

group = "tech.bedson"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.triumphteam.dev/snapshots/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    implementation("dev.triumphteam:triumph-gui:3.1.13")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

kotlin {
    jvmToolchain(21)
}

// Use Mojang mappings for Paper plugins (recommended)
paperweight.reobfArtifactConfiguration =
    io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION

tasks {
    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")
        relocate("dev.triumphteam.gui", "tech.bedson.playerworldmanager.libs.gui")
        relocate("kotlin", "tech.bedson.playerworldmanager.libs.kotlin")
    }

    reobfJar {
        inputJar.set(shadowJar.flatMap { it.archiveFile })
    }

    assemble {
        dependsOn(reobfJar)
    }

    build {
        dependsOn(shadowJar)
    }
}

// Folia server directory
val foliaDir = layout.projectDirectory.dir("run")
val foliaJar = foliaDir.file("folia.jar")
val pluginsDir = foliaDir.dir("plugins")
val scriptsDir = layout.projectDirectory.dir(".claude/skills/folia/scripts")

// Download Folia server JAR
tasks.register<Exec>("downloadFolia") {
    group = "folia"
    description = "Download the latest Folia server JAR"
    workingDir = foliaDir.asFile
    commandLine("bash", scriptsDir.file("download-server.sh").asFile.absolutePath, "1.21.4")
    doFirst {
        foliaDir.asFile.mkdirs()
    }
    doLast {
        // Rename the downloaded JAR to folia.jar for consistency
        foliaDir.asFile.listFiles()?.filter { it.name.startsWith("folia-") && it.name.endsWith(".jar") }
            ?.maxByOrNull { it.lastModified() }?.renameTo(foliaJar.asFile)
    }
}

// Copy plugin JAR to Folia plugins folder
tasks.register<Copy>("deployPlugin") {
    group = "folia"
    description = "Copy the plugin JAR to the Folia server plugins folder"
    dependsOn("reobfJar")
    from(tasks.named("reobfJar").map { (it as io.papermc.paperweight.tasks.RemapJar).outputJar })
    into(pluginsDir)
    doFirst {
        pluginsDir.asFile.mkdirs()
    }
}

// Run Folia server interactively
tasks.register<Exec>("runFolia") {
    group = "folia"
    description = "Run the Folia server with the plugin"
    dependsOn("deployPlugin")
    workingDir = foliaDir.asFile
    commandLine("bash", scriptsDir.file("run-server.sh").asFile.absolutePath)
    standardInput = System.`in`
    doFirst {
        if (!foliaJar.asFile.exists()) {
            throw GradleException("Folia JAR not found. Run './gradlew downloadFolia' first.")
        }
    }
}

// Test plugin loading (for LLM use)
tasks.register<Exec>("testPlugin") {
    group = "folia"
    description = "Start Folia, wait for load, shutdown, and output logs for error checking"
    dependsOn("deployPlugin")
    workingDir = foliaDir.asFile
    commandLine("bash", scriptsDir.file("test-plugin.sh").asFile.absolutePath)
    doFirst {
        if (!foliaJar.asFile.exists()) {
            throw GradleException("Folia JAR not found. Run './gradlew downloadFolia' first.")
        }
    }
}

// All-in-one: download (if needed), build, deploy, test
tasks.register("testFolia") {
    group = "folia"
    description = "Download Folia (if needed), build plugin, deploy, and test loading"
    doFirst {
        if (!foliaJar.asFile.exists()) {
            println("Folia JAR not found, downloading...")
        }
    }
    doLast {
        println("\n=== Test completed. Check output above for errors. ===")
    }
}

// Configure testFolia dependencies dynamically
tasks.named("testFolia") {
    if (!foliaJar.asFile.exists()) {
        dependsOn("downloadFolia")
    }
    finalizedBy("testPlugin")
}

// Clean up test server files (but keep the JAR and plugins)
tasks.register<Delete>("cleanTestServer") {
    group = "folia"
    description = "Clean up test server files (logs, worlds) but keep the Folia JAR and plugins"
    delete(fileTree(foliaDir) {
        exclude("folia.jar", "plugins/**")
    })
}

// RCON command execution
val rconScript = scriptsDir.file("rcon.py").asFile.absolutePath

// Send a single RCON command (use -Pcmd="command")
tasks.register<Exec>("rcon") {
    group = "folia"
    description = "Send an RCON command to running server (use -Pcmd=\"command\")"
    commandLine("python3", rconScript, project.findProperty("cmd")?.toString() ?: "help")
    isIgnoreExitValue = true
}

// Start server in background for interactive testing
tasks.register<Exec>("startServer") {
    group = "folia"
    description = "Start Folia server in background with RCON enabled"
    dependsOn("deployPlugin")
    workingDir = foliaDir.asFile
    commandLine("bash", "-c", """
        ${scriptsDir.file("run-server.sh").asFile.absolutePath} &
        echo ${'$'}! > server.pid
        echo "Server starting in background (PID: ${'$'}(cat server.pid))"
        echo "Waiting for server to be ready..."
        sleep 15
        echo "Server should be ready. Use './gradlew rcon -Pcmd=\"command\"' to send commands"
        echo "Use './gradlew stopServer' to stop"
    """.trimIndent())
    doFirst {
        if (!foliaJar.asFile.exists()) {
            throw GradleException("Folia JAR not found. Run './gradlew downloadFolia' first.")
        }
    }
}

// Stop background server
tasks.register<Exec>("stopServer") {
    group = "folia"
    description = "Stop the background Folia server"
    workingDir = foliaDir.asFile
    commandLine("bash", "-c", """
        if [ -f server.pid ]; then
            PID=${'$'}(cat server.pid)
            echo "Sending stop command via RCON..."
            python3 $rconScript stop 2>/dev/null || true
            sleep 5
            if kill -0 ${'$'}PID 2>/dev/null; then
                echo "Server still running, sending SIGTERM..."
                kill ${'$'}PID 2>/dev/null || true
                sleep 3
            fi
            if kill -0 ${'$'}PID 2>/dev/null; then
                echo "Force killing server..."
                kill -9 ${'$'}PID 2>/dev/null || true
            fi
            rm -f server.pid
            echo "Server stopped"
        else
            echo "No server.pid file found"
        fi
    """.trimIndent())
    isIgnoreExitValue = true
}

paper {
    main = "tech.bedson.playerworldmanager.PlayerWorldManager"
    apiVersion = "1.21"
    foliaSupported = true
    name = "PlayerWorldManager"
    version = project.version.toString()
    description = "Create and manage personal worlds with friends"
    authors = listOf("prorickey")

    // Commands are registered programmatically via Brigadier
    // No commands section needed

    serverDependencies {
        register("PlaceholderAPI") {
            required = false
            load = net.minecrell.pluginyml.paper.PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
    }

    permissions {
        register("playerworldmanager.create") {
            description = "Allows creating personal worlds"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.TRUE
        }
        register("playerworldmanager.admin") {
            description = "Admin permissions for world management"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
        }
        register("playerworldmanager.admin.bypass") {
            description = "Bypass access restrictions for admin teleportation"
            default = net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Permission.Default.OP
        }
    }
}
