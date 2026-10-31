package com.shouyun.windweave.testing;

import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.wind.WindField;
import com.shouyun.windweave.wind.WindFireBehavior;
import com.shouyun.windweave.wind.WindSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@EventBusSubscriber(modid = Windweave.MODID)
@PrefixGameTestTemplate(false)
public final class WindFireGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("windweave.gametest")) event.register(WindFireGameTests.class);
    }

    // A separate sequential batch: dimension-wide test winds never race projectile scenarios.
    @GameTest(batch = "windFire", template = "empty", templateNamespace = Windweave.MODID, timeoutTicks = 100)
    public static void fireRespondsToWind(GameTestHelper helper) {
        var level = helper.getLevel();
        var wind = WindSavedData.get(level);
        var rule = level.getGameRules().getRule(GameRules.RULE_DOFIRETICK);
        boolean originalRule = rule.get();
        RandomSource random = RandomSource.create(732049);
        StringBuilder report = new StringBuilder();
        try {
            rule.set(true, level.getServer());
            BlockPos source = helper.absolutePos(new BlockPos(12, 3, 12));
            BlockPos east = source.east(2), west = source.west(2);
            level.setBlockAndUpdate(source.below(), Blocks.NETHERRACK.defaultBlockState());
            level.setBlockAndUpdate(source, Blocks.FIRE.defaultBlockState());
            level.setBlockAndUpdate(east.below(), Blocks.HAY_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(west.below(), Blocks.HAY_BLOCK.defaultBlockState());
            wind.setFixed(90, 0);
            for (int i = 0; i < 128; i++) WindFireBehavior.fanFire(level, source, random);
            check(helper, level.isEmptyBlock(east) && level.isEmptyBlock(west), report, "Calm adds no fire spread");
            wind.setFixed(90, 12);
            for (int i = 0; i < 128 && level.isEmptyBlock(east); i++) WindFireBehavior.fanFire(level, source, random);
            check(helper, level.getBlockState(east).is(Blocks.FIRE) && level.isEmptyBlock(west), report, "East wind ignites downwind fuel only");
            level.removeBlock(east, false);
            wind.setFixed(270, 12);
            for (int i = 0; i < 128 && level.isEmptyBlock(west); i++) WindFireBehavior.fanFire(level, source, random);
            check(helper, level.getBlockState(west).is(Blocks.FIRE) && level.isEmptyBlock(east), report, "Reversed wind reverses extra ignition");
            level.removeBlock(west, false);
            wind.setFixed(90, 40);
            rule.set(false, level.getServer());
            for (int i = 0; i < 128; i++) {
                WindFireBehavior.fanFire(level, source, random);
                WindFireBehavior.blowOutFire(level, source, random);
            }
            check(helper, level.isEmptyBlock(east) && level.getBlockState(source).is(Blocks.FIRE), report, "doFireTick=false blocks wind fire changes");
            rule.set(true, level.getServer());
            level.setBlockAndUpdate(source.east(), Blocks.STONE.defaultBlockState());
            for (int i = 0; i < 128; i++) WindFireBehavior.fanFire(level, source, random);
            check(helper, level.isEmptyBlock(east), report, "Fire cannot jump through a downwind wall");
            level.removeBlock(source.east(), false);
            level.setBlockAndUpdate(east, Blocks.WATER.defaultBlockState());
            for (int i = 0; i < 128; i++) WindFireBehavior.fanFire(level, source, random);
            check(helper, level.getBlockState(east).is(Blocks.WATER), report, "Wind ignition never replaces water");
            level.setBlockAndUpdate(east, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(east.below(), Blocks.STONE.defaultBlockState());
            for (int i = 0; i < 128; i++) WindFireBehavior.fanFire(level, source, random);
            check(helper, level.isEmptyBlock(east), report, "No extra fire on nonflammable downwind terrain");
            check(helper, WindFireBehavior.blowoutChance(12, false, false) == 0
                    && WindFireBehavior.blowoutChance(32, true, false) < WindFireBehavior.blowoutChance(32, false, false),
                    report, "Only strong wind blows out fire; nearby fuel resists blowout");

            BlockPos small = helper.absolutePos(new BlockPos(18, 3, 18));
            level.setBlockAndUpdate(small.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(small, Blocks.FIRE.defaultBlockState());
            level.setBlockAndUpdate(small.west(2), Blocks.STONE.defaultBlockState());
            check(helper, WindField.sample(level, Vec3.atCenterOf(small)).lengthSqr() == 0, report, "Upwind wall shelters a fire");
            for (int i = 0; i < 128; i++) WindFireBehavior.blowOutFire(level, small, random);
            check(helper, level.getBlockState(small).is(Blocks.FIRE), report, "Sheltered fire is not blown out");
            level.removeBlock(small.west(2), false);
            // Invoke BlockState.tick to exercise the actual FireBlock mixin, not just the helper.
            for (int i = 0; i < 128 && level.getBlockState(small).is(Blocks.FIRE); i++) level.getBlockState(small).tick(level, small, random);
            check(helper, level.isEmptyBlock(small), report, "Actual FireBlock tick extinguishes exposed fire in strong wind");
            for (int i = 0; i < 128; i++) WindFireBehavior.blowOutFire(level, source, random);
            check(helper, level.getBlockState(source).is(Blocks.FIRE), report, "Netherrack eternal fire remains protected");

            BlockPos camp = helper.absolutePos(new BlockPos(18, 3, 7));
            level.setBlockAndUpdate(camp.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(camp, Blocks.CAMPFIRE.defaultBlockState());
            long delay = Math.floorMod(-level.getGameTime() - camp.asLong(), 40);
            if (delay == 0) delay = 40;
            helper.runAfterDelay(delay, () -> {
                try {
                    for (int i = 0; i < 256 && level.getBlockState(camp).getValue(CampfireBlock.LIT); i++) {
                        CampfireBlockEntity.cookTick(level, camp, level.getBlockState(camp), (CampfireBlockEntity) level.getBlockEntity(camp));
                    }
                    check(helper, !level.getBlockState(camp).getValue(CampfireBlock.LIT), report, "Actual campfire cookTick can blow out a lit campfire");
                    java.nio.file.Files.writeString(java.nio.file.Path.of("fire-test-result.txt"), report + "PASS\n");
                    helper.succeed();
                } catch (java.io.IOException error) { throw new RuntimeException(error); }
                finally { rule.set(originalRule, level.getServer()); wind.setAutomatic(); }
            });
        } catch (Throwable error) {
            rule.set(originalRule, level.getServer());
            wind.setAutomatic();
            throw error;
        }
    }

    private static void check(GameTestHelper helper, boolean condition, StringBuilder report, String message) {
        helper.assertTrue(condition, message);
        report.append("PASS: ").append(message).append('\n');
    }
}
