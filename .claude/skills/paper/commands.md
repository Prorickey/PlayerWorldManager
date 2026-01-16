# Paper Brigadier Command System

Paper uses Minecraft's Brigadier framework for commands, providing a modern, type-safe command API with client-side validation and tab completion.

## Key Advantages Over Bukkit Commands

- **Reduced parsing**: Framework handles argument validation automatically
- **Better UX**: Clients receive error checking before command execution
- **Datapack integration**: Commands work within reload events
- **Easier subcommands**: Hierarchical command structures are simpler to build
- **Type safety**: Arguments are parsed and validated at the framework level

## Command Registration

Commands are registered using the `LifecycleEventManager`, ensuring proper handling during server reloads.

### Via Plugin onEnable (Recommended for most plugins)

```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val registrar = event.registrar()

            // Register a command tree
            registrar.register(buildMyCommand())

            // Register with description and aliases
            registrar.register(
                buildAnotherCommand(),
                "Command description",
                listOf("alias1", "alias2")
            )
        }
    }

    private fun buildMyCommand(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("mycommand")
            .then(/* subcommands */)
            .executes { ctx -> Command.SINGLE_SUCCESS }
            .build()
    }
}
```

### Via PluginBootstrap (For datapack availability)

Commands registered in `onEnable` are not available to datapack functions. Use `PluginBootstrap` for datapack integration:

```kotlin
class MyPluginBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        context.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(buildCommand())
        }
    }
}
```

Declare in `paper-plugin.yml`:
```yaml
bootstrapper: com.example.plugin.MyPluginBootstrap
```

## Command Trees

Commands are structured as trees with literals and arguments as nodes.

### Basic Structure

```kotlin
Commands.literal("world")                    // /world
    .then(Commands.literal("create")         // /world create
        .then(Commands.argument("name", StringArgumentType.word())  // /world create <name>
            .executes { ctx -> handleCreate(ctx) }
        )
    )
    .then(Commands.literal("delete")         // /world delete
        .then(Commands.argument("name", StringArgumentType.word())
            .executes { ctx -> handleDelete(ctx) }
        )
    )
    .then(Commands.literal("list")           // /world list
        .executes { ctx -> handleList(ctx) }
    )
    .build()
```

### Node Types

| Type | Created With | Purpose |
|------|--------------|---------|
| **Literal** | `Commands.literal("name")` | Fixed text the user must type exactly |
| **Argument** | `Commands.argument("name", type)` | Variable input parsed by ArgumentType |

## Arguments and Literals

### Literals

Literals are fixed command branches. You know which branch executed based on tree structure:

```kotlin
Commands.literal("teleport")
    .then(Commands.literal("home")
        .executes { ctx ->
            // Executes for /teleport home
            Command.SINGLE_SUCCESS
        }
    )
    .then(Commands.literal("spawn")
        .executes { ctx ->
            // Executes for /teleport spawn
            Command.SINGLE_SUCCESS
        }
    )
```

### Arguments

Arguments accept variable input and return parsed values.

#### Primitive Argument Types

| Type | Return | Input Example | Notes |
|------|--------|---------------|-------|
| `BoolArgumentType.bool()` | Boolean | `true`/`false` | Boolean values only |
| `IntegerArgumentType.integer()` | Integer | `253`, `-123` | Any valid integer |
| `IntegerArgumentType.integer(min, max)` | Integer | `1-100` | Bounded range |
| `LongArgumentType.longArg()` | Long | `25418263123783` | Any valid long |
| `FloatArgumentType.floatArg()` | Float | `253.2`, `-25.0` | Any valid float |
| `FloatArgumentType.floatArg(min, max)` | Float | `0.0-1.0` | Bounded range |
| `DoubleArgumentType.doubleArg()` | Double | `4123.242`, `-1.1` | Any valid double |
| `StringArgumentType.word()` | String | `single_word` | Single word, alphanumeric + `+-_.` |
| `StringArgumentType.string()` | String | `"with spaces"` | Word or quoted string |
| `StringArgumentType.greedyString()` | String | `any text here` | Rest of input; must be last arg |

#### Accessing Argument Values

```kotlin
Commands.literal("setspeed")
    .then(Commands.argument("speed", FloatArgumentType.floatArg(0f, 1f))
        .executes { ctx ->
            // Method 1: Generic getArgument
            val speed = ctx.getArgument("speed", Float::class.java)

            // Method 2: Type-specific getter
            val speed2 = FloatArgumentType.getFloat(ctx, "speed")

            Command.SINGLE_SUCCESS
        }
    )
```

### Minecraft-Specific Arguments

Paper provides additional argument types via the `ArgumentTypes` class:

```kotlin
import io.papermc.paper.command.brigadier.argument.ArgumentTypes

// Player selector (single player)
Commands.argument("player", ArgumentTypes.player())

// Multiple players
Commands.argument("players", ArgumentTypes.players())

// Entity selector
Commands.argument("entity", ArgumentTypes.entity())

// World
Commands.argument("world", ArgumentTypes.world())

// Block position
Commands.argument("position", ArgumentTypes.blockPosition())

// Item stack
Commands.argument("item", ArgumentTypes.itemStack())

// Game mode
Commands.argument("mode", ArgumentTypes.gameMode())
```

#### Resolving Selectors

Entity and player selectors return resolvers that must be resolved:

```kotlin
Commands.argument("target", ArgumentTypes.player())
    .executes { ctx ->
        val resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver::class.java)
        val player = resolver.resolve(ctx.source).firstOrNull()

        if (player != null) {
            // Use player
        }

        Command.SINGLE_SUCCESS
    }
```

## Executors

Executors handle command logic via the `Command<S>` functional interface:

```kotlin
@FunctionalInterface
interface Command<S> {
    companion object {
        const val SINGLE_SUCCESS = 1
    }
    fun run(ctx: CommandContext<S>): Int
}
```

### CommandContext Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getSource()` | `CommandSourceStack` | Execution context |
| `getArgument(name, type)` | `T` | Parsed argument value |

### CommandSourceStack Methods

| Method | Returns | Description |
|--------|---------|-------------|
| `getSender()` | `CommandSender` | Who issued the command |
| `getExecutor()` | `Entity?` | Target entity (for `/execute as`) |
| `getLocation()` | `Location` | Command execution location |

### Complete Example

```kotlin
Commands.literal("flyspeed")
    .then(Commands.argument("speed", FloatArgumentType.floatArg(0f, 1f))
        .executes { ctx ->
            val speed = FloatArgumentType.getFloat(ctx, "speed")
            val sender = ctx.source.sender
            val executor = ctx.source.executor

            if (executor !is Player) {
                sender.sendMessage(Component.text("Only players can fly!", NamedTextColor.RED))
                return@executes Command.SINGLE_SUCCESS
            }

            executor.flySpeed = speed
            executor.sendMessage(Component.text("Flight speed set to $speed", NamedTextColor.GREEN))

            Command.SINGLE_SUCCESS
        }
    )
```

### Separating Logic with Method References

For complex commands, use method references:

```kotlin
.executes(::handleFlySpeed)

private fun handleFlySpeed(ctx: CommandContext<CommandSourceStack>): Int {
    // Implementation
    return Command.SINGLE_SUCCESS
}
```

## Requirements (Permissions)

Use `requires()` to restrict command access:

```kotlin
Commands.literal("admin")
    .requires { source ->
        source.sender.hasPermission("myplugin.admin")
    }
    .executes { ctx -> /* admin logic */ }
```

### Common Patterns

```kotlin
// Permission check
.requires { it.sender.hasPermission("myplugin.command") }

// Operator check
.requires { it.sender.isOp }

// Player-only command
.requires { it.executor is Player }

// Combined conditions
.requires { source ->
    source.sender.hasPermission("myplugin.use") &&
    source.executor is Player
}
```

### Updating Client Commands

After permission changes, sync with clients:

```kotlin
player.updateCommands()
```

## Suggestions (Tab Completion)

Use `suggests()` to provide tab completion:

```kotlin
Commands.argument("world", StringArgumentType.word())
    .suggests { ctx, builder ->
        // Filter suggestions by what user has typed
        Bukkit.getWorlds()
            .map { it.name }
            .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
            .forEach { builder.suggest(it) }

        builder.buildFuture()
    }
```

### SuggestionsBuilder Methods

| Method | Description |
|--------|-------------|
| `getInput()` | Full chat text including command |
| `getStart()` | Index where argument begins |
| `getRemaining()` | Current argument as typed |
| `getRemainingLowerCase()` | Lowercased for filtering |
| `suggest(String)` | Add a suggestion |
| `suggest(String, Message)` | Add suggestion with tooltip |
| `suggest(int)` | Add integer suggestion |
| `buildFuture()` | Return CompletableFuture<Suggestions> |

### Player Name Suggestions

```kotlin
.suggests { ctx, builder ->
    Bukkit.getOnlinePlayers()
        .map { it.name }
        .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
        .forEach { builder.suggest(it) }
    builder.buildFuture()
}
```

## BasicCommand (Simple Alternative)

For simple commands without complex argument parsing, use `BasicCommand`:

```kotlin
class MyBasicCommand : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        sender.sendMessage(Component.text("Hello!"))
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> {
        return listOf("suggestion1", "suggestion2")
    }

    override fun permission(): String? = "myplugin.basic"
}

// Registration
lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
    event.registrar().register("basiccmd", MyBasicCommand())
}
```

## Complete Command Example

```kotlin
class WorldCommand(
    private val plugin: JavaPlugin,
    private val worldManager: WorldManager
) {
    fun build(): LiteralCommandNode<CommandSourceStack> {
        return Commands.literal("world")
            .then(Commands.literal("create")
                .requires { it.sender.hasPermission("myplugin.world.create") }
                .then(Commands.argument("name", StringArgumentType.word())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests { _, builder ->
                            listOf("normal", "flat", "amplified", "void")
                                .filter { it.startsWith(builder.remainingLowerCase) }
                                .forEach { builder.suggest(it) }
                            builder.buildFuture()
                        }
                        .executes(::handleCreate)
                    )
                    .executes { ctx ->
                        // Default to normal type
                        handleCreateWithType(ctx, "normal")
                    }
                )
            )
            .then(Commands.literal("list")
                .executes(::handleList)
            )
            .then(Commands.literal("tp")
                .then(Commands.argument("world", StringArgumentType.word())
                    .suggests(::suggestOwnedWorlds)
                    .executes(::handleTeleport)
                )
            )
            .build()
    }

    private fun handleCreate(ctx: CommandContext<CommandSourceStack>): Int {
        val name = StringArgumentType.getString(ctx, "name")
        val type = StringArgumentType.getString(ctx, "type")
        return handleCreateWithType(ctx, type)
    }

    private fun handleCreateWithType(ctx: CommandContext<CommandSourceStack>, type: String): Int {
        val sender = ctx.source.sender
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can create worlds", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        val name = StringArgumentType.getString(ctx, "name")
        sender.sendMessage(Component.text("Creating world '$name' of type '$type'...", NamedTextColor.YELLOW))

        // Create world logic...

        return Command.SINGLE_SUCCESS
    }

    private fun handleList(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        // List worlds...
        return Command.SINGLE_SUCCESS
    }

    private fun handleTeleport(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can teleport", NamedTextColor.RED))
            return Command.SINGLE_SUCCESS
        }

        val worldName = StringArgumentType.getString(ctx, "world")
        // Teleport logic...

        return Command.SINGLE_SUCCESS
    }

    private fun suggestOwnedWorlds(
        ctx: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val sender = ctx.source.sender
        if (sender is Player) {
            worldManager.getWorldsByOwner(sender.uniqueId)
                .map { it.name }
                .filter { it.lowercase().startsWith(builder.remainingLowerCase) }
                .forEach { builder.suggest(it) }
        }
        return builder.buildFuture()
    }
}
```

## Migration from Bukkit Commands

| Bukkit | Brigadier |
|--------|-----------|
| `CommandExecutor.onCommand()` | `.executes { ctx -> }` |
| `TabCompleter.onTabComplete()` | `.suggests { ctx, builder -> }` |
| `args[0]`, `args[1]` | `ctx.getArgument("name", Type::class.java)` |
| `sender.hasPermission()` | `.requires { it.sender.hasPermission() }` |
| Manual arg parsing | `ArgumentType` handles validation |
| `plugin.yml` commands section | Programmatic registration |

## Resources

- [Paper Command API Introduction](https://docs.papermc.io/paper/dev/command-api/basics/introduction/)
- [Command Trees](https://docs.papermc.io/paper/dev/command-api/basics/command-tree/)
- [Arguments and Literals](https://docs.papermc.io/paper/dev/command-api/basics/arguments-and-literals/)
- [Executors](https://docs.papermc.io/paper/dev/command-api/basics/executors/)
- [Requirements](https://docs.papermc.io/paper/dev/command-api/basics/requirements/)
- [Suggestions](https://docs.papermc.io/paper/dev/command-api/basics/argument-suggestions/)
- [Minecraft Arguments](https://docs.papermc.io/paper/dev/command-api/arguments/minecraft/)
- [BasicCommand](https://docs.papermc.io/paper/dev/command-api/misc/basic-command/)
