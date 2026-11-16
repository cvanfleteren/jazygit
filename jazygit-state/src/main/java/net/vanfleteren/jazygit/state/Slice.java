package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.lens.Lens;

/**
 * A part of the {@link Model} that one feature owns: the key under which the model keeps it in its {@link Slices},
 * and what it is before the feature changed it. The feature declares it, as a {@code static final} field next to the
 * type it holds, and the model names no feature for it: adding one changes nothing outside the feature.
 *
 * <p>Two slices are the same when their names are; each name is declared once.
 *
 * @param name    what tells it apart from every other slice, starting with the root package of the module that
 *                declares it
 * @param initial what it holds until it is changed, and again once it is changed back to it
 * @param <T>     what it holds, immutable
 */
public record Slice<T>(String name, T initial) {

    public static <T> Slice<T> of(String name, T initial) {
        return new Slice<>(name, initial);
    }

    /**
     * Where this slice sits in a model, for the code that changes models through a {@link Lens}.
     */
    public Lens<Model, T> lens() {
        return Lens.of(model -> model.get(this), (model, value) -> model.map(this, held -> value));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Slice<?> slice && slice.name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
