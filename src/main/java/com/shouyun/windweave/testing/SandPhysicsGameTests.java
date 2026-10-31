package com.shouyun.windweave.testing;

import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.Config;
import com.shouyun.windweave.wind.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@EventBusSubscriber(modid = Windweave.MODID)
@PrefixGameTestTemplate(false)
public final class SandPhysicsGameTests {
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("windweave.gametest")) event.register(SandPhysicsGameTests.class);
    }
    @GameTest(batch = "sandPhysics", template = "flight", templateNamespace = Windweave.MODID, timeoutTicks = 100)
    public static void actualItemsAndShelter(GameTestHelper h) {
        var level = h.getLevel(); var state = WindSavedData.get(level); state.setFixed(90, 20);
        h.assertTrue(Sandstorm.strength(8) == 0 && Sandstorm.strength(22) == 1, "Storm threshold must distinguish ordinary wind from storms");
        h.assertTrue(WindMath.itemCorrection(new Vec3(1, -.2, 0), Vec3.ZERO, false, 1).equals(Vec3.ZERO), "Calm leaves vanilla item velocity untouched");
        h.assertTrue(WindMath.itemCorrection(Vec3.ZERO, new Vec3(4,0,0), true,1).equals(Vec3.ZERO), "Ground friction resists a breeze");
        Vec3 single = WindMath.itemCorrection(Vec3.ZERO, new Vec3(20,0,0), false,1);
        h.assertTrue(single.y == 0 && single.length() <= .045001 && single.x > WindMath.itemCorrection(Vec3.ZERO,new Vec3(20,0,0),false,64).x,
                "Item drag is horizontal, bounded and stack-sensitive");
        for (int y=1; y<14; y++) for (int z=8; z<16; z++) h.setBlock(15,y,z,Blocks.STONE);
        double previous=-1;
        for (int i=0; i<=28; i++) {
            Vec3 p=h.absoluteVec(new Vec3(17+i*.25,8,12)); double speed=WindField.sample(level,p).length();
            h.assertTrue(speed >= previous-1E-6 && (previous<0 || speed-previous<1.3), "Wake recovers monotonically without a hard jump");
            previous=speed;
        }
        var light=item(h,30,10,25,Items.FEATHER,1);
        var heavy=item(h,30,10,30,Items.COBBLESTONE,64);
        var sheltered=item(h,17,8,12,Items.PAPER,1);
        // Enclosed source water prevents flow from confounding the air-only check.
        for (int x=39;x<=41;x++) for(int z=24;z<=26;z++) for(int y=6;y<=9;y++) h.setBlock(x,y,z,Blocks.STONE);
        h.setBlock(40,7,25,Blocks.WATER);h.setBlock(40,8,25,Blocks.WATER);
        var wet=item(h,40.5,7.2,25.5,Items.STICK,1);
        h.runAfterDelay(20,()->{
            h.assertTrue(light.getX()-h.absoluteVec(new Vec3(30,10,25)).x>3, "Real loose item visibly drifts under wind");
            h.assertTrue(light.getDeltaMovement().x>heavy.getDeltaMovement().x*2, "Full stack has greater inertia in actual entity ticks");
            h.assertTrue(Math.abs(sheltered.getDeltaMovement().x)<1E-8, "Near-wall item remains sheltered");
            h.assertTrue(wet.isInWater() && Math.abs(wet.getDeltaMovement().x)<1E-8, "Submerged items keep vanilla fluid behavior");
            state.setFixed(270,20);
            var west=item(h,35,12,35,Items.FLINT,1);
            h.runAfterDelay(15,()->{
                h.assertTrue(west.getDeltaMovement().x<-.15, "Reverse wind reverses actual item force");
                Config.ITEM_WIND.set(false);
                var disabled=item(h,35,12,40,Items.CLAY_BALL,1);
                h.runAfterDelay(8,()->{
                    boolean still=Math.abs(disabled.getDeltaMovement().x)<1E-8;
                    Config.ITEM_WIND.set(true); state.setAutomatic();
                    h.assertTrue(still, "Item switch disables physics");
                    try { java.nio.file.Files.writeString(java.nio.file.Path.of("sand-physics-result.txt"),"PASS: thresholds, calm, friction, acceleration cap, stack inertia, graded shelter, actual item drift, underwater, reverse wind, switch\n"); }
                    catch(Exception e){throw new RuntimeException(e);}
                    h.succeed();
                });
            });
        });
    }
    private static ItemEntity item(GameTestHelper h,double x,double y,double z,Item type,int count) {
        Vec3 p=h.absoluteVec(new Vec3(x,y,z));
        var item=new ItemEntity(h.getLevel(),p.x,p.y,p.z,new ItemStack(type,count),0,0,0);
        item.setNoGravity(true);item.setNeverPickUp();h.getLevel().addFreshEntity(item);return item;
    }
}
