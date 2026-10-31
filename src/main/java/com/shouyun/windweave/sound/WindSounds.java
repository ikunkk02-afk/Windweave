package com.shouyun.windweave.sound;

import com.shouyun.windweave.Windweave;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class WindSounds {
    public static final DeferredRegister<SoundEvent> EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, Windweave.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> WIND = register("wind_loop");
    public static final DeferredHolder<SoundEvent, SoundEvent> SAND = register("sandstorm_loop");
    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Windweave.MODID, name)));
    }
    private WindSounds() {}
}
