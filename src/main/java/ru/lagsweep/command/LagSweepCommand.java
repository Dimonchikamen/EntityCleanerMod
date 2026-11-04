package ru.lagsweep.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import ru.lagsweep.SweepController;
import ru.lagsweep.config.SweepConfig;

/**
 * /lagsweep sweep                 — немедленная очистка (с предупреждением 5..1 сек)
 * /lagsweep interval <минут>      — изменить период очистки (и сохранить в конфиг)
 * /lagsweep status                — показать текущий статус и оставшееся время
 */
public final class LagSweepCommand {

    private LagSweepCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lagsweep")
                .requires(source -> source.hasPermission(2)) // OP-уровень 2
                .then(Commands.literal("sweep")
                        .executes(LagSweepCommand::sweepNow))
                .then(Commands.literal("interval")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                .executes(LagSweepCommand::setInterval)))
                .then(Commands.literal("status")
                        .executes(LagSweepCommand::status)));
    }

    private static int sweepNow(CommandContext<CommandSourceStack> ctx) {
        SweepController.requestImmediateSweep();
        ctx.getSource().sendSuccess(
                () -> Component.literal("🧹 Очистка будет выполнена со следующего тика.")
                        .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setInterval(CommandContext<CommandSourceStack> ctx) {
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        SweepConfig.GENERAL.intervalMinutes.set(minutes);
        SweepConfig.SPEC.save();
        SweepController.restartCycle();
        ctx.getSource().sendSuccess(
                () -> Component.literal("Интервал очистки: " + minutes + " мин (сохранено в lagsweep.toml)")
                        .withStyle(ChatFormatting.AQUA), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        long remainingSec = SweepController.ticksUntilSweep() / 20L;
        int[] wl = SweepController.whitelistStats();
        ctx.getSource().sendSuccess(
                () -> Component.literal(String.format(
                        "Период: %d мин | До очистки: %d мин %d сек | Whitelist: %d предметов, %d тегов, %d модов",
                        SweepConfig.GENERAL.intervalMinutes.get(),
                        remainingSec / 60, remainingSec % 60,
                        wl[0], wl[1], wl[2]))
                        .withStyle(ChatFormatting.YELLOW), false);
        return 1;
    }
}
