package me.melontini.andromeda.util.exceptions;

import com.google.common.base.Strings;
import me.melontini.andromeda.base.Bootstrap;
import me.melontini.andromeda.util.CommonValues;
import me.melontini.andromeda.util.CrashHandler;
import me.melontini.dark_matter.api.base.util.classes.Context;
import me.melontini.dark_matter.api.base.util.classes.ThrowingRunnable;
import me.melontini.dark_matter.api.crash_handler.Crashlytics;
import me.melontini.dark_matter.api.crash_handler.Prop;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Consumer;

public class AndromedaException extends RuntimeException {

    private final boolean report;
    private final Map<String, String> statuses;

    @SuppressWarnings("unused")
    private AndromedaException() {
        this(false, "Empty ctx called! This must never happen!!!", null, new HashMap<>());
    }

    private AndromedaException(boolean report, String message, Throwable cause, Map<String, String> statuses) {
        super(message, cause);
        this.report = report;
        this.statuses = statuses;
    }

    @Override
    public String getMessage() {
        StringBuilder b = new StringBuilder();
        b.append("(Andromeda) ");
        if (Strings.isNullOrEmpty(super.getMessage())) b.append("Something went very wrong!");
        else b.append(super.getMessage());
        return b.toString();
    }

    public Map<String, String> getStatuses() {
        return Collections.unmodifiableMap(statuses);
    }

    public boolean shouldReport() {
        return report;
    }

    public static void run(ThrowingRunnable<Throwable> runnable, Consumer<Builder> consumer) {
        try {
            runnable.run();
        } catch (Throwable e) {
            var builder = AndromedaException.builder();
            consumer.accept(builder);
            throw builder.cause(e).build();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private static final Set<String> DEFAULT_KEYS = Set.of(
                "bootstrap_status", "platform",
                prop(Prop.ENVIRONMENT), prop(Prop.OS), prop(Prop.JAVA_VERSION), prop(Prop.JAVA_VENDOR)
        );

        private static String prop(Prop prop) {
            return prop.name().toLowerCase();
        }

        private final List<String> message = new ArrayList<>();
        private Throwable cause;
        private boolean report = true;

        private final Map<String, String> statuses = new LinkedHashMap<>();

        private Builder() {
            add(Prop.ENVIRONMENT, Prop.OS, Prop.JAVA_VERSION, Prop.JAVA_VENDOR);
            add("platform", CommonValues.platform());
            add("bootstrap_status", Bootstrap.Status.get());
        }

        public Builder message(String message) {
            this.message.add(message);
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder report(boolean report) {
            this.report = report;
            return this;
        }

        public Builder add(Prop... props) {
            for (Prop prop : props) {
                statuses.put(prop.name().toLowerCase(), prop.get());
            }
            return this;
        }

        public Builder add(String key, Object value) {
            statuses.put(key, String.valueOf(value));
            return this;
        }

        private void disableInHierarchy(Throwable cause) {
            if (cause == null) return;
            if (cause instanceof AndromedaException e) {
                for (String defaultKey : DEFAULT_KEYS) {
                    e.statuses.remove(defaultKey);
                }
            }
            disableInHierarchy(cause.getCause());
        }

        public AndromedaException build() {
            disableInHierarchy(cause);

            var e = new AndromedaException(report,
                    message.isEmpty() ? "Something went very wrong!" : StringUtils.join(message.toArray(), '\n'),
                    cause, statuses);

            //CrashHandler can't automatically handle preLaunch errors, so this is what we have to do.
            if (!Crashlytics.hasHandler("andromeda")) CrashHandler.handleCrash(e, Context.of());

            return e;
        }
    }
}
