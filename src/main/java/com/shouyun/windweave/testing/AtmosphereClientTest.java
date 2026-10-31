package com.shouyun.windweave.testing;

import com.mojang.logging.LogUtils;
import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;

/** Tests the actual client sound engine and fog callbacks under real thunder weather. */
@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class AtmosphereClientTest {
    private static final StringBuilder REPORT = new StringBuilder();
    private static int ticks, reloadTick;
    private static boolean reloaded;
    private static float breeze, medium;
    private static Vec3 daySnow;
    private static long fogFrames;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.atmosphereClientTest")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.isPaused()) return;
        ticks++; mc.player.setYRot(180); mc.player.setXRot(8);
        try {
            if (ticks == 1) {
                mc.options.hideGui = true; mc.options.bobView().set(false);
                ClientConfig.STORM_FOG.set(true); ClientConfig.WIND_SOUND.set(true); ClientConfig.WIND_VOLUME.set(.65);
                ClientConfig.SANDSTORM.set(true); ClientConfig.SAND_DRIFT.set(true);
                for (String cmd : new String[]{
                        "fill -24 99 -24 24 99 24 minecraft:sandstone", "fill -24 100 -24 24 100 24 minecraft:sand",
                        "fill -24 101 -24 24 113 24 minecraft:air", "fill -24 114 -24 24 126 24 minecraft:air",
                        "fill -24 127 -24 24 139 24 minecraft:air", "fill -24 140 -24 24 145 24 minecraft:air",
                        "fill -20 101 -20 -12 103 4 minecraft:sandstone", "fill -20 104 -20 -16 106 4 minecraft:sandstone",
                        "time set noon", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "weather thunder", "windweave calm"
                }) command(mc, cmd);
                biome(mc, "desert");
                var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamemode spectator " + player.getScoreboardName());
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "tp " + player.getScoreboardName() + " 0 102 12 180 8");
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "windweave flow off");
                });
                float previous = -1;
                for (int speed = 0; speed <= 40; speed++) {
                    var mix = WindAudio.mix(speed, 1, .65F);
                    float total = mix.wind()+mix.sand();
                    check(total >= previous && total <= .248, "Audio curve monotonic and capped at " + speed + " m/s: " + total);
                    if (speed <= 4) check(total == 0, "Breeze threshold is silent at " + speed + " m/s");
                    var dry = WindAudio.mix(speed, 0, .65F);
                    check(Math.abs(total - dry.wind()) < 1E-6, "Storm shares rather than adds loudness at " + speed + " m/s");
                    previous = total;
                }
            }
            if (ticks == 160) {
                check(mc.level.getThunderLevel(1) > .95, "Real thunderstorm reached full strength");
                Vec3 c = StormFog.lastColor();
                check(c.x > .70 && c.y > .57 && c.z < .45 && c.x > c.z * 1.6, "Calm desert thunder uses bright yellow fog independently of sand particles: " + c);
                check(StormFog.lastEnd() <= 31 && WindSand.intensity() < .001, "Desert thunder stays low-visibility even without local wind");
                check(!WindAudio.playing(), "No ambient loop playing in calm weather");
                screenshot(mc, "atmosphere-desert-thunder.png"); command(mc, "windweave set east 8");
            }
            if (ticks == 240) {
                breeze = WindAudio.windGain()+WindAudio.sandGain();
                check(WindAudio.playing() && breeze > .005 && breeze < .02, "8 m/s breeze is quiet: " + breeze);
                command(mc, "windweave set east 16");
            }
            if (ticks == 320) {
                medium = WindAudio.windGain()+WindAudio.sandGain();
                check(medium > breeze * 3 && medium < .085, "16 m/s increases volume gradually within reduced budget: " + medium);
                command(mc, "windweave set east 28");
            }
            if (ticks == 400) {
                float strong = WindAudio.windGain()+WindAudio.sandGain();
                check(strong > medium * 2 && strong <= .248 && WindAudio.sandGain() > .03, "28 m/s storm has two layers within one capped budget: " + strong);
                mc.reloadResourcePacks().thenRun(() -> reloaded = true);
            }
            if (reloaded && reloadTick == 0) reloadTick = ticks;
            if (reloadTick > 0 && ticks == reloadTick + 80) {
                check(WindAudio.playing() && WindAudio.windGain()+WindAudio.sandGain() < .248, "Quiet wind mix recovers after resource reload");
                command(mc, "windweave set east 2"); biome(mc, "plains");
            }
            if (reloadTick > 0 && ticks == reloadTick + 180) {
                check(!WindAudio.playing() && WindAudio.windGain()+WindAudio.sandGain() < .002, "Dropping to 2 m/s fades and stops loops");
                Vec3 c = StormFog.lastColor();
                check(c.x > .55 && c.y > .60 && c.z > c.x, "Plains thunder uses readable grey-blue fog: " + c);
                check(StormFog.lastEnd() >= 51 && StormFog.lastEnd() <= 53, "Non-desert storm retains 52-block fog range");
                screenshot(mc, "atmosphere-plains-thunder.png"); biome(mc, "snowy_plains");
            }
            if (reloadTick > 0 && ticks == reloadTick + 260) {
                daySnow = StormFog.lastColor();
                check(daySnow.x > .64 && daySnow.z > .74, "Snow storm uses pale blue-white fog: " + daySnow);
                screenshot(mc, "atmosphere-snow-thunder.png"); command(mc, "time set midnight");
            }
            if (reloadTick > 0 && ticks == reloadTick + 300) {
                Vec3 night = StormFog.lastColor();
                check(night.x > .20 && night.length() < daySnow.length() * .55, "Night stays dim without collapsing to black: " + night);
                screenshot(mc, "atmosphere-snow-night.png");
                command(mc, "time set noon"); ClientConfig.STORM_FOG.set(false); fogFrames = StormFog.terrainFrames();
            }
            if (reloadTick > 0 && ticks == reloadTick + 320) {
                check(StormFog.terrainFrames() == fogFrames, "Disabling storm fog restores vanilla fog callbacks");
                ClientConfig.STORM_FOG.set(true); command(mc, "weather clear");
            }
            if (reloadTick > 0 && ticks == reloadTick + 450) fogFrames = StormFog.terrainFrames();
            if (reloadTick > 0 && ticks == reloadTick + 470) {
                check(mc.level.getRainLevel(1) < .001 && StormFog.terrainFrames() == fogFrames, "Clearing weather removes the atmospheric override");
                Files.writeString(Path.of("atmosphere-client-result.txt"), REPORT + "PASS\n");
                LogUtils.getLogger().info("ATMOSPHERE CLIENT TEST PASS\n{}", REPORT); mc.stop();
            }
            if (ticks > 1100) throw new IllegalStateException("Atmosphere test timed out");
        } catch (Throwable error) {
            try { Files.writeString(Path.of("atmosphere-client-result.txt"), REPORT+"FAIL tick="+ticks+": "+error+"\n"); } catch (Exception ignored) {}
            LogUtils.getLogger().error("ATMOSPHERE TEST FAILED at " + ticks, error); mc.stop();
        }
    }
    private static void biome(Minecraft mc, String name) {
        for (int y=96; y<144; y+=12) command(mc, "fillbiome -24 " + y + " -24 24 " + (y+11) + " 24 minecraft:" + name);
    }
    private static void command(Minecraft mc, String cmd) {
        var server=mc.getSingleplayerServer(); server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),cmd));
    }
    private static void screenshot(Minecraft mc,String name) { Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message -> {}); }
    private static void check(boolean value,String message) { if (!value) throw new IllegalStateException(message); REPORT.append("PASS: ").append(message).append('\n'); }
}
