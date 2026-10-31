package com.shouyun.windweave.testing;

import com.mojang.logging.LogUtils;
import com.shouyun.windweave.*;
import com.shouyun.windweave.client.*;
import com.shouyun.windweave.particle.WeatherParticles;
import com.shouyun.windweave.wind.ClientWindState;
import net.minecraft.client.*;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;
import java.nio.file.*;

/** Development-only acceptance run through real providers, audio engine, fog and world collisions. */
@EventBusSubscriber(modid = Windweave.MODID, value = Dist.CLIENT)
public final class SandClientTest {
    private static final StringBuilder REPORT = new StringBuilder();
    private static int ticks, reloadTick;
    private static boolean reloaded;
    private static WindSandParticle dust, grain, wall, wet;
    private static Particle debris;
    private static Vec3 dustStart, debrisStart;
    private static long rendered;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("windweave.sandClientTest")) return;
        var mc=Minecraft.getInstance();
        if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null || mc.isPaused())return;
        ticks++;mc.player.setYRot(180);mc.player.setXRot(8);
        try {
            if(ticks==1){
                mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.particles().set(ParticleStatus.ALL);
                ClientConfig.SAND_DRIFT.set(true);ClientConfig.SANDSTORM.set(true);ClientConfig.WIND_SOUND.set(true);
                ClientConfig.SAND_DENSITY.set(1.0);ClientConfig.PARTICLE_PHYSICS.set(true);
                var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                server.execute(()->{
                    var source=server.createCommandSourceStack();
                    String name=server.getPlayerList().getPlayer(uuid).getScoreboardName();
                    for(String cmd:new String[]{
                            "fill -24 99 -24 24 99 24 minecraft:stone",
                            "fill -24 100 -24 24 100 24 minecraft:sand",
                            "fill -24 101 -24 24 113 24 minecraft:air",
                            "fill -24 114 -24 24 126 24 minecraft:air",
                            "fill -24 127 -24 24 139 24 minecraft:air",
                            "fill -24 140 -24 24 145 24 minecraft:air",
                            "fillbiome -24 96 -24 24 107 24 minecraft:desert",
                            "fillbiome -24 108 -24 24 119 24 minecraft:desert",
                            "fillbiome -24 120 -24 24 131 24 minecraft:desert",
                            "fillbiome -24 132 -24 24 143 24 minecraft:desert",
                            "fill -8 101 -10 -8 120 -4 minecraft:stone",
                            "fill 8 108 -10 14 108 -4 minecraft:stone",
                            "setblock 6 105 0 minecraft:water",
                            "time set noon","gamerule doDaylightCycle false","gamerule doWeatherCycle false",
                            "weather clear","windweave calm","gamemode spectator "+name,"tp "+name+" 0 102 12 180 8"
                    })server.getCommands().performPrefixedCommand(source,cmd);
                    server.getCommands().performPrefixedCommand(server.getPlayerList().getPlayer(uuid).createCommandSourceStack(),"windweave flow off");
                });
            }
            // Discard particles emitted from the previous saved wind while setup packets were arriving.
            if(ticks==80) WindSand.clear();
            if(ticks==100){
                check(WindSand.count()==0 && WindSand.intensity()<.001,"Calm desert has no natural sand emission or sandstorm fog: count="+WindSand.count()+" intensity="+WindSand.intensity()+" wind="+ClientWindState.ambient(mc.level));
                command(mc,"windweave set east 8");
            }
            if(ticks==180){
                check(WindSand.count()>15 && WindSand.intensity()<.001,"Ordinary 8 m/s wind emits surface sand without a sandstorm: "+WindSand.count());
                screenshot(mc,"sand-drift-8.png");rendered=WindSand.rendered();
                dust=particle(mc,true,0,114,0);dust.setLifetime(160);dustStart=dust.getPos();
                debris=mc.particleEngine.createParticle(new DustParticleOptions(new Vector3f(.9F,.7F,.4F),1),0,114,3,0,0,0);
                debris.setParticleSpeed(0,0,0);debris.setLifetime(160);debrisStart=debris.getPos();
            }
            if(ticks==195){
                check(dust.getPos().x-dustStart.x>3 && debris.getPos().x-debrisStart.x>2,"Custom sand dust and vanilla dust acquire real wind displacement");
                check(WindSand.rendered()>rendered+100,"Sand sprites rendered through the actual particle engine");
                command(mc,"windweave set west 20");
            }
            if(ticks==230){
                check(ClientWindState.ambient(mc.level).x < -19 && dust.velocity().x<-.8,"Existing dust reverses smoothly with server-synchronized wind: "+dust.velocity());
                check(debris.getPos().x<debrisStart.x,"Existing vanilla dust reverses too");
                wall=particle(mc,true,-7.3,110,-7);wall.setLifetime(120);
                wet=particle(mc,false,6.5,105.2,.5);
                grain=particle(mc,false,0,101.08,0);grain.setLifetime(120);
            }
            if(ticks==270){
                check(wall.getPos().x>-8,"Swept sand collision does not cross a solid wall");
                check(!wet.isAlive(),"Sand is removed on entering water");
                check(grain.getPos().y>=101,"Saltating grains cannot fall through the sand surface");
                check(WindSand.intensity()>.85 && WindSand.count()>200,"Strong desert wind raises a dense sandstorm: "+WindSand.count()+" intensity="+WindSand.intensity());
                check(StormFog.lastEnd()<40,"Actual terrain fog reduces visibility during sandstorm: "+StormFog.lastEnd());
                var mix = WindAudio.mix(20, WindSand.intensity(), ClientConfig.WIND_VOLUME.get().floatValue());
                check(WindAudio.playing() && WindAudio.windGain()>.02 && WindAudio.sandGain()>.01
                        && WindAudio.windGain()+WindAudio.sandGain() <= mix.wind()+mix.sand()+.01,
                        "Both ambient layers run within the shared, reduced loudness budget");
                screenshot(mc,"sandstorm-20.png");
                mc.options.particles().set(ParticleStatus.MINIMAL);
            }
            if(ticks==285){
                check(WindSand.count()<=200,"Minimal quality caps sand particle pool at 200");
                ClientConfig.SANDSTORM.set(false);
            }
            if(ticks==300){
                check(WindSand.intensity()==0 && WindSand.count()<=36,"Disabling storms removes haze and suspended storm layer while preserving surface sand");
                ClientConfig.SAND_DRIFT.set(false);ClientConfig.PARTICLE_PHYSICS.set(false);
                debris=mc.particleEngine.createParticle(new DustParticleOptions(new Vector3f(1,1,1),1),0,114,0,0,0,0);
                debris.setParticleSpeed(0,0,0);debris.setLifetime(60);debrisStart=debris.getPos();
                ClientConfig.WIND_SOUND.set(false);
            }
            if(ticks==315){
                check(WindSand.count()==0,"Both sand switches remove all sand particles");
                check(Math.abs(debris.getPos().x-debrisStart.x)<.001,"Debris switch preserves vanilla stationary dust");
                check(!WindAudio.playing() && WindAudio.windGain()==0,"Sound switch stops and releases ambient loops");
                ClientConfig.SAND_DRIFT.set(true);ClientConfig.SANDSTORM.set(true);ClientConfig.WIND_SOUND.set(true);ClientConfig.PARTICLE_PHYSICS.set(true);
                mc.options.particles().set(ParticleStatus.ALL);
                mc.reloadResourcePacks().thenRun(()->reloaded=true);
            }
            if(ticks>330 && reloaded && reloadTick==0)reloadTick=ticks;
            if(reloadTick>0 && ticks==reloadTick+100){
                check(WindSand.count()>200 && WindAudio.playing(),"Particles and audio resume after an actual resource reload");
                screenshot(mc,"sandstorm-after-reload.png");
                command(mc,"windweave calm");
            }
            if(reloadTick>0 && ticks==reloadTick+240){
                check(WindSand.count()==0 && WindSand.intensity()<.001 && WindAudio.windGain()<.005 && WindAudio.sandGain()<.01,
                        "Calm clears particles and smoothly fades both haze and audio");
                command(mc,"fillbiome -24 96 -24 24 107 24 minecraft:plains");
                command(mc,"fillbiome -24 108 -24 24 119 24 minecraft:plains");
                command(mc,"windweave set east 22");
            }
            if(reloadTick>0 && ticks==reloadTick+310){
                check(WindSand.intensity()<.001 && WindSand.count()>10,"A sandy non-desert biome has drifting sand but no desert storm");
                WindSand.clear();WindAudio.clear();
                check(WindSand.count()==0 && !WindAudio.playing(),"Explicit world/logout cleanup releases particles and sounds");
                Files.writeString(Path.of("sand-client-result.txt"),REPORT+"PASS\n");LogUtils.getLogger().info("SAND CLIENT TEST PASS\n{}",REPORT);mc.stop();
            }
            if(ticks>1000)throw new IllegalStateException("Client test timeout");
        }catch(Throwable e){
            try{Files.writeString(Path.of("sand-client-result.txt"),REPORT+"FAIL tick="+ticks+": "+e+"\n");}catch(Exception ignored){}
            LogUtils.getLogger().error("SAND CLIENT TEST FAILED at "+ticks,e);mc.stop();
        }
    }
    private static WindSandParticle particle(Minecraft mc,boolean dust,double x,double y,double z){
        return (WindSandParticle)mc.particleEngine.createParticle(dust?WeatherParticles.DUST.get():WeatherParticles.SAND.get(),x,y,z,0,0,0);
    }
    private static void command(Minecraft mc,String cmd){var server=mc.getSingleplayerServer();server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),cmd));}
    private static void screenshot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});}
    private static void check(boolean condition,String text){if(!condition)throw new IllegalStateException(text);REPORT.append("PASS: ").append(text).append('\n');}
}
