package com.shouyun.windweave.testing;

import com.mojang.logging.LogUtils;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.client.*;
import com.shouyun.windweave.device.*;
import com.shouyun.windweave.wind.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class StormDevicesClientTest {
    private static final StringBuilder REPORT = new StringBuilder();
    private static int ticks;
    private static double initialY;
    private static java.util.concurrent.CompletableFuture<Void> resourceReload;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.stormDevicesClientTest")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.isPaused()) return;
        ticks++;
        try {
            if (ticks == 1) {
                mc.options.hideGui = false; mc.options.bobView().set(false);
                var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid); var level = player.serverLevel();
                    player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                    var source = server.createCommandSourceStack();
                    for (String command : new String[]{
                            "fill -32 100 -32 32 100 32 minecraft:stone",
                            "fill -32 101 -32 32 107 32 minecraft:air", "fill -32 108 -32 32 114 32 minecraft:air",
                            "fill -32 115 -32 32 121 32 minecraft:air", "fill -32 122 -32 32 128 32 minecraft:air",
                            "fillbiome -24 96 -24 -1 115 24 minecraft:plains", "fillbiome 0 96 -24 24 115 24 minecraft:plains",
                            "time set noon", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather clear",
                            "windweave set east 12", "setblock -3 101 0 windweave:anemometer", "setblock 0 101 0 windweave:wind_vane",
                            "setblock 3 101 0 windweave:wind_controller[facing=south]", "tp " + player.getScoreboardName() + " 0 102.8 6 180 12"
                    }) server.getCommands().performPrefixedCommand(source, command);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(WindDevices.METER.get()));
                    player.getInventory().setItem(1, new ItemStack(WindDevices.ANEMOMETER_ITEM.get()));
                    player.getInventory().setItem(2, new ItemStack(WindDevices.VANE_ITEM.get()));
                    player.getInventory().setItem(3, new ItemStack(WindDevices.CONTROLLER_ITEM.get()));
                    if (net.neoforged.fml.ModList.get().isLoaded("iris")) {
                        for (String command : new String[]{
                                "fill -6 100 -6 -1 100 -2 minecraft:grass_block",
                                "fill -6 101 -5 -1 101 -2 minecraft:short_grass",
                                "fill -7 101 -6 -7 104 -2 minecraft:oak_leaves[persistent=true]",
                                "fill 1 100 -6 6 100 -2 minecraft:water",
                                "setblock -5 101 0 minecraft:fire"})
                            server.getCommands().performPrefixedCommand(source, command);
                    }
                });
            }
            if (ticks == 80) {
                if (net.neoforged.fml.ModList.get().isLoaded("iris")) {
                    check(IrisWindBridge.registrations() > 0 && IrisWindBridge.reads() > 0,
                            "Iris compiled adapted programs and updates active Windweave uniforms: registrations="
                                    + IrisWindBridge.registrations() + " reads=" + IrisWindBridge.reads());
                    check(WindTerrainShaders.visualWind().x > 10, "Iris frame clock follows the east wind");
                }
                checkInstrumentModels(mc);
                var speed = (WindStationBlockEntity) mc.level.getBlockEntity(new BlockPos(-3, 101, 0));
                var vane = (WindStationBlockEntity) mc.level.getBlockEntity(new BlockPos(0, 101, 0));
                check(speed != null && speed.angle > 100, "Real anemometer rotor advances with local wind");
                check(vane != null && Math.abs(net.minecraft.util.Mth.wrapDegrees(vane.bearing - 90)) < 2, "Real vane points downwind east");
                check(mc.player.getMainHandItem().is(WindDevices.METER.get()), "Handheld wind meter inventory synchronization");
                mc.gui.getChat().clearMessages(false);
                shot(mc, "wind-instruments.png");
                server(mc, player -> {
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "tp @s 2 102 6 180 8");
                    var control = (WindControllerBlockEntity) player.level().getBlockEntity(new BlockPos(3, 101, 0));
                    player.openMenu(control, buffer -> buffer.writeBlockPos(control.getBlockPos()));
                });
            }
            if (ticks == 105) {
                check(mc.screen instanceof WindControllerScreen && mc.player.containerMenu instanceof WindControllerMenu, "Controller opens real client screen and synchronized menu");
                int id = mc.player.containerMenu.containerId;
                for (int action : new int[]{3, 9, 9, 11, 12}) mc.gameMode.handleInventoryButtonClick(id, action);
            }
            if (ticks == 130) {
                var menu = (WindControllerMenu) mc.player.containerMenu;
                check(menu.value(0) == 270 && menu.value(1) == 22 && menu.value(2) == 32 && menu.value(3) == 1,
                        "Direction, strength, radius and enable buttons synchronize through the real server menu");
                check(WindField.sample(mc.level, mc.player.getEyePosition()).x < -21.9,
                        "Client receives active controller source and measures its local west wind");
                if (net.neoforged.fml.ModList.get().isLoaded("iris"))
                    check(WindTerrainShaders.visualWind().x < -20, "Iris uniforms reverse with the local controller's west wind");
                shot(mc, "wind-controller-screen.png");
            }
            if (ticks == 140) {
                mc.screen.onClose();
                server(mc, player -> ((WindControllerBlockEntity) player.level().getBlockEntity(new BlockPos(3, 101, 0))).configure(180, 0, 16, true));
            }
            if (ticks == 175) {
                check(WindField.sample(mc.level, mc.player.getEyePosition()).length() < .1, "Controller calm pocket overrides regional wind on the client");
                if (net.neoforged.fml.ModList.get().isLoaded("iris"))
                    check(WindTerrainShaders.visualWind().length() < 1, "Iris uniforms settle inside the calm pocket");
                server(mc, player -> {
                    var level = player.serverLevel();
                    ((WindControllerBlockEntity) level.getBlockEntity(new BlockPos(3, 101, 0))).configure(90, 12, 24, false);
                    WindSavedData.get(level).setFixed(90, 60); WindEvents.sync(level);
                    player.setGameMode(GameType.SURVIVAL); player.setInvulnerable(true);
                    player.getAbilities().flying = false; player.onUpdateAbilities();
                    player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4), "tp @s 0 101 8 180 0");
                });
                initialY = 101;
            }
            if (ticks == 300) {
                check(!mc.player.getAbilities().flying && mc.player.getY() > initialY + 2 && mc.player.getX() > 2,
                        "Real survival player is pushed and lifted on the controlling client: position=" + mc.player.position());
                server(mc, player -> {
                    player.setGameMode(GameType.CREATIVE); player.getAbilities().flying = true; player.onUpdateAbilities();
                    var commands = player.getServer().getCommands(); var source = player.createCommandSourceStack().withPermission(4);
                    commands.performPrefixedCommand(source, "tp @s 0 103 8 180 12");
                    player.setDeltaMovement(Vec3.ZERO); player.hurtMarked = true;
                    commands.performPrefixedCommand(source, "weather thunder");
                });
            }
            if (ticks == 430) {
                check(mc.level.getRainLevel(1) > .95 && mc.level.getThunderLevel(1) > .95,
                        "Actual thunderstorm synchronized: rain=" + mc.level.getRainLevel(1) + " thunder=" + mc.level.getThunderLevel(1));
                check(WindWeather.count(false) > 900 && WindWeather.count(false) <= WindWeather.MAX_PARTICLES,
                        "Dense wind-driven rain remains bounded: " + WindWeather.count(false));
                check(WindWeather.sprayCount() > 5 && WindWeather.sprayCount() <= WindWeather.MAX_SPRAY,
                        "Wind-driven ground spray is visible and bounded: " + WindWeather.sprayCount());
                check(WindWeather.averageVelocity(false).x > 2, "Extreme wind produces strongly slanted rain");
                shot(mc, "extreme-thunderstorm.png");
                resourceReload = mc.reloadResourcePacks();
            }
            if (ticks >= 445 && resourceReload != null && resourceReload.isDone()) {
                resourceReload.join();
                checkInstrumentModels(mc);
                check(true, "Instrument models and animated heads survive a real resource reload");
                if (net.neoforged.fml.ModList.get().isLoaded("iris")) {
                    check(IrisWindBridge.reads() > 100, "Iris wind uniforms continue updating after resource reload");
                    shot(mc, "iris-windweave-iteration.png");
                }
                server(mc, player -> { WindSavedData.get(player.serverLevel()).setAutomatic(); WindEvents.sync(player.serverLevel()); });
                Files.writeString(Path.of("storm-devices-client-result.txt"), REPORT + "PASS\n");
                LogUtils.getLogger().info("STORM DEVICES CLIENT TEST PASS\n{}", REPORT); mc.stop();
            }
            if (ticks > 550) throw new IllegalStateException("Storm device client test timeout");
        } catch (Throwable error) {
            try { Files.writeString(Path.of("storm-devices-client-result.txt"), REPORT + "FAIL tick=" + ticks + ": " + error); }
            catch (Exception ignored) {}
            LogUtils.getLogger().error("STORM DEVICES CLIENT TEST FAILED at tick " + ticks, error); mc.stop();
        }
    }
    private static void server(Minecraft mc, java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> work) {
        var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID(); server.execute(() -> work.accept(server.getPlayerList().getPlayer(uuid)));
    }
    private static void shot(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {}); }
    private static void checkInstrumentModels(Minecraft mc) {
        for (var item : new net.minecraft.world.item.Item[]{WindDevices.METER.get(), WindDevices.ANEMOMETER_ITEM.get(),
                WindDevices.VANE_ITEM.get(), WindDevices.CONTROLLER_ITEM.get()}) {
            var model = mc.getItemRenderer().getModel(new ItemStack(item), mc.level, mc.player, 0);
            check(model.isGui3d(), "Instrument has a 3D item model: " + item);
            checkModel(mc, model, item.toString());
        }
        checkModel(mc, mc.getModelManager().getModel(WindStationRenderer.ROTOR), "animated rotor");
        checkModel(mc, mc.getModelManager().getModel(WindStationRenderer.VANE), "animated vane head");
    }
    private static void checkModel(Minecraft mc, net.minecraft.client.resources.model.BakedModel model, String name) {
        check(model != mc.getModelManager().getMissingModel(), "Baked instrument model exists: " + name);
        var random = net.minecraft.util.RandomSource.create(42);
        var quads = new java.util.ArrayList<>(model.getQuads(null, null, random));
        for (var direction : net.minecraft.core.Direction.values()) quads.addAll(model.getQuads(null, direction, random));
        check(quads.size() > 12, "Instrument geometry is present: " + name);
        for (var quad : quads) {
            var texture = quad.getSprite().contents().name();
            if (!texture.getNamespace().equals("windweave") || !texture.getPath().startsWith("block/instruments/"))
                throw new IllegalStateException("Missing or unexpected instrument texture: " + name + " / " + texture);
        }
        check(true, "All instrument faces resolve original textures: " + name);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); REPORT.append("PASS: ").append(message).append('\n'); }
}
