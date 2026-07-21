package com.example.companion.client;

import com.example.companion.entity.CompanionEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.zombie.ZombieModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity, ZombieRenderState, ZombieModel<ZombieRenderState>> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/zombie/zombie.png");

    public CompanionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ZombieModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
    }

    @Override
    public ZombieRenderState createRenderState() {
        return new ZombieRenderState();
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return TEXTURE;
    }
}
