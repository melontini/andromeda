package me.melontini.andromeda.modules.world.auto_planting.mixin;


import me.melontini.andromeda.base.ModuleManager;
import me.melontini.andromeda.common.conflicts.CommonRegistries;
import me.melontini.andromeda.modules.world.auto_planting.AutoPlanting;
import net.minecraft.block.PlantBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Unique
    private static final AutoPlanting am$tbpgs = ModuleManager.quick(AutoPlanting.class);

    @Shadow public abstract ItemStack getStack();

    @Unique
    private final Random andromeda$random = new Random();

    @Inject(at = @At("HEAD"), method = "tick")
    public void andromeda$tryPlant(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        ItemStack stack = this.getStack();
        BlockPos pos = entity.getBlockPos();
        World world = entity.getWorld();

        if (!world.isClient()) {
            AutoPlanting.Config config = world.am$get(am$tbpgs);
            if (!config.enabled) return;
            if (entity.age % andromeda$random.nextInt(20, 101) == 0) {
                if (stack.getItem() instanceof BlockItem) {
                    if (((BlockItem) stack.getItem()).getBlock() instanceof PlantBlock) {
                        if (world.getFluidState(pos).isEmpty()) {
                            if (config.blacklistMode == config.idList.contains(CommonRegistries.items().getId(stack.getItem()).toString()))
                                return;

                            ((BlockItem) stack.getItem()).place(new ItemPlacementContext(world, null, null, stack, world.raycast(
                                    new RaycastContext(
                                            new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                                            new Vec3d(pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5),
                                            RaycastContext.ShapeType.COLLIDER,
                                            RaycastContext.FluidHandling.ANY,
                                            entity
                                    )
                            )));
                        }
                    }
                }
            }
        }
    }
}
