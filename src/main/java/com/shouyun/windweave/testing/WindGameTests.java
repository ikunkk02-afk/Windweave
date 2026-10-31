package com.shouyun.windweave.testing;

import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.mixin.AbstractArrowAccessor;
import com.shouyun.windweave.wind.WindField;
import com.shouyun.windweave.wind.WindMath;
import com.shouyun.windweave.wind.WindSavedData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Enabled only by the development GameTest run; no automatic registration in player worlds. */
@EventBusSubscriber(modid = Windweave.MODID)
@PrefixGameTestTemplate(false)
public final class WindGameTests {
    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("windweave.gametest")) event.register(WindGameTests.class);
    }

    @GameTest(template = "empty", templateNamespace = Windweave.MODID)
    public static void physicalInvariants(GameTestHelper helper) {
        Vec3 velocity = new Vec3(0, -.2, -2);
        helper.assertTrue(WindMath.dragCorrection(velocity, Vec3.ZERO, .012).equals(Vec3.ZERO), "Calm must be exactly vanilla");
        Vec3 cross = WindMath.dragCorrection(velocity, new Vec3(.6, 0, 0), .012);
        helper.assertTrue(cross.x > 0 && cross.y == 0, "Crosswind bends east without changing gravity");
        helper.assertTrue(WindMath.dragCorrection(velocity, new Vec3(0, 0, -.6), .012).z < 0, "Tailwind reduces deceleration");
        helper.assertTrue(WindMath.dragCorrection(velocity, new Vec3(0, 0, .6), .012).z > 0, "Headwind slows northward flight");
        Vec3 fast = new Vec3(0, 0, -3);
        Vec3 withTailwind = fast.scale(.99).add(WindMath.dragCorrection(fast.scale(.99), new Vec3(0, 0, -.6), .012));
        helper.assertTrue(withTailwind.length() < fast.length(), "A slower tailwind must never accelerate a faster arrow");
        helper.assertTrue(WindMath.dragCorrection(new Vec3(200, 0, -200), new Vec3(2, 0, 0), .1).length() <= .080001,
                "Extreme input must respect acceleration bound");
        Vec3 smoke = Vec3.ZERO;
        for (int i = 0; i < 100; i++) smoke = WindMath.smokeStep(smoke, new Vec3(.6, 0, 0), .06);
        helper.assertTrue(smoke.x > .59 && smoke.x < .6 && smoke.y == 0, "Smoke approaches air velocity without overshoot");
        helper.assertTrue(WindMath.fromBearing(90, 12).distanceTo(new Vec3(12, 0, 0)) < 1E-8, "Compass east means +X");
        helper.assertTrue(WindMath.fromBearing(0, 12).distanceTo(new Vec3(0, 0, -12)) < 1E-8, "Compass north means -Z");
        helper.succeed();
    }

    @GameTest(batch = "playerLaunch", template = "flight", templateNamespace = Windweave.MODID, timeoutTicks = 80)
    public static void playerItemLaunches(GameTestHelper helper) {
        var level = helper.getLevel();
        WindSavedData.get(level).setFixed(90, 12);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().instabuild = true;
        player.getInventory().add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW, 64));
        var samples = new java.util.ArrayList<LaunchSample>();
        net.minecraft.world.item.Item[] items = {net.minecraft.world.item.Items.BOW, net.minecraft.world.item.Items.SNOWBALL, net.minecraft.world.item.Items.TRIDENT};
        for (int i = 0; i < items.length; i++) {
            var pos = helper.absoluteVec(new Vec3(38 + i * 10, 17, 75));
            player.moveTo(pos.x, pos.y, pos.z, 180, 0);
            player.setDeltaMovement(Vec3.ZERO);
            var stack = new net.minecraft.world.item.ItemStack(items[i]);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            items[i].use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (items[i] != net.minecraft.world.item.Items.SNOWBALL) {
                items[i].releaseUsing(stack, level, player, items[i].getUseDuration(stack, player) - 20);
            }
            var launched = level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,
                            new net.minecraft.world.phys.AABB(pos.add(-2, -2, -2), pos.add(2, 4, 2)))
                    .stream().filter(entity -> entity.getOwner() == player).findFirst().orElseThrow();
            samples.add(new LaunchSample(launched, launched.position(), launched.getDeltaMovement()));
            player.stopUsingItem();
        }
        helper.runAfterDelay(20, () -> {
            StringBuilder report = new StringBuilder();
            for (LaunchSample sample : samples) {
                int n = sample.entity.tickCount;
                double calmX = sample.start.x + sample.velocity.x * (1 - Math.pow(.99, n)) / .01;
                double deviation = sample.entity.getX() - calmX;
                report.append(sample.entity.getType()).append(" ticks=").append(n).append(" launchSpeed=")
                        .append(sample.velocity.length()).append(" eastDeflection=").append(deviation).append('\n');
                helper.assertTrue(n == 20 && sample.entity.isAlive(), "Actual launched projectile must fly twenty normal ticks");
                helper.assertTrue(deviation > 3, "Normal-speed player-item launch must visibly deflect at 12 m/s: " + deviation);
            }
            com.mojang.logging.LogUtils.getLogger().info("WINDWEAVE REAL ITEM LAUNCHES\n{}", report);
            try { java.nio.file.Files.writeString(java.nio.file.Path.of("real-launch-result.txt"), report); }
            catch (java.io.IOException error) { throw new RuntimeException(error); }
            WindSavedData.get(level).setAutomatic();
            helper.succeed();
        });
    }

    private record LaunchSample(net.minecraft.world.entity.projectile.Projectile entity, Vec3 start, Vec3 velocity) {}

    @GameTest(template = "empty", templateNamespace = Windweave.MODID, timeoutTicks = 100)
    public static void actualProjectileAndIndicatorTicks(GameTestHelper helper) {
        var level = helper.getLevel();
        var state = WindSavedData.get(level);
        // One sequential scenario: tests never race by changing the same dimension's wind.
        var source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(helper.absoluteVec(new Vec3(4, 3, 4))).withPermission(4);
        level.getServer().getCommands().performPrefixedCommand(source, "windweave set east 12");
        helper.assertTrue(!state.isAutomatic(), "Fixed wind command must execute");
        CompoundTag saved = state.save(new CompoundTag(), level.registryAccess());
        helper.assertTrue(!saved.getBoolean("automatic") && saved.getDouble("speed") == 12, "Control state must persist");

        // Keep wind probes eight blocks away from the GameTest barrier enclosure.
        var arrow = helper.spawn(EntityType.ARROW, new Vec3(10, 6, 12));
        var snowball = helper.spawn(EntityType.SNOWBALL, new Vec3(13, 6, 12));
        var trident = helper.spawn(EntityType.TRIDENT, new Vec3(16, 6, 12));
        for (var projectile : java.util.List.of(arrow, snowball, trident)) {
            projectile.setNoGravity(true);
            projectile.setDeltaMovement(0, 0, -.4);
        }
        var indicator = helper.spawn(Windweave.WIND_INDICATOR.get(), new Vec3(12, 1, 4));

        // An embedded arrow must not creep out of its block under wind.
        helper.setBlock(12, 2, 4, Blocks.STONE);
        var embedded = helper.spawn(EntityType.ARROW, new Vec3(12.5, 2.5, 4.5));
        embedded.setNoGravity(true);
        var embeddedTag = new CompoundTag();
        embedded.addAdditionalSaveData(embeddedTag);
        embeddedTag.putBoolean("inGround", true);
        embedded.readAdditionalSaveData(embeddedTag);

        helper.setBlock(12, 2, 8, Blocks.WATER);
        var underwater = helper.spawn(EntityType.ARROW, new Vec3(12.5, 2.3, 8.5));
        underwater.setNoGravity(true);
        underwater.setDeltaMovement(Vec3.ZERO);

        // Upwind wall at -X blocks an eastward flow, but air above it remains exposed.
        helper.setBlock(7, 4, 9, Blocks.STONE);
        helper.assertTrue(WindField.sample(level, helper.absoluteVec(new Vec3(9.5, 4.5, 9.5))).equals(Vec3.ZERO), "Wall must shelter downwind point");
        helper.assertTrue(WindField.sample(level, helper.absoluteVec(new Vec3(9.5, 6.5, 9.5))).x > 11.9, "Open air must see the same east wind");

        helper.runAfterDelay(8, () -> {
            helper.assertTrue(arrow.getDeltaMovement().x > .01, "Arrow must acquire eastward motion through actual event ticks; ticks="
                    + arrow.tickCount + " velocity=" + arrow.getDeltaMovement() + " local=" + WindField.sample(level, arrow.position())
                    + " inGround=" + ((AbstractArrowAccessor) arrow).windweave$isInGround() + " removed=" + arrow.isRemoved());
            helper.assertTrue(snowball.getDeltaMovement().x > arrow.getDeltaMovement().x, "Light thrown object must respond more than arrow");
            helper.assertTrue(trident.getDeltaMovement().x > 0 && trident.getDeltaMovement().x < arrow.getDeltaMovement().x, "Heavy trident responds less");
            helper.assertTrue(((AbstractArrowAccessor) embedded).windweave$isInGround() && embedded.getDeltaMovement().lengthSqr() < 1E-10,
                    "Embedded arrow must remain still");
            helper.assertTrue(underwater.isInWaterOrBubble() && Math.abs(underwater.getDeltaMovement().x) < 1E-8, "No wind underwater");
            helper.assertTrue(indicator.isRemoved(), "Obsolete fixed arrow entity must be retired");
            state.setFixed(270, 12);
            var westArrow = helper.spawn(EntityType.ARROW, new Vec3(10, 8, 12));
            westArrow.setNoGravity(true);
            westArrow.setDeltaMovement(0, 0, -.4);
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(westArrow.getDeltaMovement().x < -.01, "Reversed wind must reverse trajectory deflection");
                level.getServer().getCommands().performPrefixedCommand(source, "windweave calm");
                var calmArrow = helper.spawn(EntityType.ARROW, new Vec3(10, 10, 12));
                calmArrow.setNoGravity(true);
                calmArrow.setDeltaMovement(0, 0, -.4);
                helper.runAfterDelay(8, () -> {
                    helper.assertTrue(Math.abs(calmArrow.getDeltaMovement().x) < 1E-10, "Calm must not deflect a new arrow");
                    helper.assertTrue(indicator.isRemoved(), "No fixed arrow entity may remain");
                    level.getServer().getCommands().performPrefixedCommand(source, "windweave auto");
                    helper.assertTrue(state.isAutomatic(), "Auto command restores natural wind");
                    helper.succeed();
                });
            });
        });
    }
}
