package me.melontini.andromeda.modules.entities.snowball_tweaks;

import me.melontini.andromeda.config.BasicConfig;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

public class Config extends BasicConfig {

    @ConfigEntry.Gui.Tooltip
    public boolean freeze = true;

    @ConfigEntry.Gui.Tooltip
    public boolean extinguish = true;

    @ConfigEntry.Gui.Tooltip
    public boolean melt = true;

    @ConfigEntry.Gui.Tooltip
    public boolean layers = false;

    public boolean enableCooldown = true;

    @ConfigEntry.Gui.Tooltip
    public int cooldown = 10;
}