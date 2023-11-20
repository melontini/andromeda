package me.melontini.andromeda.mixin.entities.villagers_follow_emeralds;

import me.melontini.andromeda.base.ModuleManager;
import me.melontini.andromeda.modules.entities.villagers_follow_emeralds.VillagerTemptGoal;
import me.melontini.andromeda.modules.entities.villagers_follow_emeralds.VillagersFollowEmeralds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.village.VillagerType;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
abstract class VillagerEntityMixin extends MerchantEntity {
    @Unique
    private static final VillagersFollowEmeralds am$vfeb = ModuleManager.quick(VillagersFollowEmeralds.class);

    public VillagerEntityMixin(EntityType<? extends MerchantEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/passive/VillagerEntity;setVillagerData(Lnet/minecraft/village/VillagerData;)V", shift = At.Shift.AFTER), method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;Lnet/minecraft/village/VillagerType;)V")
    private void andromeda$init(EntityType<? extends VillagerEntity> entityType, World world, VillagerType type, CallbackInfo ci) {
        if (am$vfeb.config().enabled)
            this.goalSelector.add(6, new VillagerTemptGoal((VillagerEntity) (Object) this, 0.5, Ingredient.ofItems(Items.EMERALD_BLOCK), false));
    }
}
