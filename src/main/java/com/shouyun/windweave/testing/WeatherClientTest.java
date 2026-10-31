package com.shouyun.windweave.testing;

import com.mojang.logging.LogUtils;
import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.client.WindWeather;
import com.shouyun.windweave.client.WindWeatherParticle;
import com.shouyun.windweave.client.StormFog;
import com.shouyun.windweave.particle.WeatherParticles;
import com.shouyun.windweave.wind.ClientWindState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;

/** Actual client provider, network, collision, biome emitter and rendering regression. Not shipped. */
@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class WeatherClientTest {
    private static final StringBuilder REPORT = new StringBuilder();
    private static int ticks;
    private static WindWeatherParticle rain, snow, roofRain, roofSnow, wallRain, wallSnow, fluid;
    private static Vec3 rainStart, snowStart;
    private static double slowRain, slowSnow;
    private static long quads;
    private static boolean reloaded;
    private static int stormStart;
    private static long fogFrames;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.weatherClientTest")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.isPaused()) return;
        ticks++;
        // Keep the test view stable even when the window gains focus.
        mc.player.setYRot(180); mc.player.setXRot(0);
        try {
            if (ticks == 1) {
                mc.options.hideGui = true;
                mc.options.bobView().set(false);
                mc.options.particles().set(ParticleStatus.ALL);
                ClientConfig.RAIN.set(true); ClientConfig.SNOW.set(true); ClientConfig.WEATHER_DENSITY.set(1.0);
                var server = mc.getSingleplayerServer(); var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var source = server.createCommandSourceStack();
                    String name = server.getPlayerList().getPlayer(uuid).getScoreboardName();
                    for (String command : new String[]{
                            "fill -24 100 -24 24 100 24 minecraft:white_concrete",
                            "fill -24 101 -24 24 113 24 minecraft:air",
                            "fill -24 114 -24 24 126 24 minecraft:air",
                            "fill -24 127 -24 24 139 24 minecraft:air",
                            "fill -24 140 -24 24 145 24 minecraft:air",
                            "fillbiome -24 96 -24 -1 119 24 minecraft:plains",
                            "fillbiome -24 120 -24 -1 144 24 minecraft:plains",
                            "fillbiome 0 96 -24 24 119 24 minecraft:snowy_plains",
                            "fillbiome 0 120 -24 24 144 24 minecraft:snowy_plains",
                            "fill 8 108 -10 14 108 -4 minecraft:stone",
                            "fill -8 101 -10 -8 140 -4 minecraft:stone",
                            "setblock 6 105 0 minecraft:water",
                            "time set noon", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                            "weather rain", "windweave set east 12", "gamemode spectator " + name,
                            "tp " + name + " 0 104 12 180 0"
                    }) server.getCommands().performPrefixedCommand(source, command);
                    server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayer(uuid).createCommandSourceStack(), "windweave flow off");
                });
            }
            if (ticks == 100) {
                check(WindWeather.count(false) > 15 && WindWeather.count(true) > 15, "Natural rainy/snowy biomes both emit particles: " + WindWeather.count(false) + "/" + WindWeather.count(true));
                check(mc.level.getBiome(new BlockPos(-12, 112, 0)).value().getPrecipitationAt(new BlockPos(-12, 112, 0)) == Biome.Precipitation.RAIN,
                        "Warm biome uses rain");
                check(mc.level.getBiome(new BlockPos(12, 112, 0)).value().getPrecipitationAt(new BlockPos(12, 112, 0)) == Biome.Precipitation.SNOW,
                        "Cold biome uses snow");
                check(Math.abs(ClientWindState.ambient(mc.level).x - 12) < .001, "Server east wind synchronized");
                probes(mc); quads = WindWeather.renderedQuads();
                check(WindWeather.snowVariants() == 6, "All six snow silhouettes are present in real particles");
                check(rain.halfWidth() >= .019F, "Rain geometry is at least 58 percent thicker than 0.3.0");
            }
            if (ticks == 110) {
                check(rain.getPos().x - rainStart.x > 5 && snow.getPos().x - snowStart.x > 5, "Both particles travel east at 12 m/s");
                check(rain.getPos().y < rainStart.y - 5 && snow.getPos().y < snowStart.y, "Both retain downward fall; snow falls slower");
                check(WindWeather.renderedQuads() > quads + 100, "Crossed 3D particle geometry reached real world rendering");
                screenshot(mc, "weather-east-12.png"); command(mc, "windweave set west 12");
            }
            if (ticks == 128) {
                check(rain.isAlive() && snow.isAlive() && rain.velocity().x < -.5 && snow.velocity().x < -.5,
                        "Existing rain and snow reverse with synchronized west wind: " + rain.velocity() + "/" + snow.velocity());
                screenshot(mc, "weather-west-12.png"); command(mc, "windweave calm");
            }
            if (ticks == 155) probes(mc);
            if (ticks == 165) {
                check(Math.abs(rain.getPos().x - rainStart.x) < .01 && Math.abs(snow.getPos().x - snowStart.x) < .25, "Calm gives vertical rain and only gentle snow flutter");
                command(mc, "windweave set east 4");
            }
            if (ticks == 185) probes(mc);
            if (ticks == 195) {
                slowRain = rain.getPos().x - rainStart.x; slowSnow = snow.getPos().x - snowStart.x;
                command(mc, "windweave set east 16");
            }
            if (ticks == 215) probes(mc);
            if (ticks == 225) {
                double fastRain = rain.getPos().x - rainStart.x, fastSnow = snow.getPos().x - snowStart.x;
                check(fastRain > slowRain * 3.8 && fastSnow > slowSnow * 3.6,
                        "4 -> 16 m/s increases actual displacement fourfold: rain " + slowRain + " -> " + fastRain + ", snow " + slowSnow + " -> " + fastSnow);
                command(mc, "windweave calm");
            }
            if (ticks == 245) {
                roofRain = particle(mc, false, 11, 109.2, -7); roofSnow = particle(mc, true, 11, 109.2, -7);
                fluid = particle(mc, false, 6.5, 105.2, .5);
                command(mc, "windweave set west 12");
            }
            if (ticks == 265) {
                check(!roofRain.isAlive() && !roofSnow.isAlive(), "Swept collisions stop both kinds on a roof");
                check(!fluid.isAlive(), "Rain terminates in water");
                wallRain = particle(mc, false, -7.3, 125, -7); wallSnow = particle(mc, true, -7.3, 125, -7);
            }
            if (ticks == 295) {
                check(wallRain.getPos().x > -8 && wallSnow.getPos().x > -8, "Westbound rain and snow never cross the solid wall");
                check(wallSnow.velocity().x > -.02, "Wall shelter reduces snow drift using the existing local wind query");
                ClientConfig.RAIN.set(false);
            }
            if (ticks == 305) {
                check(WindWeather.count(false) == 0 && WindWeather.count(true) > 0, "Rain switch restores rain sheets independently of snow");
                ClientConfig.RAIN.set(true); ClientConfig.SNOW.set(false);
            }
            if (ticks == 315) {
                check(WindWeather.count(true) == 0 && WindWeather.count(false) > 0, "Snow switch works independently of rain");
                ClientConfig.SNOW.set(true); mc.options.particles().set(ParticleStatus.MINIMAL);
            }
            if (ticks == 325) {
                check(WindWeather.count(false) + WindWeather.count(true) <= 200, "Minimal particle setting reduces weather budget to 200");
                mc.options.particles().set(ParticleStatus.ALL);
                mc.reloadResourcePacks().thenRun(() -> reloaded = true);
            }
            if (ticks >= 345 && reloaded && stormStart == 0) {
                check(WindWeather.count(false) + WindWeather.count(true) > 20, "Weather resumes after real resource reload without stale budget");
                screenshot(mc, "weather-after-reload.png");
                stormStart = ticks; fogFrames = StormFog.terrainFrames();
                ClientConfig.STORM_FOG.set(true);
                ClientConfig.WEATHER_DENSITY.set(.25);
                mc.options.particles().set(ParticleStatus.MINIMAL);
                command(mc, "weather thunder"); command(mc, "windweave auto");
            }
            if (stormStart > 0 && ticks == stormStart + 140) {
                check(mc.level.getThunderLevel(1) > .95F, "Real thunderstorm weather synchronized to client");
                check(WindWeather.budget(mc.level, .2) == WindWeather.MAX_PARTICLES, "Thunder reaches bounded maximum weather budget");
                check(WindWeather.count(false) + WindWeather.count(true) > 1200, "Real storm emitter fills dense weather pool: " + (WindWeather.count(false) + WindWeather.count(true)));
                check(StormFog.terrainFrames() > fogFrames && StormFog.lastEnd() <= 53, "Real thunderstorm terrain fog reaches 52-block visibility: " + StormFog.lastEnd());
                check(ClientWindState.ambient(mc.level).length() > 8, "Natural storm wind synchronized to the shared client field: " + ClientWindState.ambient(mc.level));
                screenshot(mc, "weather-thunder-mixed.png");
                ClientConfig.RAIN.set(false);
            }
            if (stormStart > 0 && ticks == stormStart + 200) {
                check(WindWeather.count(false) == 0 && WindWeather.count(true) > 1200, "Storm snow alone uses maximum pool");
                screenshot(mc, "weather-thunder-snow.png");
                ClientConfig.RAIN.set(true); ClientConfig.SNOW.set(false);
            }
            if (stormStart > 0 && ticks == stormStart + 260) {
                check(WindWeather.count(true) == 0 && WindWeather.count(false) > 1000, "Storm rain alone uses maximum emission rate: " + WindWeather.count(false));
                screenshot(mc, "weather-thunder-rain.png");
                ClientConfig.SNOW.set(true); ClientConfig.WEATHER_DENSITY.set(1.0);
                mc.options.particles().set(ParticleStatus.ALL);
                command(mc, "weather clear");
            }
            if (stormStart > 0 && ticks == stormStart + 400) {
                check(mc.level.getRainLevel(1) < .001 && mc.level.getThunderLevel(1) < .001
                        && WindWeather.count(false) + WindWeather.count(true) == 0, "Clearing storm removes particles and fog intensity");
                check(WindWeather.stormStrength(mc.level, 1) == 0, "Thunder haze fades completely in clear weather");
                mc.level.setRainLevel(0);
                command(mc, "weather clear");
                ClientConfig.RAIN.set(false); ClientConfig.SNOW.set(false);
                check(!WindWeather.enabled(Biome.Precipitation.RAIN) && !WindWeather.enabled(Biome.Precipitation.SNOW), "Both disabled restores vanilla precipitation kinds");
                ClientConfig.RAIN.set(true); ClientConfig.SNOW.set(true);
                WindWeather.clear();
                check(WindWeather.count(false) + WindWeather.count(true) == 0, "Level/logout cleanup releases all weather particles");
                Files.writeString(Path.of("weather-test-result.txt"), REPORT + "PASS\n");
                LogUtils.getLogger().info("WEATHER CLIENT TEST PASS\n{}", REPORT); mc.stop();
            }
            if (ticks > 1100) throw new IllegalStateException("Weather test timed out");
        } catch (Throwable error) {
            try { Files.writeString(Path.of("weather-test-result.txt"), REPORT + "FAIL tick=" + ticks + ": " + error + "\n"); }
            catch (Exception ignored) {}
            LogUtils.getLogger().error("WEATHER TEST FAILED at " + ticks, error); mc.stop();
        }
    }
    private static void probes(Minecraft mc) {
        rain = particle(mc, false, -2, 132, 0); snow = particle(mc, true, 2, 132, 0);
        rainStart = rain.getPos(); snowStart = snow.getPos();
    }
    private static WindWeatherParticle particle(Minecraft mc, boolean snow, double x, double y, double z) {
        var result = mc.particleEngine.createParticle(snow ? WeatherParticles.SNOW.get() : WeatherParticles.RAIN.get(), x, y, z, 0, 0, 0);
        check(result instanceof WindWeatherParticle, "Custom " + (snow ? "snow" : "rain") + " provider loaded");
        return (WindWeatherParticle) result;
    }
    private static void screenshot(Minecraft mc, String name) { Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {}); }
    private static void command(Minecraft mc, String command) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        REPORT.append("PASS: ").append(message).append('\n');
    }
}
