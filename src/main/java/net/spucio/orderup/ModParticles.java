package net.spucio.orderup;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, OrderUp.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> COIN = PARTICLE_TYPES.register(
            "coin",
            () -> new SimpleParticleType(false)
    );

    public static void register(IEventBus bus) {
        PARTICLE_TYPES.register(bus);
    }
}
