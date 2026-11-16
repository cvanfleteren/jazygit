package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.lens.Lens;

/**
 * A part of the {@link Model} that one feature owns: the key under which the model keeps it in its {@link Slices},
 * and what it is before the feature changed it. The feature declares it once, as a {@code static final} field next to
 * the type it holds, and the model names no feature for it: adding one changes nothing outside the feature.
 *
 * <p>A slice is the same as itself only: two slices declared apart are two slices, whatever their names. That is why
 * it is a class and not a record, which the architecture rules make an exception for by name ({@code Values.SLICE}
 * in jazygit-test-support).
 *
 * @param <T> what it holds, immutable
 */
public final class Slice<T> {

    private final String name;
    private final T initial;

    private Slice(String name, T initial) {
        this.name = name;
        this.initial = initial;
    }

    /**
     * A new slice, which holds {@code initial} until it is changed.
     *
     * @param name what it is shown as; no message key, so not starting with the root package of a module
     */
    public static <T> Slice<T> of(String name, T initial) {
        return new Slice<>(name, initial);
    }

    /**
     * What it is shown as.
     */
    public String name() {
        return name;
    }

    /**
     * What it holds until it is changed, and again once it is changed back to it.
     */
    public T initial() {
        return initial;
    }

    /**
     * Where this slice sits in a model, for the code that changes models through a {@link Lens}.
     */
    public Lens<Model, T> lens() {
        return Lens.of(model -> model.get(this), (model, value) -> model.map(this, held -> value));
    }
}
