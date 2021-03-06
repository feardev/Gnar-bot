package xyz.gnarbot.gnar.commands.dispatcher.predicates

import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.entities.Member
import xyz.gnarbot.gnar.Bot
import xyz.gnarbot.gnar.commands.CommandExecutor
import xyz.gnarbot.gnar.utils.Context
import java.util.function.BiPredicate

class IgnoredPredicate : BiPredicate<CommandExecutor, Context> {
    override fun test(cmd: CommandExecutor, context: Context): Boolean {
        return !isIgnored(context, context.member)
    }

    // Ignore check:
    // Optional ignores: user, channel, role
    // Do not ignore if user have administrator role
    // Do not ignore if user is bot administrator
    private fun isIgnored(context: Context, member: Member): Boolean {
        return (context.data.ignored.users.contains(member.user.id)
                || context.data.ignored.channels.contains(context.textChannel.id)
                || context.data.ignored.roles.any { id -> member.roles.any { it.id == id } })
                && !member.hasPermission(Permission.ADMINISTRATOR)
                && member.user.idLong !in Bot.CONFIG.admins
    }
}