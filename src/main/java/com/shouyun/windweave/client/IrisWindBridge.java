package com.shouyun.windweave.client;

import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.Config;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;
import org.joml.Vector3f;
import java.util.function.Supplier;
import java.util.function.DoubleSupplier;

/** Optional Iris integration. Only this adapter reflects Iris types; no hard runtime dependency. */
public final class IrisWindBridge {
    private static long registrations, reads;
    private IrisWindBridge() {}
    public static long registrations() { return registrations; }
    public static long reads() { return reads; }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void register(Object holder) {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.gl.uniform.UniformHolder");
            Class<? extends Enum> frequency = (Class<? extends Enum>) Class.forName("net.irisshaders.iris.gl.uniform.UniformUpdateFrequency");
            Object frame = Enum.valueOf(frequency, "PER_FRAME");
            var scalar = api.getMethod("uniform1f", frequency, String.class, DoubleSupplier.class);
            var vec2 = api.getMethod("uniform2f", frequency, String.class, Supplier.class);
            var vec3 = api.getMethod("uniform3f", frequency, String.class, Supplier.class);
            scalar.invoke(holder, frame, "windweaveActive", (DoubleSupplier) () -> {
                reads++;
                return Minecraft.getInstance().level != null ? 1 : 0;
            });
            scalar.invoke(holder, frame, "windweaveTime", (DoubleSupplier) WindTerrainShaders::animationSeconds);
            vec3.invoke(holder, frame, "windweaveWind", (Supplier<Vector3f>) () -> {
                var w = WindTerrainShaders.visualWind();
                return new Vector3f((float) w.x, (float) w.y, (float) w.z);
            });
            vec2.invoke(holder, frame, "windweaveTravel", (Supplier<Vector2f>) () ->
                    new Vector2f((float) WindTerrainShaders.travelX(), (float) WindTerrainShaders.travelZ()));
            vec3.invoke(holder, frame, "windweaveEffects", (Supplier<Vector3f>) () -> {
                boolean active = Config.ENABLED.get();
                return new Vector3f(active && ClientConfig.VEGETATION.get() ? 1 : 0,
                        active && ClientConfig.WATER.get() ? 1 : 0, active && ClientConfig.FIRE.get() ? 1 : 0);
            });
            registrations++;
        } catch (ReflectiveOperationException error) {
            // An incompatible Iris API must be visible; silently returning would look like working compatibility.
            throw new IllegalStateException("Windweave could not register the Iris wind uniforms", error);
        }
    }
}
