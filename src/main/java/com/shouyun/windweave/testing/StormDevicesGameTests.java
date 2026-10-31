package com.shouyun.windweave.testing;

import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.device.*;
import com.shouyun.windweave.wind.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@EventBusSubscriber(modid = Windweave.MODID)
@PrefixGameTestTemplate(false)
public final class StormDevicesGameTests {
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("windweave.gametest")) event.register(StormDevicesGameTests.class);
    }
    @GameTest(batch = "stormDevices", template = "flight", templateNamespace = Windweave.MODID, timeoutTicks = 100)
    public static void stormCanyonControllersAndLift(GameTestHelper helper) {
        double peak = 0, quiet = 100;
        for (int t = 0; t < 86400; t++) {
            double speed = WeatherWind.sample(t, .4, 6, 1, 1).length();
            peak = Math.max(peak, speed); quiet = Math.min(quiet, speed);
        }
        helper.assertTrue(peak >= 79.99 && quiet < 20, "Rare thunder fronts eventually reach the cap with playable intervals");
        for (double speed : new double[]{0, 6, 10, 13.5, 16}) {
            for (var movement : new Vec3[]{Vec3.ZERO, new Vec3(-.22, 0, 0), new Vec3(.22, 0, 0)})
                helper.assertTrue(BodyWind.correction(movement, new Vec3(speed, 0, 0), true, false, 1).equals(Vec3.ZERO),
                        "Rain-range wind preserves walking speed and never sends motion corrections");
        }
        var push = BodyWind.correction(new Vec3(-.22, 0, 0), new Vec3(30, 0, 0), true, false, 1);
        helper.assertTrue(push.x > 0 && push.x < .004 && push.y == 0, "Moderate headwind causes only gentle ground resistance");
        Vec3 lift = BodyWind.correction(Vec3.ZERO, new Vec3(60, 0, 0), true, false, 1);
        Vec3 braced = BodyWind.correction(Vec3.ZERO, new Vec3(60, 0, 0), true, true, 1);
        helper.assertTrue(lift.y > .08 && braced.y < .08 && braced.x < lift.x, "Extreme gust overcomes gravity; crouching braces on the ground");
        helper.assertTrue(BodyWind.correction(Vec3.ZERO, Vec3.ZERO, true, false, 1).equals(Vec3.ZERO), "Calm never lifts a body");

        var level = helper.getLevel(); var state = WindSavedData.get(level);
        state.setFixed(0, 12);
        for (int x : new int[]{40, 56}) for (int y = 0; y <= 20; y++) for (int z = 5; z < 91; z++) helper.setBlock(x, y, z, Blocks.STONE);
        TerrainWind.clear(level);
        Vec3 canyon = WindField.sample(level, helper.absoluteVec(new Vec3(48.5, 10.5, 48.5)));
        helper.assertTrue(canyon.horizontalDistance() > 15 && canyon.z < 0, "Tall narrow canyon accelerates aligned flow: " + canyon);
        state.setFixed(30, 12); TerrainWind.clear(level);
        Vec3 turned = WindField.sample(level, helper.absoluteVec(new Vec3(48.5, 10.5, 48.5)));
        helper.assertTrue(Math.abs(turned.x / turned.z) < Math.tan(Math.toRadians(30)), "Canyon guides diagonal wind toward its axis");

        BlockPos controlPos = new BlockPos(75, 4, 48);
        helper.setBlock(controlPos, WindDevices.CONTROLLER.get());
        var control = (WindControllerBlockEntity) level.getBlockEntity(helper.absolutePos(controlPos));
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        Vec3 near = helper.absoluteVec(new Vec3(75.5, 5, 48.5)); player.moveTo(near.x, near.y, near.z, 0, 0);
        var menu = new WindControllerMenu(1, player.getInventory(), control);
        helper.assertTrue(menu.clickMenuButton(player, 1) && menu.clickMenuButton(player, 9) && menu.clickMenuButton(player, 11)
                && menu.clickMenuButton(player, 12), "Nearby player can set direction, speed, radius and enable controller");
        helper.assertTrue(control.bearing == 90 && control.speed == 17 && control.radius == 32 && control.enabled, "GUI data reaches owning block entity");
        player.moveTo(near.x + 20, near.y, near.z, 0, 0);
        helper.assertTrue(!menu.clickMenuButton(player, 12) && control.enabled, "Distant menu clicks cannot change controller");
        control.configure(90, 24, 16, true);
        state.setFixed(0, 12);
        Vec3 core = WindField.sample(level, helper.absoluteVec(new Vec3(76.5, 5.5, 48.5)));
        helper.assertTrue(core.x > 23.9 && Math.abs(core.z) < .01, "Controller core overrides direction and speed");
        Vec3 outside = WindField.sample(level, helper.absoluteVec(new Vec3(90.5, 5.5, 80.5)));
        helper.assertTrue(outside.z < -11.9 && Math.abs(outside.x) < .01, "Outside radius retains regional wind");
        var saved = control.saveWithoutMetadata(level.registryAccess());
        var loaded = new WindControllerBlockEntity(control.getBlockPos(), control.getBlockState()); loaded.loadWithComponents(saved, level.registryAccess());
        helper.assertTrue(loaded.speed == 24 && loaded.bearing == 90 && loaded.radius == 16 && loaded.enabled, "Controller settings survive NBT round trip");
        var packet = new com.shouyun.windweave.network.WindPayload(level.dimension().location(), 0, -12, WindSources.nearby(level, near));
        var buffer = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
        try {
            com.shouyun.windweave.network.WindPayload.STREAM_CODEC.encode(buffer, packet);
            helper.assertTrue(com.shouyun.windweave.network.WindPayload.STREAM_CODEC.decode(buffer).sources().equals(packet.sources()), "Nearby controller sources survive network codec");
        } finally { buffer.release(); }
        control.configure(90, 60, 24, true);
        var zombie = helper.spawn(EntityType.ZOMBIE, new Vec3(76.5, 5, 45.5)); zombie.setInvulnerable(true);
        double startY = zombie.getY();
        for (int x = 83; x <= 85; x++) for (int y = 3; y <= 7; y++) for (int z = 50; z <= 52; z++) helper.setBlock(x, y, z, Blocks.WATER);
        var wet = helper.spawn(EntityType.ZOMBIE, new Vec3(84.5, 4, 51.5)); wet.setNoAi(true); wet.setInvulnerable(true);
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(zombie.getY() > startY + 1 && zombie.getDeltaMovement().x > .05,
                    "Actual extreme-wind body ticks push and lift a living entity; age=" + zombie.tickCount + " dy=" + (zombie.getY() - startY)
                    + " v=" + zombie.getDeltaMovement() + " wind=" + WindField.sample(level, zombie.position().add(0, 1, 0)) + " alive=" + zombie.isAlive());
            helper.assertTrue(wet.isInWaterOrBubble() && Math.abs(wet.getDeltaMovement().x) < .03, "Submerged bodies skip air wind");
            control.configure(90, 0, 16, true);
            helper.assertTrue(WindField.sample(level, helper.absoluteVec(new Vec3(76.5, 5.5, 48.5))).lengthSqr() < .001, "Controller can create a calm pocket in a windy dimension");
            control.configure(90, 24, 16, false);
            helper.assertTrue(WindField.sample(level, helper.absoluteVec(new Vec3(76.5, 5.5, 48.5))).z < -11.9, "Disabling controller restores natural regional wind");
            helper.setBlock(controlPos, Blocks.AIR);
            helper.assertTrue(WindSources.nearby(level, near).isEmpty(), "Removing controller unregisters its field");
            state.setAutomatic();
            helper.succeed();
        });
    }
}
