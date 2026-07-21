package com.example.companion.entity;

import com.example.companion.CompanionMod;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * All persistent state for a {@link CompanionEntity}, stored via Fabric's
 * {@link AttachmentType} so Fabric handles NBT serialization automatically.
 *
 * <p>Hunger and objective live directly in this object.
 * The inventory is synced to/from {@link net.minecraft.world.SimpleContainer}
 * in {@code addAdditionalSaveData} / {@code readAdditionalSaveData}.
 */
public class CompanionPersistentData {

    // ------------------------------------------------------------------
    // SlottedItem record
    // ------------------------------------------------------------------

    public record SlottedItem(int slot, ItemStack stack) {
        public static final Codec<SlottedItem> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Codec.INT.fieldOf("slot").forGetter(SlottedItem::slot),
                        ItemStack.CODEC.fieldOf("item").forGetter(SlottedItem::stack)
                ).apply(instance, SlottedItem::new));
    }

    // ------------------------------------------------------------------
    // Fields
    // ------------------------------------------------------------------

    private int hunger = 20;
    private String objective = "NONE";
    private List<SlottedItem> inventoryItems = new ArrayList<>();
    private String currentPlanJson = "";

    // ------------------------------------------------------------------
    // Codec + Attachment
    // ------------------------------------------------------------------

    public static final Codec<CompanionPersistentData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("hunger", 20).forGetter(d -> d.hunger),
                    Codec.STRING.optionalFieldOf("objective", "NONE").forGetter(d -> d.objective),
                    SlottedItem.CODEC.listOf().optionalFieldOf("inventory", List.of()).forGetter(d -> d.inventoryItems),
                    Codec.STRING.optionalFieldOf("current_plan", "").forGetter(d -> d.currentPlanJson)
            ).apply(instance, (hunger, objective, inventory, planJson) -> {
                CompanionPersistentData data = new CompanionPersistentData();
                data.hunger = hunger;
                data.objective = objective;
                data.inventoryItems = new ArrayList<>(inventory);
                data.currentPlanJson = planJson;
                return data;
            }));

    public static final AttachmentType<CompanionPersistentData> ATTACHMENT =
            AttachmentRegistry.create(
                    Identifier.fromNamespaceAndPath(CompanionMod.MOD_ID, "entity_data"),
                    builder -> builder.persistent(CODEC).initializer(CompanionPersistentData::new));

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    public int getHunger()                        { return hunger; }
    public void setHunger(int v)                  { this.hunger = Math.max(0, Math.min(20, v)); }

    public String getObjective()                  { return objective; }
    public void setObjective(String objective)    { this.objective = objective; }

    public List<SlottedItem> getInventoryItems()          { return inventoryItems; }
    public void setInventoryItems(List<SlottedItem> items) { this.inventoryItems = items; }

    public String getCurrentPlanJson()             { return currentPlanJson; }
    public void   setCurrentPlanJson(String json)  { this.currentPlanJson = json != null ? json : ""; }
}
