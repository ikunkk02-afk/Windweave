package com.shouyun.windweave.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.shouyun.windweave.network.FlowPayload;
import com.shouyun.windweave.wind.WindEvents;
import com.shouyun.windweave.wind.WindField;
import com.shouyun.windweave.wind.WindMath;
import com.shouyun.windweave.wind.WindSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class WindCommands {
    private WindCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("windweave")
                .executes(ctx -> status(ctx.getSource()))
                .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())));
        var set = Commands.literal("set").requires(source -> source.hasPermission(2))
                .then(Commands.argument("bearing", DoubleArgumentType.doubleArg(0, 360))
                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0, 80))
                                .executes(ctx -> fixed(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "bearing"),
                                        DoubleArgumentType.getDouble(ctx, "speed")))));
        String[] directions = {"north", "east", "south", "west"};
        for (int i = 0; i < directions.length; i++) {
            final double bearing = i * 90;
            set.then(Commands.literal(directions[i]).then(Commands.argument("speed", DoubleArgumentType.doubleArg(0, 80))
                    .executes(ctx -> fixed(ctx.getSource(), bearing, DoubleArgumentType.getDouble(ctx, "speed")))));
        }
        root.then(set);
        root.then(Commands.literal("flow").executes(ctx -> flow(ctx.getSource(), true))
                .then(Commands.literal("on").executes(ctx -> flow(ctx.getSource(), true)))
                .then(Commands.literal("off").executes(ctx -> flow(ctx.getSource(), false))));
        root.then(Commands.literal("calm").requires(source -> source.hasPermission(2))
                .executes(ctx -> fixed(ctx.getSource(), 90, 0)));
        root.then(Commands.literal("auto").requires(source -> source.hasPermission(2)).executes(ctx -> {
            WindSavedData.get(ctx.getSource().getLevel()).setAutomatic();
            WindEvents.sync(ctx.getSource().getLevel());
            ctx.getSource().sendSuccess(() -> Component.translatable("command.windweave.auto"), true);
            return 1;
        }));
        root.then(Commands.literal("indicator").requires(source -> source.hasPermission(2))
                .executes(ctx -> indicator(ctx.getSource(), ctx.getSource().getPosition()))
                .then(Commands.argument("pos", Vec3Argument.vec3()).executes(ctx ->
                        indicator(ctx.getSource(), Vec3Argument.getVec3(ctx, "pos")))));
        dispatcher.register(root);
    }

    private static int fixed(CommandSourceStack source, double bearing, double speed) {
        WindSavedData.get(source.getLevel()).setFixed(bearing, speed);
        WindEvents.sync(source.getLevel());
        source.sendSuccess(() -> Component.translatable("command.windweave.fixed", format(bearing % 360), format(speed)), true);
        return 1;
    }

    private static int status(CommandSourceStack source) {
        Vec3 local = WindField.sample(source.getLevel(), source.getPosition().add(0, 1.4, 0));
        Vec3 ambient = WindSavedData.get(source.getLevel()).ambient(source.getLevel());
        source.sendSuccess(() -> Component.translatable("command.windweave.status",
                Component.translatable(WindSavedData.get(source.getLevel()).isAutomatic()
                        ? "windweave.mode.auto" : "windweave.mode.fixed"),
                format(ambient.length()), format(local.length()), format(local.length() < .05 ? 0 : WindMath.bearing(local))), false);
        return 1;
    }

    private static int indicator(CommandSourceStack source, Vec3 position) {
        return flow(source, true);
    }

    private static int flow(CommandSourceStack source, boolean enabled) {
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.windweave.flow.player_only"));
            return 0;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new FlowPayload(enabled));
        source.sendSuccess(() -> Component.translatable(enabled ? "command.windweave.flow.on" : "command.windweave.flow.off"), false);
        return 1;
    }

    private static String format(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
}
