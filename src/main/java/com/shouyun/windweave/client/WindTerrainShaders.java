package com.shouyun.windweave.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import com.shouyun.windweave.ClientConfig;
import com.shouyun.windweave.Config;
import com.shouyun.windweave.Windweave;
import com.shouyun.windweave.wind.ClientWindState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Namespaced terrain programs: no replacement files in assets/minecraft. */
public final class WindTerrainShaders {
    private static final Map<String, ShaderInstance> SHADERS = new HashMap<>();
    private static volatile boolean ready;
    private static ClientLevel currentLevel;
    private static WindAnimationClock clock = new WindAnimationClock();
    private static long frames;
    private static long uploads;
    private static final ResourceLocation STILL_IMAGE = ResourceLocation.withDefaultNamespace("textures/block/water_still.png");
    private static final ResourceLocation FLOW_IMAGE = ResourceLocation.withDefaultNamespace("textures/block/water_flow.png");
    private static final ResourceLocation STILL_SPRITE = ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation FLOW_SPRITE = ResourceLocation.withDefaultNamespace("block/water_flow");
    private WindTerrainShaders() {}

    public static boolean available() { return ready; }
    public static long uniformUploads() { return uploads; }
    public static Vec3 visualWind() { return clock.wind(); }
    public static double animationSeconds() { return clock.seconds(); }
    public static long frameCount() { return frames; }
    public static double travelX() { return clock.travelX(); }
    public static double travelZ() { return clock.travelZ(); }
    public static void clear() { currentLevel = null; clock = new WindAnimationClock(); }

    public static void register(RegisterShadersEvent event) {
        ready = false;
        SHADERS.clear();
        // These renderers replace the vertex format/terrain pipeline. Do not send them encoded light data.
        if (java.util.List.of("embeddium", "sodium", "iris", "oculus").stream().anyMatch(ModList.get()::isLoaded)) {
            LogUtils.getLogger().info("Windweave: alternative terrain renderer detected; preserving its vertex format. Iris wind uniforms are available to adapted shader packs.");
            return;
        }
        try {
            for (String layer : java.util.List.of("solid", "cutout", "cutout_mipped", "translucent")) {
                event.registerShader(new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(Windweave.MODID, "terrain_" + layer), DefaultVertexFormat.BLOCK), shader -> {
                    SHADERS.put(layer, shader);
                    ready = SHADERS.size() == 4;
                });
            }
        } catch (IOException error) {
            throw new java.io.UncheckedIOException("Could not load Windweave terrain shaders", error);
        }
    }

    public static void beginFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != currentLevel) { clear(); currentLevel = mc.level; }
        if (mc.level == null) return;
        Vec3 target = Config.ENABLED.get() ? com.shouyun.windweave.wind.WindField.sample(mc.level,
                mc.gameRenderer.getMainCamera().getPosition()) : Vec3.ZERO;
        clock.advance(System.nanoTime(), mc.isPaused() || !mc.level.tickRateManager().runsNormally(), target);
        frames++;
    }

    public static ShaderInstance select(String layer, ShaderInstance vanilla) {
        if (!ready || Minecraft.getInstance().level == null) return vanilla;
        ShaderInstance shader = SHADERS.get(layer);
        Minecraft mc = Minecraft.getInstance();
        Vec3 smooth = clock.wind();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        shader.safeGetUniform("Wind").set((float) smooth.x, (float) smooth.z);
        shader.safeGetUniform("WindTime").set((float) clock.seconds());
        shader.safeGetUniform("WindTravel").set((float) clock.travelX(), (float) clock.travelZ());
        shader.safeGetUniform("WindCamera").set((float) camera.x, (float) camera.y, (float) camera.z);
        shader.safeGetUniform("WindEffects").set(ClientConfig.VEGETATION.get() ? 1F : 0F,
                ClientConfig.WATER.get() ? 1F : 0F, ClientConfig.FIRE.get() ? 1F : 0F);
        shader.safeGetUniform("WindShelter").set(Config.SHELTER.get() ? 1F : 0F);
        if (layer.equals("translucent")) {
            var atlas = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            waterRect(shader, "WindStillRect", atlas.apply(STILL_SPRITE));
            waterRect(shader, "WindFlowRect", atlas.apply(FLOW_SPRITE));
            // Read original vertical strips outside the atlas: shaders can blend fractional frames at render speed.
            shader.setSampler("WindStillFrames", mc.getTextureManager().getTexture(STILL_IMAGE));
            shader.setSampler("WindFlowFrames", mc.getTextureManager().getTexture(FLOW_IMAGE));
        }
        uploads++;
        return shader;
    }

    private static void waterRect(ShaderInstance shader, String uniform, TextureAtlasSprite sprite) {
        shader.safeGetUniform(uniform).set(sprite.getU0(), sprite.getV0(), sprite.getU1() - sprite.getU0(), sprite.getV1() - sprite.getV0());
    }
}
