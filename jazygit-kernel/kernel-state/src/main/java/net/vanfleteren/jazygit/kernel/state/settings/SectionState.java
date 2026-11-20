package net.vanfleteren.jazygit.kernel.state.settings;

/**
 * What a section of the settings panel keeps for itself while the panel is open, as what it read when the panel
 * opened, by its name (ADR 163). Whoever declares the section declares it as a constant, as a feature declares a part
 * of the model (ADR 157).
 *
 * @param name    the name it is kept under, starting with the root package of the module that declares it
 * @param initial what it is as the panel opens, until the section sets it
 * @param <S>     what it is
 */
public record SectionState<S>(String name, S initial) {}
