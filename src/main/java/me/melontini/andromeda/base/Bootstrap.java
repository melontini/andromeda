package me.melontini.andromeda.base;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.CustomLog;
import me.melontini.andromeda.base.config.Config;
import me.melontini.andromeda.common.Andromeda;
import me.melontini.andromeda.common.client.AndromedaClient;
import me.melontini.andromeda.util.ClassPath;
import me.melontini.andromeda.util.CommonValues;
import me.melontini.andromeda.util.CrashHandler;
import me.melontini.andromeda.util.Debug;
import me.melontini.andromeda.util.exceptions.AndromedaException;
import me.melontini.andromeda.util.mixin.AndromedaMixins;
import me.melontini.dark_matter.api.base.util.EntrypointRunner;
import me.melontini.dark_matter.api.base.util.classes.ThrowingRunnable;
import me.melontini.dark_matter.api.crash_handler.Crashlytics;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bootstrap is responsible for bootstrapping the bulk of Andromeda.
 * <p> This includes, but not limited to: <br/>
 * <ul>
 *     <li>Discovering modules.</li>
 *     <li>Constructing the {@link ModuleManager}.</li>
 *     <li>Injecting mixin configs. {@link MixinProcessor}</li>
 *     <li>Running module entrypoints.</li>
 *     <li>Performing basic module verification.</li>
 * </ul>
 */
@CustomLog
public class Bootstrap {

    static ModuleManager INSTANCE;
    static volatile Status STATUS = Status.PRE_INIT;

    @Environment(EnvType.CLIENT)
    public static void onClient() {
        updateStatus(Status.CLIENT);

        if (Debug.hasKey(Debug.Keys.VERIFY_MIXINS))
            MixinEnvironment.getCurrentEnvironment().audit();

        for (Module<?> module : ModuleManager.get().loaded()) {
            AndromedaException.run(module::onClient, () ->
                    new AndromedaException.Builder().message("Failed to execute Module.onClient!").add("module", module.meta().id()));
        }
        AndromedaClient.init();
    }

    @Environment(EnvType.SERVER)
    public static void onServer() {
        updateStatus(Status.SERVER);

        if (Debug.hasKey(Debug.Keys.VERIFY_MIXINS))
            MixinEnvironment.getCurrentEnvironment().audit();

        for (Module<?> module : ModuleManager.get().loaded()) {
            AndromedaException.run(module::onServer, () ->
                    new AndromedaException.Builder().message("Failed to execute Module.onServer!").add("module", module.meta().id()));
        }
    }

    public static void onMain() {
        updateStatus(Status.MAIN);
        if (Mixins.getUnvisitedCount() > 0) {
            for (org.spongepowered.asm.mixin.transformer.Config config : Mixins.getConfigs()) {
                if (!config.isVisited() && config.getName().startsWith("andromeda_dynamic$$"))
                    throw new AndromedaException.Builder()
                            .message("Mixin failed to consume Andromeda's late configs!")
                            .add("mixin_config", config.getName())
                            .build();
            }
        }

        for (Module<?> module : ModuleManager.get().loaded()) {
            AndromedaException.run(module::onMain, () ->
                    new AndromedaException.Builder().message("Failed to execute Module.onMain!").add("module", module.meta().id()));
        }

        Andromeda.init();
    }

    public static void onPreLaunch() {
        LOGGER.info("Andromeda({}) on {}({})", CommonValues.version(), CommonValues.platform(), CommonValues.platform().version());

        Crashlytics.addHandler("andromeda", CrashHandler::handleCrash);

        AtomicReference<JsonObject> oldCfg = new AtomicReference<>();
        var oldCfgPath = FabricLoader.getInstance().getConfigDir().resolve("andromeda.json");
        if (Files.exists(oldCfgPath)) {
            if (!Files.exists(CommonValues.configPath())) {
                wrapIO(() -> {
                    oldCfg.set(JsonParser.parseReader(Files.newBufferedReader(oldCfgPath)).getAsJsonObject());
                    Files.createDirectories(CommonValues.configPath().getParent());
                    Files.move(oldCfgPath, CommonValues.configPath());
                }, "Couldn't rename pre-1.0.0 config!");
            } else {
                wrapIO(() -> Files.delete(oldCfgPath), "Couldn't delete pre-1.0.0 config!");
            }
        }

        Config.load();

        updateStatus(Status.DISCOVERY);
        List<Module<?>> list = new ArrayList<>(40);
        AndromedaException.run(() -> {
            //This should probably be removed.
            ServiceLoader.load(Module.class).stream().map(ServiceLoader.Provider::get).forEach(list::add);
            EntrypointRunner.run("andromeda:modules", ModuleManager.ModuleSupplier.class, s -> list.addAll(s.get()));
        }, () -> new AndromedaException.Builder().message("Failed during module discovery!"));

        if (list.isEmpty()) {
            LOGGER.error("Andromeda couldn't discover any modules! This should not happen!");
        }

        list.removeIf(m -> (m.meta().environment() == me.melontini.andromeda.base.Environment.CLIENT && CommonValues.environment() == EnvType.SERVER));

        ModuleManager m;
        try {
            m = new ModuleManager(list, oldCfg.get());
        } catch (Throwable t) {//Manager constructor does a lot of heavy-lifting, so we want to catch any errors.
            throw new AndromedaException.Builder()
                    .cause(t).message("Failed to initialize ModuleManager!!!")
                    .build();
        }
        m.print();
        //Scan for mixins.
        m.loaded().forEach(module -> getModuleClassPath().addUrl(module.getClass().getProtectionDomain().getCodeSource().getLocation()));
        MixinProcessor.addMixins(m);
        FabricLoader.getInstance().getObjectShare().put("andromeda:module_manager", m);

        updateStatus(Status.PRE_LAUNCH);

        for (Module<?> module : ModuleManager.get().loaded()) {
            AndromedaException.run(module::onPreLaunch, () ->
                    new AndromedaException.Builder().message("Failed to execute Module.onPreLaunch!").add("module", module.meta().id()));
        }
    }

    static void wrapIO(ThrowingRunnable<IOException> runnable, String msg) {
        try {
            runnable.run();
        } catch (IOException e) {
            LOGGER.error(msg, e);
        }
    }

    public static ClassPath getModuleClassPath() {
        return AndromedaMixins.getClassPath();
    }

    public static ExecutorService getPreLaunchService() {
        return ForkJoinPool.commonPool();
    }

    public static boolean testModVersion(Module<?> m, String modId, String predicate) {
        Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(modId);
        if (mod.isPresent() && !Debug.skipIntegration(m.meta().id(), modId)) {
            try {
                VersionPredicate version = VersionPredicate.parse(predicate);
                return version.test(mod.get().getMetadata().getVersion());
            } catch (VersionParsingException e) {
                return false;
            }
        }
        return false;
    }

    public static boolean isModLoaded(Module<?> m, String modId) {
        return !Debug.skipIntegration(m.meta().id(), modId) && FabricLoader.getInstance().isModLoaded(modId);
    }

    public static Status getStatus() {
        return STATUS;
    }

    private static void updateStatus(Status status) {
        STATUS = status;
        LOGGER.debug("Status updated to {}", status);
    }

    public enum Status {
        PRE_INIT,
        DISCOVERY,
        PRE_LAUNCH,
        MAIN,
        CLIENT,
        SERVER
    }
}
