package hexacloud.core.utils.common;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Casts {

    private Casts() {
    }

    public static <T> Optional<T> as(Object obj, Class<T> type) {
        return type.isInstance(obj)
                ? Optional.of(type.cast(obj))
                : Optional.empty();
    }

    public static <T, R> Optional<R> map(Object obj, Class<T> type, Function<T, R> mapper) {
        return type.isInstance(obj)
                ? Optional.ofNullable(mapper.apply(type.cast(obj)))
                : Optional.empty();
    }

    public static Matcher match(Object obj) {
        return new Matcher(obj);
    }

    public static <R> ValueMatcher<R> matchValue(Object obj) {
        return new ValueMatcher<R>(obj);
    }

    public static <T> boolean test(Object obj, Class<T> type, Predicate<T> predicate) {
    return type.isInstance(obj)
        && predicate.test(type.cast(obj));
}

    public static boolean is(Object obj, Class<?> type) {
        return type.isInstance(obj);
    }

    public static final class Matcher {

        private final Object obj;
        private boolean matched;

        private Matcher(Object obj) {
            this.obj = obj;
        }

        public <T> Matcher when(Class<T> type, Consumer<T> consumer) {
            if (!matched && type.isInstance(obj)) {
                matched = true;
                consumer.accept(type.cast(obj));
            }
            return this;
        }

        public Matcher otherwise(Consumer<Object> consumer) {
            if (!matched) {
                consumer.accept(obj);
            }
            return this;
        }

        public boolean matched() {
            return matched;
        }
    }

    public static final class ValueMatcher<R> {

        private final Object obj;
        private boolean matched;
        private R result;

        private ValueMatcher(Object obj) {
            this.obj = obj;
        }

        public <T> ValueMatcher<R> when(Class<T> type, Function<T, R> mapper) {
            if (!matched && type.isInstance(obj)) {
                matched = true;
                result = mapper.apply(type.cast(obj));
            }
            return this;
        }

        public R otherwise(Function<Object, R> mapper) {
            if (!matched) {
                matched = true;
                result = mapper.apply(obj);
            }
            return result;
        }

        public R orElse(R defaultValue) {
            return matched ? result : defaultValue;
        }

        public R orElseGet(Supplier<R> supplier) {
            return matched ? result : supplier.get();
        }

        public R get() {
            return result;
        }

        public boolean matched() {
            return matched;
        }
    }
}