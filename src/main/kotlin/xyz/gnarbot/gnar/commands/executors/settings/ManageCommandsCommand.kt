package xyz.gnarbot.gnar.commands.executors.settings

import net.dv8tion.jda.core.Permission
import xyz.gnarbot.gnar.commands.Category
import xyz.gnarbot.gnar.commands.Command
import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.commands.template.CommandTemplate
import xyz.gnarbot.gnar.commands.template.Description
import xyz.gnarbot.gnar.commands.template.Parser
import xyz.gnarbot.gnar.commands.template.Parsers
import xyz.gnarbot.gnar.guilds.suboptions.CommandOptions
import xyz.gnarbot.gnar.guilds.suboptions.CommandOptionsOverride
import xyz.gnarbot.gnar.utils.Context
import java.util.*

private val override: Map<Class<*>, Parser<*>> = HashMap(Parsers.PARSER_MAP).also {
    it[String::class.java] = Parser<String>("@user|@role|@channel|*", "Name or mention of a user, role, or channel") { _, s -> s }
}

@Command(
        id = 54,
        aliases = arrayOf("commands", "cmd", "command", "cmds"),
        usage = "(enable|disable|toggle) (specific|category) (_command|category) ...",
        description = "Manage the usage of commands.",
        toggleable = false,
        category = Category.SETTINGS,
        permissions = arrayOf(Permission.MANAGE_SERVER)
)
class ManageCommandsCommand : CommandTemplate(override) {
    @Description("Completely disable or enable a command.")
    fun toggle_specific(context: Context, cmd: CommandExecutor) {
        options(context, cmd) {
            it.isEnabled = !it.isEnabled
            context.data.save()

            if (it.isEnabled) {
                context.send().info("The command ${cmd.info.aliases[0]} is now enabled.").queue()
            } else {
                context.send().info("The command ${cmd.info.aliases[0]} is now disabled.").queue()
            }
        }
    }

    @Description("Completely disable or enable a category.")
    fun toggle_category(context: Context, category: Category) {
        options(context, category) {
            it.isEnabled = !it.isEnabled
            context.data.save()

            if (it.isEnabled) {
                context.send().info("The category ${category.title} is now enabled.").queue()
            } else {
                context.send().info("The category ${category.title} is now disabled.").queue()
            }
        }
    }

    @Description(value = "Enable a command for a user/role/channel.")
    fun enable_specific(context: Context, cmd: CommandExecutor, scope: ManageScope, entity: String) {
        // parent exists
        // NULL scope options for this command
        //
        // enable * ?
        //      create or inherit options and create an empty list for scope
        // enable entity ?
        //      create or inherit options and subtract entity from parent scope
        val options = context.data.command.options[cmd.info.id]
        if (options == null || scope.rawTransform(options) == null) {
            val desyncString = "\u26A0De-synced from the category option `${cmd.info.category.title}`."

            if (entity == "*") {
                options(context, cmd.info.category) { categoryOptions ->
                    val list = scope.rawTransform(categoryOptions)
                    if (list == null || list.isEmpty()) {
                        context.send().error("`${cmd.info.aliases[0]}` is not disabled for any ${scope.name.toLowerCase()}.").queue()
                        return
                    }

                    (options?.copy() ?: CommandOptions()).let {
                        context.data.command.options.put(cmd.info.id, it)
                        scope.transform(it)
                    }
                    context.data.save()

                    context.send().embed("Command Management") {
                        desc {
                            "Enabled `${cmd.info.aliases[0]}` for every ${scope.name.toLowerCase()}.\n$desyncString"
                        }
                    }.action().queue()
                    return
                }
            } else entity(context, scope, entity)?.let { (item, itemDisplay) ->
                val all = HashSet<String>()
                options(context, cmd.info.category) { all.addAll(scope.transform(it)) }

                if (!all.remove(item)) {
                    context.send().error("`${cmd.info.aliases[0]}` is not disabled for $itemDisplay.").queue()
                    return
                }

                (options?.copy() ?: CommandOptions()).let {
                    context.data.command.options.put(cmd.info.id, it)
                    scope.transform(it).addAll(all)
                }
                context.data.save()

                context.send().embed("Command Management") {
                    desc {
                        "`${cmd.info.aliases[0]}` is now allowed for $itemDisplay.\n$desyncString"
                    }
                }.action().queue()
                return
            }
        }

        options(context, cmd) {
            if (entity == "*") {
                allowAll(context, it, cmd.info.aliases[0], scope)
            } else entity(context, scope, entity)?.let { (item, itemDisplay) ->
                allowTo(context, item, itemDisplay, it, scope, cmd.info.aliases[0])
            }
        }
    }

    @Description(value = "Enable a category for a user/role/channel.")
    fun enable_category(context: Context, category: Category, scope: ManageScope, entity: String) {
        options(context, category) {
            if (entity == "*") {
                allowAll(context, it, category.title, scope)
            } else entity(context, scope, entity)?.let { (item, itemDisplay) ->
                allowTo(context, item, itemDisplay, it, scope, category.title)
            }
        }
    }

    @Description(value ="Disable a command for a user/role/channel.")
    fun disable_specific(context: Context, cmd: CommandExecutor, scope: ManageScope, entity: String) {
        val options = context.data.command.options[cmd.info.id]
        if (options == null || scope.rawTransform(options) == null) {
            val desyncString = "\u26A0De-synced from the category option `${cmd.info.category.title}`."

            if (entity == "*") {
                options(context, cmd.info.category) { categoryOptions ->
                    val set = HashSet(scope.rawTransform(categoryOptions) ?: Collections.emptySet())
                    val all = scope.all(context)

                    if (!set.addAll(all)) {
                        context.send().error("`${cmd.info.aliases[0]}` is already disabled for every ${scope.name.toLowerCase()}.").queue()
                        return
                    }

                    (options?.copy() ?: CommandOptions()).let {
                        context.data.command.options.put(cmd.info.id, it)
                        scope.transform(it).addAll(set)
                    }
                    context.data.save()

                    context.send().embed("Command Management") {
                        desc {
                            "Disabled the command for every ${scope.name.toLowerCase()}.\n$desyncString"
                        }
                    }.action().queue()
                    return
                }
            } else entity(context, scope, entity)?.let { (item, itemDisplay) ->
                val all = HashSet<String>()
                options(context, cmd.info.category) { all.addAll(scope.transform(it)) }

                if (!all.add(item)) {
                    context.send().error("`${cmd.info.aliases[0]}` is already disabled for $itemDisplay.").queue()
                    return
                }

                (options?.copy() ?: CommandOptions()).let {
                    context.data.command.options.put(cmd.info.id, it)
                    scope.transform(it).addAll(all)
                }
                context.data.save()

                context.send().embed("Command Management") {
                    desc {
                        "`${cmd.info.aliases[0]}` is now disabled for $itemDisplay.\n$desyncString"
                    }
                }.action().queue()
                return
            }
        }

        options(context, cmd) {
            if (entity == "*") {
                disallowAll(context, it, cmd.info.aliases[0], scope)
            } else entity(context, scope, entity)?.let { (item, itemDisplay) ->
                disallowTo(context, item, itemDisplay, it, scope, cmd.info.aliases[0])
            }
        }
    }

    @Description(value = "Disable a category for a user/role/channel.")
    fun disable_category(context: Context, category: Category, scope: ManageScope, entity: String) {
        options(context, category) {
            if (entity == "*") {
                disallowAll(context, it, category.title, scope)
            } else entity(context, scope, entity)?.let { (item, itemDisplay) ->
                disallowTo(context, item, itemDisplay, it, scope, category.title)
            }
        }
    }

    @Description(value = "Show the options of the command.")
    fun options_specific(context: Context, cmd: CommandExecutor) {
        val options = context.data.command.let { CommandOptionsOverride(it.options[cmd.info.id], it.categoryOptions[cmd.info.category.ordinal]) }

        val inheritString = "\uD83D\uDD01 Synced with the options from the command category `${cmd.info.category.title}`.\n"

        context.send().embed("Command Management") {
            field("Toggle") {
                if (!options.isEnabled) {
                    if (options.inheritToggle()) {
                        "Disabled by the category."
                    } else {
                        "Disabled."
                    }
                } else {
                    "Enabled."
                }
            }

            field("Disabled Users") {
                options.disabledUsers.let {
                    if (it.isEmpty()) {
                        "This command is not disabled for any users."
                    } else {
                        buildString {
                            if (options.inheritUsers()) append(inheritString)
                            it.mapNotNull(context.guild::getMemberById).forEach { append("• ").append(it.asMention).append('\n') }
                        }
                    }
                }
            }

            field("Disabled Roles") {
                options.disabledRoles.let {
                    if (it.isEmpty()) {
                        "This command is not disabled for any roles."
                    } else {
                        buildString {
                            if (options.inheritRoles()) append(inheritString)
                            it.mapNotNull(context.guild::getRoleById).forEach { append("• ").append(it.asMention).append('\n') }
                        }
                    }
                }
            }

            field("Disabled Channels") {
                options.disabledChannels.let {
                    if (it.isEmpty()) {
                        "This command is not disabled for any channels."
                    } else {
                        buildString {
                            if (options.inheritChannels()) append(inheritString)
                            it.mapNotNull(context.guild::getTextChannelById).forEach { append("• ").append(it.asMention).append('\n') }
                        }
                    }
                }
            }
        }.action().queue()
    }

    @Description(value = "Show the options of the category.")
    fun options_category(context: Context, category: Category) {
        val options = context.data.command.categoryOptions[category.ordinal]
        if (options == null) {
            context.send().embed("Command Management") {
                desc { "Category `${category.title}` is allowed to everybody with the appropriate permission." }
            }.action().queue()
            return
        }

        context.send().embed("Command Management") {
            field("Toggle") {
                if (!options.isEnabled) {
                    "Disabled."
                } else {
                    "Enabled."
                }
            }

            field("Disabled Users") {
                options.disabledUsers.let {
                    if (it.isEmpty()) {
                        "This category is not disabled for any users."
                    } else {
                        buildString {
                            it.mapNotNull(context.guild::getMemberById).forEach { append("• ").append(it.asMention).append('\n') }
                        }
                    }
                }
            }

            field("Disabled Roles") {
                options.disabledRoles.let {
                    if (it.isEmpty()) {
                        "This category is not disabled for any roles."
                    } else {
                        buildString {
                            it.mapNotNull(context.guild::getRoleById).forEach { append("• ").append(it.asMention).append('\n') }
                        }
                    }
                }
            }

            field("Disabled Channels") {
                options.disabledChannels.let {
                    if (it.isEmpty()) {
                        "This category is not disabled for any channels."
                    } else {
                        buildString {
                            it.mapNotNull(context.guild::getTextChannelById).forEach { append("• ").append(it.asMention).append('\n') }
                        }
                    }
                }
            }
        }.action().queue()
    }

    private fun allowAll(context: Context, options: CommandOptions, cmd: String, scope: ManageScope) {
        val set = scope.transform(options)

        if (set.isEmpty()) {
            context.send().error("`$cmd` is not disabled for any ${scope.name.toLowerCase()}.").queue()
            return
        }

        set.clear()
        context.data.save()

        context.send().embed("Command Management") {
            desc { "Enabled `$cmd` for every ${scope.name.toLowerCase()}." }
        }.action().queue()
    }

    private fun allowTo(context: Context, id: String, target: String,
                        options: CommandOptions, scope: ManageScope, cmd: String) {
        val set = scope.transform(options)

        if (!set.remove(id)) {
            context.send().error("`$cmd` is not disabled for $target.").queue()
            return
        }

        context.data.save()
        context.send().embed("Command Management") {
            desc { "`$cmd` is now allowed for $target." }
        }.action().queue()
    }

    private fun disallowAll(context: Context, options: CommandOptions, cmd: String, scope: ManageScope) {
        val set = scope.transform(options)

        val all = scope.all(context)

        if (!set.addAll(all)) {
            context.send().error("`$cmd` is already disabled for every ${scope.name.toLowerCase()}.").queue()
            return
        }
        context.data.save()

        context.send().embed("Command Management") {
            desc { "Disabled `$cmd` for every `${scope.name.toLowerCase()}`." }
        }.action().queue()
    }

    private fun disallowTo(context: Context, id: String, target: String,
                           options: CommandOptions, scope: ManageScope, cmd: String) {
        val set = scope.transform(options)

        if (!set.add(id)) {
            context.send().error("`$cmd` is already disabled for $target.").queue()
            return
        }

        context.data.save()
        context.send().embed("Command Management") {
            desc { "`$cmd` is now disabled for $target." }
        }.action().queue()
    }

    @Description("Clear all options for a command (will re-sync to category options).")
    fun clear_specific(context: Context, cmd: CommandExecutor) {
        clear(context, context.data.command.options, cmd.info.id, "category", cmd.info.aliases[0])
    }

    @Description("Clear all options for a category.")
    fun clear_category(context: Context, category: Category) {
        clear(context, context.data.command.categoryOptions, category.ordinal, "category", category.title)
    }

    private fun clear(context: Context, map: MutableMap<Int, CommandOptions>, key: Int, type: String, item: String) {
        val options = map[key]
        if (options == null) {
            context.send().embed("Command Management") {
                desc { "There's no options configured for this $type." }
            }.action().queue()
            return
        }

        map.remove(key)
        context.data.save()

        context.send().embed("Command Management") {
            desc {
                "Cleared the $type options for $item."
            }
        }.action().queue()
    }

    @Description("Clear all command options.")
    fun clear_all(context: Context) {
        if (context.data.command.options.isEmpty() && context.data.command.categoryOptions.isEmpty()) {
            context.send().error("This guild doesn't have any commands options.").queue()
            return
        }

        context.data.command.options.clear()
        context.data.command.categoryOptions.clear()
        context.data.save()

        context.send().embed("Command Management") {
            desc { "Cleared all command and category options." }
        }.action().queue()
    }

    private fun entity(context: Context, scope: ManageScope, entity: String): Pair<String, String>? {
        return when(scope) {
            ManageScope.USER -> {
                val member = Parsers.MEMBER.parse(context, entity)

                if (member == null) {
                    context.send().error("Not a valid user name or mention.").queue()
                    return null
                }

                member.user.id to member.user.asMention
            }
            ManageScope.ROLE -> {
                val role = Parsers.ROLE.parse(context, entity)

                if (role == null) {
                    context.send().error("Not a valid role name or mention").queue()
                    return null
                }

                role.id to role.asMention
            }
            ManageScope.CHANNEL -> {
                val channel = Parsers.TEXT_CHANNEL.parse(context, entity)

                if (channel == null) {
                    context.send().error("Not a valid text channel name or mention.").queue()
                    return null
                }

                channel.id to channel.asMention
            }
        }
    }

    private inline fun options(context: Context, cmd: CommandExecutor, block: (CommandOptions) -> Unit) {
        cmd.info.let {
            if (!it.toggleable) {
                context.send().error("`${it.aliases[0]}` can not be toggled.").queue()
                return
            }

            block(context.data.command.options.getOrPut(it.id, ::CommandOptions))
        }
    }

    private inline fun options(context: Context, category: Category, block: (CommandOptions) -> Unit) {
        block(context.data.command.categoryOptions.getOrPut(category.ordinal, ::CommandOptions))
    }
}