package net.spucio.orderup.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A restaurant-XP orb rendered as a soft, pastel particle. The three speed
 * arguments are intentionally interpreted as a vector from the spawn point to
 * the collection point. This lets the server send one compact particle packet
 * while the client animates a smooth arcing flight.
 */
public final class RestaurantXpParticle extends TextureSheetParticle {
    private static final int[] PALETTE = {
            0xFFFFFF,
            0xFFD6E7,
            0xFFDFFF
    };

    private final SpriteSet sprites;
    private final double startX;
    private final double startY;
    private final double startZ;
    private final double targetX;
    private final double targetY;
    private final double targetZ;
    private final double sidewaysX;
    private final double sidewaysZ;
    private final double arcHeight;
    private final float colourPhase;

    private RestaurantXpParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double targetOffsetX,
            double targetOffsetY,
            double targetOffsetZ,
            SpriteSet sprites
    ) {
        super(level, x, y, z, 0.0D, 0.0D, 0.0D);
        this.sprites = sprites;
        this.startX = x;
        this.startY = y;
        this.startZ = z;
        this.targetX = x + targetOffsetX;
        this.targetY = y + targetOffsetY;
        this.targetZ = z + targetOffsetZ;
        this.sidewaysX = (random.nextDouble() - 0.5D) * 0.55D;
        this.sidewaysZ = (random.nextDouble() - 0.5D) * 0.55D;
        this.arcHeight = 0.55D + random.nextDouble() * 0.35D;
        this.colourPhase = random.nextFloat() * PALETTE.length;

        this.lifetime = 26;
        this.quadSize = 0.145F + random.nextFloat() * 0.055F;
        this.gravity = 0.0F;
        this.friction = 1.0F;
        this.hasPhysics = false;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        setSpriteFromAge(sprites);
        updateColour();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;

        if (age++ >= lifetime) {
            remove();
            return;
        }

        float progress = age / (float) lifetime;
        float eased = progress * progress * (3.0F - 2.0F * progress);
        double curve = Math.sin(Math.PI * progress);

        setPos(
                Mth.lerp(eased, startX, targetX) + sidewaysX * curve,
                Mth.lerp(eased, startY, targetY) + arcHeight * curve,
                Mth.lerp(eased, startZ, targetZ) + sidewaysZ * curve
        );

        oRoll = roll;
        roll += 0.22F;
        quadSize *= 0.996F;
        alpha = progress > 0.88F ? Mth.clamp((1.0F - progress) / 0.12F, 0.0F, 1.0F) : 0.96F;
        setSpriteFromAge(sprites);
        updateColour();
    }

    private void updateColour() {
        float cycle = (age / 8.0F + colourPhase) % PALETTE.length;
        int firstIndex = Mth.floor(cycle);
        int secondIndex = (firstIndex + 1) % PALETTE.length;
        float mix = cycle - firstIndex;
        mix = mix * mix * (3.0F - 2.0F * mix);

        int first = PALETTE[firstIndex];
        int second = PALETTE[secondIndex];
        float red = Mth.lerp(mix, (first >> 16 & 255) / 255.0F, (second >> 16 & 255) / 255.0F);
        float green = Mth.lerp(mix, (first >> 8 & 255) / 255.0F, (second >> 8 & 255) / 255.0F);
        float blue = Mth.lerp(mix, (first & 255) / 255.0F, (second & 255) / 255.0F);
        setColor(red, green, blue);
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
            return new RestaurantXpParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
