package com.shouyun.windweave.testing;

import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.client.WindAnimationClock;
import com.shouyun.windweave.wind.VisualWindExposure;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@EventBusSubscriber(modid = Windweave.MODID)
@PrefixGameTestTemplate(false)
public final class VisualContinuityGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("windweave.gametest")) event.register(VisualContinuityGameTests.class);
    }

    // Interpolated eight-block rays and the wall must fit inside the fixture's barrier enclosure.
    @GameTest(batch = "visualContinuity", template = "flight", templateNamespace = Windweave.MODID)
    public static void continuousWindAndShelter(GameTestHelper helper) {
        StringBuilder report = new StringBuilder();
        WindAnimationClock slow = simulate(30), fast = simulate(144);
        check(helper, Math.abs(slow.travelX() - fast.travelX()) < 1E-6 && slow.wind().distanceTo(fast.wind()) < 1E-6,
                report, "Wind smoothing and accumulated travel are independent of frame rate");
        double time = fast.seconds(), travel = fast.travelX();
        fast.advance(12_000_000_000L, true, Vec3.ZERO);
        check(helper, fast.seconds() == time && fast.travelX() == travel, report, "Pausing freezes phase and advection");
        fast.advance(12_008_333_333L, false, new Vec3(11.5, 0, .8));
        check(helper, Math.abs(fast.seconds() - time - 1.0 / 120) < 1E-8 && Math.abs(fast.travelX() - travel) < .01,
                report, "Resuming and gust changes cannot jump the phase");

        var level = helper.getLevel();
        for (int y = 1; y <= 9; y++) for (int z = 3; z <= 25; z++) helper.setBlock(12, y, z, Blocks.STONE);
        var center = helper.absoluteVec(new Vec3(14.5, 3.5, 14.5));
        var field = new VisualWindExposure();
        double previous = -1, largestStep = 0;
        int intermediate = 0;
        for (int i = 0; i <= 40; i++) {
            double exposure = west(field.sample(level, center.x + i * .25, center.y, center.z));
            if (exposure > 0 && exposure < 1) intermediate++;
            if (previous >= 0) {
                largestStep = Math.max(largestStep, Math.abs(exposure - previous));
                helper.assertTrue(exposure + .001 >= previous, "Wind wake should recover gradually away from the wall");
            }
            previous = exposure;
        }
        check(helper, intermediate > 15 && largestStep <= .10, report, "Wall wake has a graded transition; largest quarter-block step=" + largestStep);
        check(helper, previous > .99, report, "Open terrain recovers full wind exposure");
        int a = field.sample(level, center.x + 1.5, center.y + .5, center.z);
        int b = new VisualWindExposure().sample(level, center.x + 1.5, center.y + .5, center.z);
        check(helper, a == b, report, "Independent section caches agree at shared vertices");
        for (int y = 1; y <= 9; y++) for (int z = 3; z <= 25; z++) helper.setBlock(12, y, z, Blocks.OAK_LEAVES);
        double leaves = west(new VisualWindExposure().sample(level, center.x, center.y, center.z));
        check(helper, leaves > .99, report, "Leaves do not pin neighboring leaf animation");
        try { java.nio.file.Files.writeString(java.nio.file.Path.of("continuity-test-result.txt"), report + "PASS\n"); }
        catch (java.io.IOException error) { throw new RuntimeException(error); }
        helper.succeed();
    }

    private static WindAnimationClock simulate(int fps) {
        var clock = new WindAnimationClock();
        clock.advance(1_000_000_000L, false, new Vec3(12, 0, 0));
        for (int i = 1; i <= fps * 10; i++) clock.advance(1_000_000_000L + Math.round(i * 1E9 / fps), false, new Vec3(12, 0, 0));
        return clock;
    }
    private static double west(int packed) { return ((packed >> 15) & 31) / 31.0; }
    private static void check(GameTestHelper helper, boolean value, StringBuilder report, String message) {
        helper.assertTrue(value, message); report.append("PASS: ").append(message).append('\n');
    }
}
