package net.spucio.orderup.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

public final class CoinParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private CoinParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites
    ) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.sprites = sprites;
        this.friction = 0.86F;
        this.gravity = 0.65F;
        this.quadSize = 0.105F + random.nextFloat() * 0.035F;
        this.lifetime = 18 + random.nextInt(10);
        this.hasPhysics = true;
        this.xd = xSpeed;
        this.yd = Math.abs(ySpeed) + 0.10D + random.nextDouble() * 0.10D;
        this.zd = zSpeed;
        this.roll = random.nextFloat() * ((float) Math.PI * 2.0F);
        this.oRoll = this.roll;
        setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        oRoll = roll;
        roll += 0.40F;
        setSpriteFromAge(sprites);
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed
        ) {
            return new CoinParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
