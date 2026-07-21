package com.example.companion;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.example.companion.client.CompanionRenderer;
import net.minecraft.client.renderer.entity.EntityRenderers;

@Environment(EnvType.CLIENT)
public class CompanionModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        EntityRenderers.register(CompanionMod.COMPANION_ENTITY_TYPE, CompanionRenderer::new);
    }
}
