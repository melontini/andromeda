package me.melontini.andromeda.modules.world.quick_fire;

import me.melontini.andromeda.base.Module;
import me.melontini.andromeda.util.annotations.config.Environment;

public class QuickFire implements Module {
    @Override
    public Environment environment() {
        return Environment.SERVER;
    }
}
