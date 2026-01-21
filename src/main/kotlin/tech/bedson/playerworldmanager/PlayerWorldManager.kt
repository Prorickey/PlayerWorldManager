package tech.bedson.playerworldmanager

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import tech.bedson.playerworldmanager.commands.ChatCommands
import tech.bedson.playerworldmanager.commands.TestCommands
import tech.bedson.playerworldmanager.commands.WorldAdminCommands
import tech.bedson.playerworldmanager.commands.WorldCommands
import tech.bedson.playerworldmanager.gui.AdminMenuGui
import tech.bedson.playerworldmanager.gui.MainMenuGui
import tech.bedson.playerworldmanager.listeners.AccessListener
import tech.bedson.playerworldmanager.listeners.ChatListener
import tech.bedson.playerworldmanager.listeners.WorldSessionListener
import tech.bedson.playerworldmanager.managers.ChatManager
import tech.bedson.playerworldmanager.managers.DataManager
import tech.bedson.playerworldmanager.managers.InviteManager
import tech.bedson.playerworldmanager.managers.WorldManager

@Suppress("UnstableApiUsage")
class PlayerWorldManager : JavaPlugin() {
    companion object {
        lateinit var instance: PlayerWorldManager
            private set
    }

    private lateinit var dataManager: DataManager
    private lateinit var worldManager: WorldManager
    private lateinit var inviteManager: InviteManager
    private lateinit var chatManager: ChatManager
    private var placeholderExpansion: PWMPlaceholderExpansion? = null

    // GUIs
    private lateinit var mainMenuGui: MainMenuGui
    private lateinit var adminMenuGui: AdminMenuGui

    override fun onEnable() {
        instance = this
        saveDefaultConfig()

        // Initialize managers
        dataManager = DataManager(dataFolder, logger)
        worldManager = WorldManager(this, dataManager)
        inviteManager = InviteManager(this, dataManager, worldManager)
        chatManager = ChatManager(this, dataManager)

        // Initialize GUIs
        mainMenuGui = MainMenuGui(this, worldManager, inviteManager, dataManager)
        adminMenuGui = AdminMenuGui(this, worldManager, inviteManager, dataManager)

        // Load data
        dataManager.loadAll()

        // Initialize world manager (processes pending deletions and loads worlds)
        worldManager.initialize()

        // Register Brigadier commands via LifecycleEventManager
        registerCommands()

        // Register listeners
        registerListeners()

        // Register PlaceholderAPI expansion if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = PWMPlaceholderExpansion(this, worldManager, inviteManager, dataManager)
            if (placeholderExpansion!!.register()) {
                logger.info("PlaceholderAPI expansion registered successfully!")
            } else {
                logger.warning("Failed to register PlaceholderAPI expansion")
            }
        } else {
            logger.info("PlaceholderAPI not found - placeholders will not be available")
        }

        logger.info("PlayerWorldManager enabled!")
    }

    override fun onDisable() {
        // Unregister PlaceholderAPI expansion
        placeholderExpansion?.unregister()

        // Save all data
        if (::dataManager.isInitialized) {
            dataManager.saveAll()
        }

        logger.info("PlayerWorldManager disabled!")
    }

    private fun registerCommands() {
        // Use LifecycleEventManager for Brigadier command registration
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()

            // World command
            val worldCommands = WorldCommands(this, worldManager, inviteManager, dataManager, mainMenuGui)
            registrar.register(
                worldCommands.build(),
                "Manage your personal worlds",
                listOf("w", "worlds")
            )

            // Chat command
            val chatCommands = ChatCommands(this, chatManager)
            registrar.register(
                chatCommands.build(),
                "Change your chat mode",
                listOf("chatmode")
            )

            // World admin command
            val worldAdminCommands = WorldAdminCommands(this, worldManager, inviteManager, dataManager, adminMenuGui)
            registrar.register(
                worldAdminCommands.build(),
                "Admin commands for world management",
                listOf("wa", "wadmin")
            )

            // Console test commands (for LLM/automated testing)
            val testCommands = TestCommands(this, worldManager, inviteManager, dataManager)
            registrar.register(
                testCommands.build(),
                "Console test commands for automated testing",
                listOf("pwmt")
            )

            logger.info("Brigadier commands registered!")
        }
    }

    private fun registerListeners() {
        val pluginManager = Bukkit.getPluginManager()

        // Access control listener
        pluginManager.registerEvents(AccessListener(this, worldManager, inviteManager), this)

        // Chat listener
        pluginManager.registerEvents(ChatListener(this, chatManager, worldManager), this)

        // World session persistence listener (saves location on quit, restores on join)
        pluginManager.registerEvents(WorldSessionListener(this, worldManager, dataManager), this)

        logger.info("Listeners registered!")
    }

    fun getDataManager(): DataManager = dataManager
    fun getWorldManager(): WorldManager = worldManager
    fun getInviteManager(): InviteManager = inviteManager
    fun getChatManager(): ChatManager = chatManager
    fun getMainMenuGui(): MainMenuGui = mainMenuGui
    fun getAdminMenuGui(): AdminMenuGui = adminMenuGui
}
