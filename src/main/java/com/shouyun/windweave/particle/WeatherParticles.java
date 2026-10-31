package com.shouyun.windweave.particle;

import com.shouyun.windweave.Windweave;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registry definitions are safe to load on a dedicated server. Weather simulation is client-only. */
public final class WeatherParticles {
    public static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, Windweave.MODID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RAIN = TYPES.register("rain", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SNOW = TYPES.register("snowflake", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SAND = TYPES.register("sand_grain", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST = TYPES.register("sand_dust", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPRAY = TYPES.register("storm_spray", () -> new SimpleParticleType(false));
    private WeatherParticles() {}
}
