package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.lens.Lens;

/**
 * A part of the {@link Model} that a feature keeps for itself, declared as a constant in the feature's own package
 * (ADR 157): the model holds it without naming it, as it holds a {@link net.vanfleteren.jazygit.state.popup.Popup}. A
 * part that was never set, or was set back to its initial value, is not kept, so two models with the same parts are
 * equal.
 *
 * <p>A part is the key it is kept under, and equal only to itself: two parts never share their value, whatever their
 * names, so no list of them is needed to keep them apart.
 *
 * @param name    what the part is to a reader, as in a failing test; it starts with the root package of the feature's
 *                module ({@code github.builds})
 * @param initial its value before the feature set it
 * @param <S>     the type of its value, a value as the rest of the model is
 */
public record Part<S>(String name, S initial) {

    public static <S> Part<S> of(String name, S initial) {
        return new Part<>(name, initial);
    }

    /**
     * Where this part sits in a model.
     */
    public Lens<Model, S> lens() {
        return Lens.of(model -> model.part(this), (model, value) -> model.withPart(this, value));
    }

    /**
     * Whether {@code other} is this part: no other part is, whatever its name and initial value.
     */
    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
