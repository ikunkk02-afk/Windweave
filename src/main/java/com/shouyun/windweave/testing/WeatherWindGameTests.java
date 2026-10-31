package com.shouyun.windweave.testing;

import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.wind.WeatherWind;
import com.shouyun.windweave.wind.WindSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@EventBusSubscriber(modid = Windweave.MODID)
@PrefixGameTestTemplate(false)
public final class WeatherWindGameTests {
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("windweave.gametest")) event.register(WeatherWindGameTests.class);
    }
    @GameTest(batch = "weatherWind", template = "empty", templateNamespace = Windweave.MODID)
    public static void weatherChangesDirectionAndSpeed(GameTestHelper helper) {
        double maxStep = 0;
        for (int phase = 0; phase < 8; phase++) for (int t = 0; t < 3600; t += 7) {
            var clear = WeatherWind.sample(t, phase, 6, 0, 0);
            var rain = WeatherWind.sample(t, phase, 6, 1, 0);
            var thunder = WeatherWind.sample(t, phase, 6, 1, 1);
            helper.assertTrue(rain.length() > clear.length() * 1.64, "Rain strengthens natural wind");
            helper.assertTrue(thunder.length() > rain.length(), "Thunder strengthens wind further at every sampled gust phase");
            helper.assertTrue(clear.normalize().distanceTo(rain.normalize()) > .3 && rain.normalize().distanceTo(thunder.normalize()) > .3,
                    "Weather changes wind bearing as well as speed");
            helper.assertTrue(WeatherWind.sample(t, phase, 40, 1, 1).length() <= 80.00001, "Storm retains 80 m/s regional speed cap");
        }
        long samples = 0, extremeSamples = 0, capSamples = 0, selectedWindows = 0, windows = 0;
        double maximum = 0, maxNaturalStep = 0;
        for (int phase = 0; phase < 32; phase++) {
            for (int slot = 0; slot < 720; slot++) {
                boolean selected = false;
                for (int second = 0; second < 120; second++) {
                    double time = slot * 120.0 + second;
                    var wind = WeatherWind.sample(time, phase * .193, 6, 1, 1);
                    double speed = wind.length(); samples++;
                    if (speed > 50) { extremeSamples++; selected = true; }
                    if (speed > 79.99) capSamples++;
                    maximum = Math.max(maximum, speed);
                    maxNaturalStep = Math.max(maxNaturalStep, wind.distanceTo(WeatherWind.sample(time + .05, phase * .193, 6, 1, 1)));
                    helper.assertTrue(WeatherWind.sample(time, phase * .193, 6, 1, 0).length() < 16, "Default ordinary rain stays below body-force threshold");
                }
                windows++; if (selected) selectedWindows++;
            }
        }
        double probability = (double) selectedWindows / windows;
        helper.assertTrue(probability > .04 && probability < .08, "Rare gust windows stay near 6%: " + probability);
        helper.assertTrue(maximum > 79.99 && (double) capSamples / samples < .003, "Maximum speed exists but occupies under 0.3% of thunder time");
        helper.assertTrue((double) extremeSamples / samples < .025, "Extreme wind occupies under 2.5% of thunder time");
        helper.assertTrue(maxNaturalStep < .4, "Natural gusts and window boundaries cannot jump to the cap: max tick vector step=" + maxNaturalStep);
        for (int tick = 0; tick < 100; tick++) {
            float a = tick / 100F, b = (tick + 1) / 100F;
            var before = WeatherWind.sample(1234 + tick / 20.0, .7, 6, a, a);
            var after = WeatherWind.sample(1234 + (tick + 1) / 20.0, .7, 6, b, b);
            maxStep = Math.max(maxStep, before.distanceTo(after));
        }
        helper.assertTrue(maxStep < 1.8, "Rapid synthetic weather ramps remain continuous: " + maxStep);
        var level = helper.getLevel(); var state = WindSavedData.get(level);
        CompoundTag saved = state.save(new CompoundTag(), level.registryAccess());
        float oldRain = level.getRainLevel(1), oldThunder = level.getThunderLevel(1);
        try {
            state.setAutomatic(); level.setRainLevel(0); level.setThunderLevel(0);
            var clear = state.ambient(level);
            level.setRainLevel(1); level.setThunderLevel(0); var rain = state.ambient(level);
            level.setThunderLevel(1); var storm = state.ambient(level);
            helper.assertTrue(rain.length() > clear.length() && storm.length() > rain.length(), "Owning server wind reads live weather");
            helper.assertTrue(clear.normalize().distanceTo(storm.normalize()) > .3, "Owning server bearing follows live storm");
            state.setFixed(90, 12);
            helper.assertTrue(Math.abs(state.ambient(level).x - 12) < .001, "Explicit fixed test wind still overrides weather");
            java.nio.file.Files.writeString(java.nio.file.Path.of("weather-wind-result.txt"),
                    "PASS: 4120 phase/time samples: rain and thunder strengthen and turn wind\n"
                    + "PASS: max per-tick weather transition vector change=" + maxStep + " m/s\n"
                    + "PASS: owning server live weather clear=" + clear + " rain=" + rain + " storm=" + storm + "\n"
                    + "PASS: speed cap and explicit fixed override\n");
            java.nio.file.Files.writeString(java.nio.file.Path.of("weather-wind-result.txt"),
                    "PASS: rare windows=" + selectedWindows + "/" + windows + " (" + probability + ")\n"
                    + "PASS: extreme time share=" + (double) extremeSamples / samples + "; maximum time share=" + (double) capSamples / samples + "\n"
                    + "PASS: maximum speed=" + maximum + "; max per-tick natural vector step=" + maxNaturalStep + " m/s\n"
                    + "PASS: all ordinary rain samples below 16 m/s; live weather and fixed wind overrides\n",
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException error) { throw new RuntimeException(error); }
        finally {
            level.setRainLevel(oldRain); level.setThunderLevel(oldThunder);
            if (saved.getBoolean("automatic")) state.setAutomatic();
            else state.setFixed(saved.getDouble("bearing"), saved.getDouble("speed"));
        }
        helper.succeed();
    }
}
