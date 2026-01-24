import io.papermc.paperweight.tasks.RemapJar
import io.papermc.paperweight.userdev.ReobfArtifactConfiguration
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription
import net.minecrell.pluginyml.paper.PaperPluginDescription
import java.util.UUID
import java.time.Instant

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
    ReobfArtifactConfiguration.MOJANG_PRODUCTION

// Generate a unique build ID for each build
val buildId: String by lazy { UUID.randomUUID().toString().substring(0, 8) }
val buildTime: String by lazy { Instant.now().toString() }

// Build info generation - creates properties file with build metadata
fun generateBuildInfoFile(outputFile: File, isDebug: Boolean) {
    val newBuildId = UUID.randomUUID().toString().substring(0, 8)
    val newBuildTime = Instant.now().toString()
    outputFile.writeText("""
        build.id=$newBuildId
        build.time=$newBuildTime
        build.version=${project.version}
        build.debug=$isDebug
    """.trimIndent())
    println("Generated build ID: $newBuildId (debug=$isDebug)")
}

// Default build info generation (debug mode for development)
val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outputFile = file("src/main/resources/build-info.properties")
    outputs.file(outputFile)
    outputs.upToDateWhen { false }  // Always regenerate
    doLast {
        generateBuildInfoFile(outputFile, true)
    }
}

// Output directory for final JARs
val outputDir = layout.buildDirectory.dir("libs")
val generatedResourcesDir = layout.buildDirectory.dir("generated-resources")

tasks {
    jar {
        enabled = false
    }

    processResources {
        dependsOn(generateBuildInfo)
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

// ==========================================
// Debug Build - includes test commands
// ==========================================
val generateDebugBuildInfo = tasks.register("generateDebugBuildInfo") {
    val genDir = generatedResourcesDir.get().dir("debug")
    val outputFile = genDir.file("build-info.properties").asFile
    outputs.file(outputFile)
    outputs.upToDateWhen { false }
    doLast {
        genDir.asFile.mkdirs()
        generateBuildInfoFile(outputFile, true)
    }
}

val shadowJarDebug = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJarDebug") {
    dependsOn(generateDebugBuildInfo, tasks.named("classes"))
    configurations = listOf(project.configurations.runtimeClasspath.get())
    archiveClassifier.set("debug")
    archiveBaseName.set("PlayerWorldManager")
    destinationDirectory.set(outputDir)

    // First add main source output (which has default build-info.properties)
    from(sourceSets.main.get().output) {
        exclude("build-info.properties")
    }
    // Then add generated resources with debug build-info.properties
    from(generatedResourcesDir.map { it.dir("debug") })

    relocate("dev.triumphteam.gui", "tech.bedson.playerworldmanager.libs.gui")
    relocate("kotlin", "tech.bedson.playerworldmanager.libs.kotlin")

    manifest {
        attributes["Implementation-Title"] = "PlayerWorldManager (Debug)"
        attributes["Implementation-Version"] = project.version
    }
}

// ==========================================
// Release Build - excludes test commands
// ==========================================
val generateReleaseBuildInfo = tasks.register("generateReleaseBuildInfo") {
    val genDir = generatedResourcesDir.get().dir("release")
    val outputFile = genDir.file("build-info.properties").asFile
    outputs.file(outputFile)
    outputs.upToDateWhen { false }
    doLast {
        genDir.asFile.mkdirs()
        generateBuildInfoFile(outputFile, false)
    }
}

val shadowJarRelease = tasks.register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJarRelease") {
    dependsOn(generateReleaseBuildInfo, tasks.named("classes"))
    configurations = listOf(project.configurations.runtimeClasspath.get())
    archiveClassifier.set("release")
    archiveBaseName.set("PlayerWorldManager")
    destinationDirectory.set(outputDir)

    // First add main source output (exclude default build-info.properties and TestCommands)
    from(sourceSets.main.get().output) {
        exclude("build-info.properties")
        exclude("tech/bedson/playerworldmanager/commands/TestCommands.class")
        exclude("tech/bedson/playerworldmanager/commands/TestCommands\$*.class")
    }
    // Then add generated resources with release build-info.properties
    from(generatedResourcesDir.map { it.dir("release") })

    relocate("dev.triumphteam.gui", "tech.bedson.playerworldmanager.libs.gui")
    relocate("kotlin", "tech.bedson.playerworldmanager.libs.kotlin")

    manifest {
        attributes["Implementation-Title"] = "PlayerWorldManager (Release)"
        attributes["Implementation-Version"] = project.version
    }
}

// ==========================================
// Combined build task for both JARs
// ==========================================
tasks.register("buildAll") {
    group = "build"
    description = "Build both debug and release JARs"
    dependsOn(shadowJarDebug, shadowJarRelease)
    doLast {
        println("\nBuild complete! Output JARs:")
        println("  Debug:   build/libs/PlayerWorldManager-${project.version}-debug.jar")
        println("  Release: build/libs/PlayerWorldManager-${project.version}-release.jar")
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
    commandLine("bash", scriptsDir.file("download-server.sh").asFile.absolutePath)  // Fetches latest version automatically
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
    from(tasks.named("reobfJar").map { (it as RemapJar).outputJar })
    into(pluginsDir)
    doFirst {
        pluginsDir.asFile.mkdirs()
    }
}

// Configure server before running (EULA, server.properties, bukkit.yml)
fun configureServer() {
    val eulaFile = foliaDir.file("eula.txt").asFile
    eulaFile.writeText("eula=true\n")

    val worldsDir = foliaDir.dir("worlds").asFile
    worldsDir.mkdirs()

    val serverProps = foliaDir.file("server.properties").asFile
    serverProps.writeText("""
        online-mode=false
        spawn-protection=0
        max-players=20
        level-name=world
        gamemode=survival
        difficulty=normal
        allow-nether=true
        enable-command-block=true
        enable-rcon=true
        rcon.port=25575
        rcon.password=test
        debug=true
    """.trimIndent())

    val bukkitYml = foliaDir.file("bukkit.yml").asFile
    bukkitYml.writeText("""
        settings:
          world-container: worlds
    """.trimIndent())
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
        configureServer()
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

// Clean worlds and plugin data (keeps server JAR, configs, plugins)
tasks.register<Delete>("serverClean") {
    group = "folia"
    description = "Clean worlds and plugin data (keeps server JAR and plugins)"
    delete(foliaDir.dir("worlds"))
    delete(foliaDir.dir("world"))
    delete(foliaDir.dir("world_nether"))
    delete(foliaDir.dir("world_the_end"))
    delete(foliaDir.dir("logs"))
    delete(foliaDir.dir("plugins/PlayerWorldManager"))
    delete(foliaDir.file("server.log"))
    delete(foliaDir.file("server.pid"))
    doFirst {
        // Also delete any player world directories (pattern: *_*)
        foliaDir.asFile.listFiles()?.filter { it.isDirectory && it.name.contains("_") }?.forEach {
            it.deleteRecursively()
        }
        // Clean session.lock files
        foliaDir.asFile.walkTopDown().filter { it.name == "session.lock" }.forEach { it.delete() }
    }
}

// Clean everything in run directory (full reset)
tasks.register("cleanAll") {
    group = "folia"
    description = "Delete everything in run directory (requires re-download of Folia)"
    doLast {
        foliaDir.asFile.listFiles()?.forEach { it.deleteRecursively() }
        println("Run directory cleaned. Run './gradlew downloadFolia' to re-download.")
    }
}

// Alias for backwards compatibility
tasks.register("cleanTestServer") {
    group = "folia"
    description = "Alias for 'serverClean' task"
    dependsOn("serverClean")
}

// RCON command execution
val rconScript: String = scriptsDir.file("rcon.py").asFile.absolutePath

// Send a single RCON command (use -Pcmd="command")
tasks.register<Exec>("rcon") {
    group = "folia"
    description = "Send an RCON command to running server (use -Pcmd=\"command\")"
    commandLine("python3", rconScript, project.findProperty("cmd")?.toString() ?: "help")
    isIgnoreExitValue = true
}

// Start server in foreground (Ctrl+C to stop)
tasks.register<Exec>("startServer") {
    group = "folia"
    description = "Start Folia server (Ctrl+C to stop)"
    dependsOn("deployPlugin")
    workingDir = foliaDir.asFile
    standardInput = System.`in`
    commandLine("bash", "-c", """
        # Remove stale locks
        find . -name "session.lock" -delete 2>/dev/null || true

        echo "Starting Folia server..."
        echo "Press Ctrl+C to stop"
        echo ""

        # Start server in foreground
        exec java -Xms512M -Xmx2G -XX:+UseG1GC -jar folia.jar --nogui
    """.trimIndent())
    doFirst {
        if (!foliaJar.asFile.exists()) {
            throw GradleException("Folia JAR not found. Run './gradlew downloadFolia' first.")
        }
        configureServer()
    }
}

// Fresh start: clean worlds then start server
tasks.register("fresh") {
    group = "folia"
    description = "Clean worlds, rebuild plugin, and start fresh"
    dependsOn("serverClean")
    finalizedBy("startServer")
}

paper {
    main = "tech.bedson.playerworldmanager.PlayerWorldManager"
    apiVersion = "1.21"
    foliaSupported = true
    name = "PlayerWorldManager"
    version = project.version.toString()
    description = "Create and manage personal worlds with friends"
    authors = listOf("prorickey")

    serverDependencies {
        register("PlaceholderAPI") {
            required = false
            load = PaperPluginDescription.RelativeLoadOrder.BEFORE
        }
    }

    permissions {
        register("playerworldmanager.create") {
            description = "Allows creating personal worlds"
            default = BukkitPluginDescription.Permission.Default.TRUE
        }
        register("playerworldmanager.admin") {
            description = "Admin permissions for world management"
            default = BukkitPluginDescription.Permission.Default.OP
        }
        register("playerworldmanager.admin.bypass") {
            description = "Bypass access restrictions for admin teleportation"
            default = BukkitPluginDescription.Permission.Default.OP
        }
    }
}
