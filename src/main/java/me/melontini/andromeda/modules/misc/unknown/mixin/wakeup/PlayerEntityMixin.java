package me.melontini.andromeda.modules.misc.unknown.mixin.wakeup;

import me.melontini.andromeda.common.util.WorldUtil;
import me.melontini.dark_matter.api.minecraft.data.NbtBuilder;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(PlayerEntity.class)
abstract class PlayerEntityMixin {

    @Shadow public abstract void playSound(SoundEvent event, SoundCategory category, float volume, float pitch);

    @Inject(at = @At("HEAD"), method = "wakeUp(ZZ)V")
    private void andromeda$wakeUp(boolean skipSleepTimer, boolean updateSleepingPlayers, CallbackInfo ci) {
        PlayerEntity player = (PlayerEntity) (Object) this;

        if (!player.world.isClient) if (player.world.getRandom().nextInt(100000) == 0) {
            Optional<BlockPos> optional = WorldUtil.pickRandomSpot(player.world, player.getBlockPos(), 10, player.world.getRandom());
            if (optional.isPresent()) {
                BlockPos pos = optional.get();
                ArmorStandEntity stand = new ArmorStandEntity(player.world, pos.getX(), pos.getY(), pos.getZ());
                ItemStack stack = new ItemStack(Items.PLAYER_HEAD);

                stack.setNbt(NbtBuilder.create().putString("SkullOwner", player.getDisplayName().getString()).build());

                stand.equipStack(EquipmentSlot.HEAD, stack);
                player.world.spawnEntity(stand);
                playSound(SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 4, 1);
            }
        }
    }
}
