package tech.bedson.playerworldmanager

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.CraftServer
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.commands.ChatCommands
import tech.bedson.playerworldmanager.commands.StatsCommands
import tech.bedson.playerworldmanager.commands.TestCommands
import tech.bedson.playerworldmanager.commands.WorldAdminCommands
import tech.bedson.playerworldmanager.commands.WorldCommands
import tech.bedson.playerworldmanager.gui.AdminMenuGui
import tech.bedson.playerworldmanager.gui.MainMenuGui
import tech.bedson.playerworldmanager.gui.WorldStatsGui
import tech.bedson.playerworldmanager.listeners.AccessListener
import tech.bedson.playerworldmanager.listeners.ChatListener
import tech.bedson.playerworldmanager.listeners.StatsListener
import tech.bedson.playerworldmanager.listeners.WorldSessionListener
import tech.bedson.playerworldmanager.managers.ChatManager
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.StatsManager
import tech.bedson.playerworldmanager.managers.WorldManager
import tech.bedson.playerworldmanager.managers.WorldStateManager
import tech.bedson.playerworldmanager.utils.DebugLogger
import java.io.File
import java.nio.file.Paths

@Suppress("UnstableApiUsage")
class PlayerWorldManager : JavaPlugin() {
    companion object {
        lateinit var instance: PlayerWorldManager
            private set

        // Build info loaded from build-info.properties
        var buildId: String = "unknown"
            private set
        var buildTime: String = "unknown"
            private set
        var buildVersion: String = "unknown"
            private set
    }

    private lateinit var debugLogger: DebugLogger
    private lateinit var dataManager: DataManager
    private lateinit var worldManager: WorldManager
    private lateinit var worldStateManager: WorldStateManager
    private lateinit var inviteManager: InviteManager
    private lateinit var chatManager: ChatManager
    private lateinit var statsManager: StatsManager
    private var placeholderExpansion: PWMPlaceholderExpansion? = null

    // GUIs
    private lateinit var mainMenuGui: MainMenuGui
    private lateinit var adminMenuGui: AdminMenuGui
    private lateinit var worldStatsGui: WorldStatsGui

    override fun onEnable() {
        instance = this

        // Load build info
        loadBuildInfo()

        saveDefaultConfig()

        // Initialize debug logger after config is loaded
        debugLogger = DebugLogger(this, "PlayerWorldManager")
        debugLogger.debugMethodEntry("onEnable")

        debugLogger.debug("Config loaded",
            "debug" to config.getBoolean("debug", false),
            "dataFolder" to dataFolder.absolutePath
        )

        // Initialize managers
        debugLogger.debug("Initializing managers...")
        dataManager = DataManager(this, dataFolder)
        debugLogger.debug("DataManager created")
        worldStateManager = WorldStateManager(this, dataManager)
        debugLogger.debug("WorldStateManager created")
        worldManager = WorldManager(this, dataManager, worldStateManager)
        debugLogger.debug("WorldManager created")
        inviteManager = InviteManager(this, dataManager, worldManager)
        debugLogger.debug("InviteManager created")
        chatManager = ChatManager(this, dataManager)
        debugLogger.debug("ChatManager created")
        statsManager = StatsManager(this, dataManager, worldManager)
        debugLogger.debug("StatsManager created")

        // Initialize GUIs
        debugLogger.debug("Initializing GUIs...")
        mainMenuGui = MainMenuGui(this, worldManager, inviteManager, dataManager, statsManager)
        debugLogger.debug("MainMenuGui created")
        adminMenuGui = AdminMenuGui(this, worldManager, inviteManager, dataManager)
        debugLogger.debug("AdminMenuGui created")
        worldStatsGui = WorldStatsGui(this, statsManager, worldManager, inviteManager, dataManager)
        debugLogger.debug("WorldStatsGui created")

        // Load data
        debugLogger.debug("Loading data from disk...")
        dataManager.loadAll()
        debugLogger.debug("Data loaded",
            "worlds" to dataManager.getAllWorlds().size,
            "players" to dataManager.getAllPlayerData().size
        )

        // Load statistics
        debugLogger.debug("Loading statistics from disk...")
        statsManager.loadAll()
        debugLogger.debug("Statistics loaded",
            "worldStats" to statsManager.getAllWorldStats().size
        )

        // Initialize world manager (processes pending deletions and loads worlds)
        debugLogger.debug("Initializing WorldManager (processing pending deletions and loading worlds)...")
        worldManager.initialize()
        debugLogger.debug("WorldManager initialized")

        // Register Brigadier commands via LifecycleEventManager
        debugLogger.debug("Registering commands...")
        registerCommands()

        // Register listeners
        debugLogger.debug("Registering listeners...")
        registerListeners()

        // Register PlaceholderAPI expansion if available
        debugLogger.debug("Checking for PlaceholderAPI...")
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            debugLogger.debug("PlaceholderAPI found, registering expansion...")
            placeholderExpansion = PWMPlaceholderExpansion(this, worldManager, inviteManager, dataManager)
            if (placeholderExpansion!!.register()) {
                logger.info("PlaceholderAPI expansion registered successfully!")
                debugLogger.debug("PlaceholderAPI expansion registered")
            } else {
                logger.warning("Failed to register PlaceholderAPI expansion")
                debugLogger.debug("PlaceholderAPI expansion registration failed")
            }
        } else {
            logger.info("PlaceholderAPI not found - placeholders will not be available")
            debugLogger.debug("PlaceholderAPI not found")
        }

        if(server.worldContainer == File(".")) {
            logger.warning("You have not changed your bukkit world container yet. PlayerWorldManager will generate a lot of worlds and will pollute your server folder quick. We suggest you set settings.world-container in bukkit.yml to \"worlds\" to help clean things up.")
        }

        debugLogger.debug("Server configuration",
            "worldContainer" to server.worldContainer.absolutePath,
            "onlineMode" to server.onlineMode,
            "maxPlayers" to server.maxPlayers
        )

        logger.info("PlayerWorldManager enabled! (Build: $buildId)")
        debugLogger.debugMethodExit("onEnable")
    }

    private fun loadBuildInfo() {
        // Note: debugLogger not yet initialized here, using standard logger for build info
        try {
            val stream = getResource("build-info.properties")
            if (stream != null) {
                val props = java.util.Properties()
                props.load(stream)
                buildId = props.getProperty("build.id", "unknown")
                buildTime = props.getProperty("build.time", "unknown")
                buildVersion = props.getProperty("build.version", "unknown")
                logger.info("Build info loaded: ID=$buildId, Time=$buildTime, Version=$buildVersion")
            } else {
                logger.warning("build-info.properties not found in JAR")
            }
        } catch (e: Exception) {
            logger.warning("Failed to load build info: ${e.message}")
        }
    }

    override fun onDisable() {
        if (::debugLogger.isInitialized) {
            debugLogger.debugMethodEntry("onDisable")
        }

        // Unregister PlaceholderAPI expansion
        if (::debugLogger.isInitialized) {
            debugLogger.debug("Unregistering PlaceholderAPI expansion...")
        }
        placeholderExpansion?.unregister()

        // Save all statistics
        if (::statsManager.isInitialized) {
            if (::debugLogger.isInitialized) {
                debugLogger.debug("Saving all statistics to disk...")
            }
            statsManager.saveAll()
            if (::debugLogger.isInitialized) {
                debugLogger.debug("Statistics saved")
            }
        }

        // Save all data
        if (::dataManager.isInitialized) {
            if (::debugLogger.isInitialized) {
                debugLogger.debug("Saving all data to disk...")
            }
            dataManager.saveAll()
            if (::debugLogger.isInitialized) {
                debugLogger.debug("Data saved")
            }
        }

        logger.info("PlayerWorldManager disabled!")
        if (::debugLogger.isInitialized) {
            debugLogger.debugMethodExit("onDisable")
        }
    }

    private fun registerCommands() {
        debugLogger.debugMethodEntry("registerCommands")

        // Use LifecycleEventManager for Brigadier command registration
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            debugLogger.debug("LifecycleEvents.COMMANDS handler triggered")
            val registrar = event.registrar()

            // World command
            debugLogger.debug("Registering WorldCommands (/world, /w, /worlds)...")
            val worldCommands = WorldCommands(this, worldManager, inviteManager, dataManager, mainMenuGui)
            registrar.register(
                worldCommands.build(),
                "Manage your personal worlds",
                listOf("w", "worlds")
            )
            debugLogger.debug("WorldCommands registered")

            // Chat command
            debugLogger.debug("Registering ChatCommands (/chat, /chatmode)...")
            val chatCommands = ChatCommands(this, chatManager)
            registrar.register(
                chatCommands.build(),
                "Change your chat mode",
                listOf("chatmode")
            )
            debugLogger.debug("ChatCommands registered")

            // World admin command
            debugLogger.debug("Registering WorldAdminCommands (/worldadmin, /wa, /wadmin)...")
            val worldAdminCommands = WorldAdminCommands(this, worldManager, inviteManager, dataManager, adminMenuGui)
            registrar.register(
                worldAdminCommands.build(),
                "Admin commands for world management",
                listOf("wa", "wadmin")
            )
            debugLogger.debug("WorldAdminCommands registered")

            // Stats command
            debugLogger.debug("Registering StatsCommands (/stats, /worldstats)...")
            val statsCommands = StatsCommands(this, statsManager, worldManager, dataManager, worldStatsGui)
            registrar.register(
                statsCommands.build(),
                "View world statistics",
                listOf("worldstats")
            )
            debugLogger.debug("StatsCommands registered")

            // Console test commands (for LLM/automated testing)
            debugLogger.debug("Registering TestCommands (/pwmtest, /pwmt)...")
            val testCommands = TestCommands(this, worldManager, inviteManager, dataManager)
            registrar.register(
                testCommands.build(),
                "Console test commands for automated testing",
                listOf("pwmt")
            )
            debugLogger.debug("TestCommands registered")

            logger.info("Brigadier commands registered!")
        }

        debugLogger.debugMethodExit("registerCommands")
    }

    private fun registerListeners() {
        debugLogger.debugMethodEntry("registerListeners")

        val pluginManager = Bukkit.getPluginManager()

        // Access control listener
        debugLogger.debug("Registering AccessListener...")
        pluginManager.registerEvents(AccessListener(this, worldManager, inviteManager), this)
        debugLogger.debug("AccessListener registered")

        // Chat listener
        debugLogger.debug("Registering ChatListener...")
        pluginManager.registerEvents(ChatListener(this, chatManager, worldManager), this)
        debugLogger.debug("ChatListener registered")

        // World session persistence listener (saves state on quit, restores on join)
        debugLogger.debug("Registering WorldSessionListener...")
        pluginManager.registerEvents(WorldSessionListener(this, worldManager, worldStateManager, dataManager), this)
        debugLogger.debug("WorldSessionListener registered")

        // Statistics listener (tracks blocks, kills, deaths, etc.)
        debugLogger.debug("Registering StatsListener...")
        pluginManager.registerEvents(StatsListener(this, statsManager, worldManager), this)
        debugLogger.debug("StatsListener registered")

        logger.info("Listeners registered!")
        debugLogger.debugMethodExit("registerListeners")
    }

    fun getDataManager(): DataManager = dataManager
    fun getWorldManager(): WorldManager = worldManager
    fun getWorldStateManager(): WorldStateManager = worldStateManager
    fun getInviteManager(): InviteManager = inviteManager
    fun getChatManager(): ChatManager = chatManager
    fun getStatsManager(): StatsManager = statsManager
    fun getMainMenuGui(): MainMenuGui = mainMenuGui
    fun getAdminMenuGui(): AdminMenuGui = adminMenuGui
    fun getWorldStatsGui(): WorldStatsGui = worldStatsGui
    fun getDebugLogger(): DebugLogger = debugLogger
}
