package com.shouyun.windweave.client;

import com.shouyun.windweave.wind.WindField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;

/** Short, translucent ground spray; real world coordinates and ordinary block collision. */
public final class StormSprayParticle extends TextureSheetParticle {
    private final float initialSize;
    private StormSprayParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z); pickSprite(sprites);
        lifetime = 20 + random.nextInt(16); initialSize = .25F + random.nextFloat() * .25F;
        quadSize = initialSize; setSize(.1F, .1F); rCol = .73F; gCol = .82F; bCol = .88F;
        var wind = WindField.sample(level, getPos()).scale(1.0 / 20);
        xd = wind.x * .7; zd = wind.z * .7; yd = .025;
        if (!WindWeather.trackSpray(this)) remove();
    }
    @Override public void tick() {
        xo = x; yo = y; zo = z;
        if (++age >= lifetime || level.getRainLevel(1) < .01 || !com.shouyun.windweave.ClientConfig.STORM_SPRAY.get()) { remove(); return; }
        var wind = WindField.sample(level, getPos()).scale(1.0 / 20);
        xd += (wind.x * .7 - xd) * .12; zd += (wind.z * .7 - zd) * .12;
        move(xd, yd, zd); yd *= .97;
        alpha = .28F * (float) Math.sin(Math.PI * age / lifetime);
        quadSize = initialSize * (1 + age * .018F);
    }
    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new StormSprayParticle(level, x, y, z, sprites);
        }
    }
}
