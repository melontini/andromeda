package me.melontini.andromeda.modules.items.better_names;

import me.melontini.andromeda.base.Module;
import me.melontini.andromeda.util.annotations.config.Environment;

public class BetterNames implements Module {

    @Override
    public Environment environment() {
        return Environment.CLIENT;
    }
}
