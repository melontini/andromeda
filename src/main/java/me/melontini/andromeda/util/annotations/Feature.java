package me.melontini.andromeda.util.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Feature {

    String[] value(); //The options need to be provided in a hierarchy. e.g. "betterFurnaceMinecart", "furnaceMinecartTakeFuelWhenLow"
}