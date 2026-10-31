package com.shouyun.windweave.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.shouyun.windweave.wind.WindField;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** Swept collision, drag, settling and saltation; grains keep their inertia when wind reverses. */
public final class WindSandParticle extends TextureSheetParticle {
    private final boolean dust, storm;
    private final double phase;
    private final float baseSize;
    private int bounces;
    private boolean natural;
    private float windOpacity = 1;
    private Vec3 wind = Vec3.ZERO;
    private WindSandParticle(ClientLevel level, double x, double y, double z, boolean dust, boolean red, boolean storm, SpriteSet sprites) {
        super(level, x, y, z);
        this.dust = dust; this.storm = storm;
        pickSprite(sprites); setSize(.035F, .035F);
        baseSize = quadSize = dust ? (storm ? .6F : .32F) + random.nextFloat() * .38F : .018F + random.nextFloat() * .027F;
        lifetime = dust ? 65 + random.nextInt(50) : 24 + random.nextInt(30);
        phase = random.nextDouble() * 6.283;
        rCol = red ? .78F : .86F; gCol = red ? .38F : .73F; bCol = red ? .18F : .48F;
        if (!dust) { rCol *= .8F; gCol *= .8F; bCol *= .8F; }
        wind = WindField.sample(level, getPos()).scale(.05);
        xd = wind.x * .3; zd = wind.z * .3; yd = dust ? .015 : .06 + random.nextDouble() * .055;
        alpha = 0;
        if (!WindSand.track(this)) remove();
    }
    void natural() { natural = true; }
    public boolean storm() { return storm; }
    public Vec3 velocity() { return new Vec3(xd, yd, zd); }
    @Override public void tick() {
        xo = x; yo = y; zo = z; oRoll = roll;
        BlockPos pos = BlockPos.containing(x, y, z);
        if (++age >= lifetime || !WindSand.enabled(storm) || !level.hasChunkAt(pos)
                || y < level.getMinBuildHeight() || !level.getFluidState(pos).isEmpty()) { remove(); return; }
        if (age == 1 || age % 3 == 0) wind = WindField.sample(level, getPos()).scale(.05);
        double response = dust ? .14 : .08;
        double factor = dust ? .95 : .65;
        xd += (wind.x * factor - xd) * response; zd += (wind.z * factor - zd) * response;
        double turbulence = Math.min(1, wind.length()) * (dust ? .028 : .008);
        yd = dust ? yd + ((storm ? -.008 : -.018) + Math.sin(age * .13 + phase) * turbulence - yd) * .12 : yd - .018;
        Vec3 wanted = new Vec3(xd, yd, zd);
        BlockPos next = BlockPos.containing(x + xd, y + yd, z + zd);
        if (!level.hasChunkAt(next)) { remove(); return; }
        Vec3 actual = Entity.collideBoundingBox(null, wanted, getBoundingBox(), level, List.of());
        setPos(x + actual.x, y + actual.y, z + actual.z);
        if (Math.abs(actual.x - xd) > 1E-6) xd = 0;
        if (Math.abs(actual.z - zd) > 1E-6) zd = 0;
        if (Math.abs(actual.y - yd) > 1E-6) {
            if (!dust && yd < 0 && wind.length() > .15 && ++bounces <= 4) {
                yd = .04 + random.nextDouble() * .065; xd *= .68; zd *= .68;
            } else { remove(); return; }
        }
        if (!level.getFluidState(BlockPos.containing(x, y, z)).isEmpty()) { remove(); return; }
        float fade = Math.min(1, age / 7F) * Mth.clamp((lifetime - age) / 15F, 0, 1);
        if (natural) {
            windOpacity += ((float) Mth.clamp(wind.length() / .18, 0, 1) - windOpacity) * .08F;
            fade *= windOpacity;
        }
        alpha = (dust ? (storm ? .17F : .20F) : .85F) * fade;
        quadSize = baseSize * (dust ? 1 + age * .008F : 1);
        roll += dust ? .003F : .08F;
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    @Override public void render(VertexConsumer out, Camera camera, float partial) {
        if (dust) super.render(out, camera, partial);
        else {
            Vec3 center = new Vec3(Mth.lerp(partial, xo, x), Mth.lerp(partial, yo, y), Mth.lerp(partial, zo, z)).subtract(camera.getPosition());
            Vec3 direction = new Vec3(xd, 0, zd);
            double speed = direction.length();
            if (speed < .001) direction = new Vec3(1, 0, 0); else direction = direction.scale(1 / speed);
            Vec3 length = direction.scale(quadSize + Math.min(.18, speed * .35));
            int light = getLightColor(partial);
            // Short world-space motion streaks make saltation readable against the sand.
            quad(out, center, length, new Vec3(0, quadSize * .65, 0), light);
            quad(out, center, length, new Vec3(-direction.z, 0, direction.x).scale(quadSize * .65), light);
        }
        WindSand.renderedParticle();
    }
    private void quad(VertexConsumer out, Vec3 center, Vec3 a, Vec3 b, int light) {
        vertex(out, center.subtract(a).subtract(b), getU0(), getV1(), light);
        vertex(out, center.subtract(a).add(b), getU0(), getV0(), light);
        vertex(out, center.add(a).add(b), getU1(), getV0(), light);
        vertex(out, center.add(a).subtract(b), getU1(), getV1(), light);
    }
    private void vertex(VertexConsumer out, Vec3 pos, float u, float v, int light) {
        out.addVertex((float) pos.x, (float) pos.y, (float) pos.z).setUv(u,v).setColor(rCol,gCol,bCol,alpha).setLight(light);
    }
    public record Provider(boolean dust, SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new WindSandParticle(level, x, y, z, dust, dx > .5, dy > .5, sprites);
        }
    }
}
