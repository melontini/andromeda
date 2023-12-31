package me.melontini.andromeda.modules.mechanics.dragon_fight.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import me.melontini.andromeda.base.ModuleManager;
import me.melontini.andromeda.modules.mechanics.dragon_fight.DragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net/minecraft/world/gen/feature/EndSpikeFeature$SpikeCache")
abstract class SpikeCacheMixin {

    @ModifyExpressionValue(method = "load(Ljava/lang/Long;)Ljava/util/List;", at = @At(value = "CONSTANT", args = "intValue=76"))
    private int andromeda$modifySpikeSize(int size) {
        if (ModuleManager.quick(DragonFight.class).config().shorterSpikes) return 72;
        return size;
    }

    @ModifyExpressionValue(method = "load(Ljava/lang/Long;)Ljava/util/List;", at = @At(value = "CONSTANT", args = "intValue=3", ordinal = 1))
    private int andromeda$modifySpikeHeight(int size) {
        if (ModuleManager.quick(DragonFight.class).config().shorterSpikes) return 2;
        return size;
    }
}
