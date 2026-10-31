package com.shouyun.windweave;

import com.shouyun.windweave.entity.WindIndicatorEntity;
import com.shouyun.windweave.particle.WeatherParticles;
import com.shouyun.windweave.network.WindPayload;
import com.shouyun.windweave.network.FlowPayload;
import com.shouyun.windweave.wind.ClientWindState;
import com.shouyun.windweave.wind.WindEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(Windweave.MODID)
public class Windweave {
    public static final String MODID = "windweave";
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredHolder<EntityType<?>, EntityType<WindIndicatorEntity>> WIND_INDICATOR =
            ENTITIES.register("wind_indicator", () -> EntityType.Builder
                    .of(WindIndicatorEntity::new, MobCategory.MISC)
                    .sized(0.5F, 1.8F).clientTrackingRange(8).updateInterval(5)
                    .fireImmune().build("windweave:wind_indicator"));

    public Windweave(IEventBus modBus, ModContainer container) {
        ENTITIES.register(modBus);
        com.shouyun.windweave.device.WindDevices.register(modBus);
        WeatherParticles.TYPES.register(modBus);
        com.shouyun.windweave.sound.WindSounds.EVENTS.register(modBus);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(WindEvents.class);
        NeoForge.EVENT_BUS.addListener(com.shouyun.windweave.wind.BodyWind::tick);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Handler/cache reference no client-only Minecraft classes.
        event.registrar("6").playToClient(WindPayload.TYPE, WindPayload.STREAM_CODEC,
                (payload, context) -> ClientWindState.accept(payload));
        event.registrar("6").playToClient(FlowPayload.TYPE, FlowPayload.STREAM_CODEC,
                (payload, context) -> ClientWindState.setFlowEnabled(payload.enabled()));
        event.registrar("6").playToClient(com.shouyun.windweave.network.BodyWindPayload.TYPE,
                com.shouyun.windweave.network.BodyWindPayload.STREAM_CODEC, (payload, context) -> {
                    var player = context.player();
                    if (player.level().dimension().location().equals(payload.dimension()) && player.isAlive()
                            && !player.isSpectator() && !player.getAbilities().flying
                            && !player.isPassenger() && !player.isSleeping() && !player.isInWaterOrBubble() && !player.isInLava())
                        player.setDeltaMovement(player.getDeltaMovement().add(payload.impulse()));
                });
    }
}
