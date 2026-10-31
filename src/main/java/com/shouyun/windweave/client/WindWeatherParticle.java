package com.shouyun.windweave.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shouyun.windweave.wind.WindField;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Crossed world-space planes form a volume; never a camera-facing weather sheet. */
public final class WindWeatherParticle extends TextureSheetParticle {
    private final boolean snow;
    private final double fallSpeed, phase;
    private final Quaternionf orientation;
    private final float spin;
    private final int variant;
    private final float tumble;
    private final double flutter;
    private Vec3 localWind;
    private boolean natural;

    private WindWeatherParticle(ClientLevel level, double x, double y, double z, boolean snow, SpriteSet sprites) {
        super(level, x, y, z);
        this.snow = snow;
        variant = snow ? random.nextInt(6) : 0;
        if (snow) setSprite(sprites.get(variant, 5)); else pickSprite(sprites);
        setSize(snow ? .065F : .025F, snow ? .065F : .04F);
        quadSize = snow ? switch (variant) {
            case 0, 1 -> .065F + random.nextFloat() * .05F;
            case 2 -> .045F + random.nextFloat() * .035F;
            case 3 -> .075F + random.nextFloat() * .055F;
            case 4 -> .03F + random.nextFloat() * .025F;
            default -> .095F + random.nextFloat() * .045F;
        } : .019F + random.nextFloat() * .004F;
        lifetime = snow ? 240 : 70;
        phase = random.nextDouble() * Math.PI * 2;
        spin = (random.nextBoolean() ? 1 : -1) * (.012F + random.nextFloat() * .065F);
        tumble = .005F + random.nextFloat() * .025F;
        flutter = .004 + random.nextDouble() * .014;
        orientation = new Quaternionf().rotationXYZ(random.nextFloat() * 6.28F, random.nextFloat() * 6.28F, random.nextFloat() * 6.28F);
        fallSpeed = snow ? .035 + random.nextDouble() * .075 + (variant == 4 ? .025 : 0) : .65 + random.nextDouble() * .25;
        localWind = WindField.sample(level, getPos()).scale(1.0 / 20);
        xd = localWind.x; zd = localWind.z; yd = -fallSpeed;
        rCol = snow ? .91F + random.nextFloat() * .09F : .73F;
        gCol = snow ? .95F + random.nextFloat() * .05F : .84F; bCol = 1;
        if (!WindWeather.track(this)) remove();
    }

    void natural() { natural = true; }
    public boolean snow() { return snow; }
    public int variant() { return variant; }
    public float halfWidth() { return quadSize; }
    public Vec3 velocity() { return new Vec3(xd, yd, zd); }

    @Override public void tick() {
        xo = x; yo = y; zo = z; oRoll = roll;
        if (++age >= lifetime || !WindWeather.enabled(snow ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN)
                || (natural && level.getRainLevel(1) <= .001F)) { remove(); return; }
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(pos) || y < level.getMinBuildHeight() || !level.getFluidState(pos).isEmpty()) { remove(); return; }
        // Resample at each actual position, so particles entering wind shadows use the same field as smoke.
        localWind = WindField.sample(level, getPos()).scale(1.0 / 20);
        double response = snow ? .16 : .24;
        xd += (localWind.x - xd) * response;
        zd += (localWind.z - zd) * response;
        yd = -fallSpeed * (snow ? 1 + .15 * Math.sin(age * .08 + phase) : 1);
        double flutterScale = flutter * (1 + Math.min(1.5, localWind.length()));
        double flutterX = snow ? Math.sin(age * (.07 + tumble) + phase) * flutterScale : 0;
        double flutterZ = snow ? Math.cos(age * (.05 + tumble) + phase) * flutterScale : 0;
        double intendedX = xd + flutterX, intendedZ = zd + flutterZ;
        move(intendedX, yd, intendedZ);
        // Particle.move uses swept block collision shapes, preventing tunnelling through roofs or walls.
        if (onGround || Math.abs(x - xo - intendedX) > 1E-5 || Math.abs(z - zo - intendedZ) > 1E-5
                || !level.getFluidState(BlockPos.containing(x, y, z)).isEmpty()) remove();
        roll += spin;
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @Override public void render(VertexConsumer buffer, Camera camera, float partial) {
        Vec3 center = new Vec3(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z)).subtract(camera.getPosition());
        double distance = center.length();
        float fade = (float) (Math.min(1, age / 4.0) * Mth.clamp((30 - distance) / 8, 0, 1)
                * Mth.clamp((distance - .25) / 1.5, 0, 1));
        if (natural) fade *= level.getRainLevel(partial);
        if (fade <= .001F) return;
        float storm = WindWeather.stormStrength(level, partial);
        alpha = (snow ? .9F : .72F + storm * .16F) * fade;
        int light = getLightColor(partial);
        if (!snow && storm > .3F) {
            float daylight = Mth.clamp(Mth.cos(level.getTimeOfDay(partial) * Mth.TWO_PI) * 2 + .5F, 0, 1);
            int sky = Math.max((light >> 20) & 15, Math.round(5 + 7 * daylight));
            light = (light & ~(15 << 20)) | (sky << 20);
        }
        if (snow) {
            Quaternionf rotation = new Quaternionf(orientation).rotateY(Mth.lerp(partial, oRoll, roll))
                    .rotateX((age + partial) * tumble).rotateZ((float) Math.sin((age + partial) * .045 + phase) * .35F);
            Vec3 a = rotated(rotation, quadSize, 0, 0), b = rotated(rotation, 0, quadSize, 0), c = rotated(rotation, 0, 0, quadSize);
            quad(buffer, center, a, b, light);
            quad(buffer, center, a, c, light);
            quad(buffer, center, b, c, light);
            if (variant == 5) {
                Vec3 offset = a.scale(.75).add(b.scale(.35));
                quad(buffer, center.add(offset), a.scale(.58), c.scale(.58), light);
                quad(buffer, center.subtract(offset), b.scale(.55), c.scale(.55), light);
            }
            WindWeather.rendered(variant == 5 ? 5 : 3);
        } else {
            Vec3 axis = velocity().normalize();
            Vec3 across = axis.cross(new Vec3(0, 1, 0));
            if (across.lengthSqr() < 1E-6) across = new Vec3(1, 0, 0);
            across = across.normalize().scale(quadSize * (1 + storm * .35));
            Vec3 other = axis.cross(across).normalize().scale(quadSize * (1 + storm * .35));
            Vec3 length = axis.scale(.25 + fallSpeed * .15 + storm * Math.min(.7, velocity().length() * .22));
            quad(buffer, center, across, length, light);
            quad(buffer, center, other, length, light);
            WindWeather.rendered(2);
        }
    }

    private static Vec3 rotated(Quaternionf rotation, float x, float y, float z) {
        Vector3f v = new Vector3f(x, y, z).rotate(rotation);
        return new Vec3(v.x, v.y, v.z);
    }
    private void quad(VertexConsumer out, Vec3 center, Vec3 a, Vec3 b, int light) {
        vertex(out, center.subtract(a).subtract(b), getU0(), getV1(), light);
        vertex(out, center.subtract(a).add(b), getU0(), getV0(), light);
        vertex(out, center.add(a).add(b), getU1(), getV0(), light);
        vertex(out, center.add(a).subtract(b), getU1(), getV1(), light);
    }
    private void vertex(VertexConsumer out, Vec3 pos, float u, float v, int light) {
        out.addVertex((float) pos.x, (float) pos.y, (float) pos.z).setUv(u, v).setColor(rCol, gCol, bCol, alpha).setLight(light);
    }

    public record Provider(boolean snow, SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            // Incoming command speeds do not replace the authoritative synchronized wind field.
            return new WindWeatherParticle(level, x, y, z, snow, sprites);
        }
    }
}
