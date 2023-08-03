package me.melontini.andromeda.entity.vehicle.boats;

import me.melontini.andromeda.registries.EntityTypeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.Item;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.World;

public class ChestBoatEntity extends StorageBoatEntity {
    public ChestBoatEntity(EntityType<? extends BoatEntity> entityType, World world) {
        super(entityType, world);
    }

    public ChestBoatEntity(World world, double x, double y, double z) {
        this(EntityTypeRegistry.BOAT_WITH_CHEST, world);
        this.setPosition(x, y, z);
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
    }

    @Override
    public ScreenHandler getScreenHandler(int syncId, PlayerInventory playerInventory) {
        return GenericContainerScreenHandler.createGeneric9x3(syncId, playerInventory, this);
    }

    @Override
    public int size() {
        return 27;
    }

    @Override
    public Item asItem() {
        return Registry.ITEM.get(Identifier.tryParse("andromeda:" + this.getBoatType().getName() + "_boat_with_chest"));
    }
}