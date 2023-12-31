package me.melontini.andromeda.common.registries;

import me.melontini.dark_matter.api.base.util.MakeSure;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Keeper<T> {

    private volatile Callable<T> supplier;
    private volatile boolean initialized;

    private T value;
    private Field field;

    private final Set<Consumer<T>> consumers = new HashSet<>();

    public Keeper(Callable<T> supplier) {
        this.supplier = MakeSure.notNull(supplier);
    }

    public static <T> Keeper<T> of(Callable<T> supplier) {
        return new Keeper<>(supplier);
    }

    public boolean isPresent() {
        return this.value != null;
    }

    public void ifPresent(Consumer<T> consumer) {
        if (this.value != null) consumer.accept(this.value);
    }

    public boolean initialized() {
        return this.supplier == null;
    }

    public Keeper<T> afterInit(Consumer<T> consumer) {
        this.consumers.add(consumer);
        return this;
    }

    public @Nullable Field getField() {
        return this.field;
    }

    void init(@Nullable Field f) throws Exception {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    this.value = supplier.call();
                    this.supplier = null;
                    this.field = f;
                    this.initialized = true;

                    if (isPresent()) this.consumers.forEach(c -> c.accept(this.value));
                }
            }
        }
    }

    public T get() {
        return this.value;
    }

    public T orThrow() {
        return orThrow("No value present! Keeper (%s) not bootstrapped?".formatted(getField() == null ? "UNKNOWN" : getField().getName()));
    }

    public T orThrow(String msg) {
        return orThrow(() -> new IllegalStateException(msg));
    }

    public <X extends Throwable> T orThrow(Supplier<X> e) throws X {
        if (this.value == null) throw e.get();
        return this.value;
    }
}
