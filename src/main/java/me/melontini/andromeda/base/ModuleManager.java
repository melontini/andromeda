package me.melontini.andromeda.base;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.*;
import lombok.CustomLog;
import me.melontini.andromeda.base.annotations.Unscoped;
import me.melontini.andromeda.util.Debug;
import me.melontini.dark_matter.api.base.config.ConfigManager;
import me.melontini.dark_matter.api.base.util.MakeSure;
import me.melontini.dark_matter.api.base.util.Utilities;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The ModuleManager is responsible for resolving and storing modules. It is also responsible for loading and fixing configs.
 */
@CustomLog
public class ModuleManager {

    public static final List<String> CATEGORIES = List.of("world", "blocks", "entities", "items", "bugfixes", "mechanics", "gui", "misc");

    private final Reference2ReferenceMap<Class<?>, Module<?>> discoveredModules;
    private final Object2ReferenceMap<String, Module<?>> discoveredModuleNames;

    private final Reference2ReferenceMap<Class<?>, Module<?>> modules;
    private final Object2ReferenceMap<String, Module<?>> moduleNames;

    final Map<String, Module<?>> mixinConfigs = new Object2ReferenceOpenHashMap<>();

    ModuleManager(List<Module<?>> sorted, @Nullable JsonObject oldCfg) {
        if (Bootstrap.INSTANCE != null)
            throw new IllegalStateException("ModuleManager already initialized!");
        Bootstrap.INSTANCE = this;

        this.discoveredModules = Utilities.supply(() -> {
            var m = sorted.stream().collect(Collectors.toMap(Object::getClass, Function.identity(), (t, t2) -> t, Reference2ReferenceLinkedOpenHashMap::new));
            return Reference2ReferenceMaps.unmodifiable(m);
        });
        this.discoveredModuleNames = Utilities.supply(() -> {
            var m = sorted.stream().collect(Collectors.toMap(module -> module.meta().id(), Function.identity(), (t, t2) -> t, Object2ReferenceOpenHashMap::new));
            return Object2ReferenceMaps.unmodifiable(m);
        });

        this.setUpConfigs(this.discoveredModules.values());

        doWork(this.discoveredModules.values(), module -> {
            module.config = Utilities.cast(module.manager.load(FabricLoader.getInstance().getConfigDir()));
            module.defaultConfig = Utilities.cast(module.manager.createDefault());
        });

        this.discoveredModules.values().forEach(Module::postConfig);

        if (oldCfg != null)
            this.discoveredModules.values().forEach(module -> module.acceptLegacyConfig(oldCfg));

        if (Debug.hasKey(Debug.Keys.ENABLE_ALL_MODULES))
            this.discoveredModules.values().forEach(module -> module.config().enabled = true);
        fixScopes(this.discoveredModules.values());

        this.discoveredModules.values().forEach(Module::save);

        this.modules = Utilities.supply(() -> {
            var m = this.discoveredModules.values().stream().filter(Module::enabled).collect(Collectors.toMap(Object::getClass, Function.identity(), (t, t2) -> t, Reference2ReferenceLinkedOpenHashMap::new));
            return Reference2ReferenceMaps.unmodifiable(m);
        });
        this.moduleNames = Utilities.supply(() -> {
            var m = this.discoveredModules.values().stream().filter(Module::enabled).collect(Collectors.toMap(module -> module.meta().id(), Function.identity(), (t, t2) -> t, Object2ReferenceOpenHashMap::new));
            return Object2ReferenceMaps.unmodifiable(m);
        });

        cleanConfigs(FabricLoader.getInstance().getConfigDir().resolve("andromeda"), this.discoveredModules.values());
    }

    private static void doWork(Collection<Module<?>> modules, Consumer<Module<?>> consumer) {
        Set<CompletableFuture<?>> futures = new HashSet<>();
        modules.forEach(m -> futures.add(CompletableFuture.runAsync(() -> consumer.accept(m))));
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    private void fixScopes(Collection<Module<?>> modules) {
        modules.forEach(m -> {
            if (m.meta().environment() == Environment.CLIENT && m.config().scope != Module.BaseConfig.Scope.GLOBAL) {
                LOGGER.error("{} Module '{}' has an invalid scope ({}), must be {}",
                        m.meta().environment(), m.meta().id(), m.config().scope, Module.BaseConfig.Scope.GLOBAL);
                m.config().scope = Module.BaseConfig.Scope.GLOBAL;
            }

            if (m.getClass().isAnnotationPresent(Unscoped.class) && m.config().scope != Module.BaseConfig.Scope.GLOBAL) {
                LOGGER.error("{} Module '{}' has an invalid scope ({}), must be {}",
                        "Unscoped", m.meta().id(), m.config().scope, Module.BaseConfig.Scope.GLOBAL);
                m.config().scope = Module.BaseConfig.Scope.GLOBAL;
            }
        });

        if (Debug.hasKey(Debug.Keys.FORCE_DIMENSION_SCOPE))
            modules.forEach(m -> {
                if (m.meta().environment() != Environment.CLIENT && !m.getClass().isAnnotationPresent(Unscoped.class)) {
                    m.config().scope = Module.BaseConfig.Scope.DIMENSION;
                }
            });
    }

    static void validateModule(Module<?> module) {
        MakeSure.notEmpty(module.meta().category(), "Module category can't be null or empty! Module: " + module.getClass());
        MakeSure.isTrue(!module.meta().category().contains("/"), "Module category can't contain '/'! Module: " + module.getClass());
        MakeSure.notEmpty(module.meta().name(), "Module name can't be null or empty! Module: " + module.getClass());
    }

    private void setUpConfigs(Collection<Module<?>> modules) {
        doWork(modules, m -> {
            var manager = makeManager(m);
            manager.onLoad((config1, path) -> {
                if (AndromedaConfig.get().sideOnlyMode) {
                    switch (m.meta().environment()) {
                        case BOTH -> config1.enabled = false;
                        case CLIENT -> {
                            if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT)
                                config1.enabled = false;
                        }
                        case SERVER -> {
                            if (FabricLoader.getInstance().getEnvironmentType() != EnvType.SERVER)
                                config1.enabled = false;
                        }
                    }
                }
            });
            manager.exceptionHandler((e, stage, path) -> LOGGER.error("Failed to %s config for module: %s".formatted(stage.toString().toLowerCase(), m.meta().id()), e));

            m.manager = Utilities.cast(manager);
            m.onConfig(Utilities.cast(manager));
        });
    }

    private ConfigManager<? extends Module.BaseConfig> makeManager(Module<?> m) {
        Class<? extends Module.BaseConfig> cls = getConfigClass(m.getClass());

        return cls == Module.BaseConfig.class ?
                ConfigManager.of(Module.BaseConfig.class, "andromeda/" + m.meta().id(), Module.BaseConfig::new) :
                ConfigManager.of(cls, "andromeda/" + m.meta().id());
    }

    /**
     * Parses the config class from modules generic type.
     *
     * @param m the module class.
     * @return the config class.
     */
    public Class<? extends Module.BaseConfig> getConfigClass(Class<?> m) {
        if (m.getGenericSuperclass() instanceof ParameterizedType pt) {
            for (Type ta : pt.getActualTypeArguments()) {
                if (ta instanceof Class<?> cls && Module.BaseConfig.class.isAssignableFrom(cls)) {
                    return Utilities.cast(cls);
                }
            }
        }
        return !Object.class.equals(m.getSuperclass()) ? getConfigClass(m.getSuperclass()) : Module.BaseConfig.class;
    }

    public void cleanConfigs(Path root, Collection<Module<?>> modules) {
        Set<Path> paths = collectPaths(root.getParent(), modules);
        if (Files.exists(root)) {
            Bootstrap.wrapIO(() -> Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toString().endsWith(".json") && !Files.isHidden(file) && !paths.contains(file)) {
                        Files.delete(file);
                        LOGGER.info("Removed {} as it doesn't belong to any module!", FabricLoader.getInstance().getGameDir().relativize(file));
                    }
                    return super.visitFile(file, attrs);
                }
            }), "Failed to clean up configs!");
        }
    }

    private Set<Path> collectPaths(Path root, Collection<Module<?>> modules) {
        Set<Path> paths = new HashSet<>();

        paths.add(root.resolve("andromeda/mod.json"));
        paths.add(root.resolve("andromeda/debug.json"));

        modules.forEach(module -> paths.add(module.manager().resolve(root)));

        return paths;
    }

    /**
     * Checks if a module is present.
     * @param cls the module class.
     * @return if a module is enabled.
     * @param <T> the module type.
     */
    public <T extends Module<?>> boolean isPresent(Class<T> cls) {
        return getModule(cls).isPresent();
    }

    /**
     * Returns the module of the given class, but only if it is enabled.
     * @param cls the module class.
     * @return The module, if enabled, or empty if not.
     * @param <T> the module type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Module<?>> Optional<T> getModule(Class<T> cls) {
        return (Optional<T>) Optional.ofNullable(modules.get(cls));
    }

    /**
     * Returns the module of the given id, but only if it is enabled.
     * @param name the module id.
     * @return The module, if enabled, or empty if not.
     * @param <T> the module type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Module<?>> Optional<T> getModule(String name) {
        return (Optional<T>) Optional.ofNullable(moduleNames.get(name));
    }

    /**
     * Returns the module of the given class.
     * <p>This will also return disabled modules. This should only be used during {@link Bootstrap.Status#DISCOVERY}.</p>
     * @param cls the module class.
     * @return The module, if discovered, or empty if not.
     * @param <T> the module type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Module<?>> Optional<T> getDiscovered(Class<T> cls) {
        return (Optional<T>) Optional.ofNullable(discoveredModules.get(cls));
    }

    /**
     * Returns the module of the given id.
     * <p>This will also return disabled modules. This should only be used during {@link Bootstrap.Status#DISCOVERY}.</p>
     * @param name the module id.
     * @return The module, if discovered, or empty if not.
     * @param <T> the module type.
     */
    @SuppressWarnings("unchecked")
    public <T extends Module<?>> Optional<T> getDiscovered(String name) {
        return (Optional<T>) Optional.ofNullable(discoveredModuleNames.get(name));
    }

    @ApiStatus.Internal
    public Optional<Module<?>> moduleFromConfig(String name) {
        return Optional.ofNullable(mixinConfigs.get(name));
    }

    @ApiStatus.Internal
    public Collection<Module<?>> all() {
        return discoveredModules.values();
    }

    /**
     * @return a collection of all loaded modules.
     */
    public Collection<Module<?>> loaded() {
        return modules.values();
    }

    /**
     * Quickly returns a module of the given class. Useful for mixins and registration.
     * <p>This will throw an {@link IllegalStateException} if the module is not loaded.</p>
     * @param cls the module class.
     * @return the module instance.
     * @param <T> the module time.
     * @throws IllegalStateException if the module is not loaded.
     */
    public static <T extends Module<?>> T quick(Class<T> cls) {
        return get().getModule(cls).orElseThrow(() -> new IllegalStateException("Module %s requested quickly, but is not loaded.".formatted(cls)));
    }

    /**
     * @return The module manager.
     */
    public static ModuleManager get() {
        return MakeSure.notNull(Bootstrap.INSTANCE, "ModuleManager requested too early!");
    }

    void print() {
        Map<String, Set<Module<?>>> categories = Utilities.consume(new LinkedHashMap<>(), map -> get().loaded().forEach(m ->
                map.computeIfAbsent(m.meta().category(), s -> new LinkedHashSet<>()).add(m)));

        StringBuilder builder = new StringBuilder();
        categories.forEach((s, strings) -> {
            builder.append("\n\t - ").append(s);
            if (!ModuleManager.CATEGORIES.contains(s)) builder.append("*");
            builder.append("\n\t  |-- ");

            StringJoiner joiner = new StringJoiner(", ");
            strings.forEach(m -> joiner.add(m.meta().name().replace('/', '.') +
                    (!m.getClass().getName().startsWith("me.melontini.andromeda") ? '*' : "")));
            builder.append(joiner);
        });
        if (!categories.isEmpty()) {
            LOGGER.info("Loaded modules: {}", builder);
            LOGGER.info("* - custom modules/categories not provided by Andromeda.");
        } else {
            LOGGER.info("No modules loaded!");
        }
    }

    public interface ModuleSupplier {
        List<? extends Module<?>> get();
    }
}
