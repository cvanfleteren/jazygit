package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.lens.Lens;

/**
 * A part of the {@link Model} that a feature keeps for itself, declared as a constant in the feature's own package
 * (ADR 157): the model holds it without naming it, as it holds a {@link net.vanfleteren.jazygit.state.popup.Popup}. A
 * part that was never set, or was set back to its initial value, is not kept, so two models with the same parts are
 * equal.
 *
 * <p>A part is kept under its name: no two parts may share one, which {@code PartNamesTest} in the app checks for
 * every part constant.
 *
 * @param name    the name the part is kept under; it starts with the root package of the feature's module
 *                ({@code github.builds})
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
}
