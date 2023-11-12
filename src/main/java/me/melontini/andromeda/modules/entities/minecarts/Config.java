package me.melontini.andromeda.modules.entities.minecarts;

import me.melontini.andromeda.config.BasicConfig;
import me.melontini.andromeda.util.annotations.config.Environment;
import me.melontini.andromeda.util.annotations.config.FeatureEnvironment;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class Config extends BasicConfig {

    @ConfigEntry.Gui.Tooltip
    @FeatureEnvironment(Environment.BOTH)
    public boolean isAnvilMinecartOn = false;

    @ConfigEntry.Gui.Tooltip
    @FeatureEnvironment(Environment.BOTH)
    public boolean isNoteBlockMinecartOn = false;

    @ConfigEntry.Gui.Tooltip
    @FeatureEnvironment(Environment.BOTH)
    public boolean isJukeboxMinecartOn = false;
}
