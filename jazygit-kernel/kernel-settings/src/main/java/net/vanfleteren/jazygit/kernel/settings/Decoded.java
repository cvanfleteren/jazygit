package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.List;
import io.vavr.control.Option;

/**
 * What the settings file's value holds of a setting, if anything, with the keys of what is wrong in it.
 *
 * @param value   what the value holds of the setting; none when it holds nothing valid, and the setting keeps what it
 *                had
 * @param invalid the keys of what is wrong in the value, as the settings file names them, such as {@code ui.theme}
 *                or {@code editors[1].dir-command}
 * @param <T>     what the setting is
 */
public record Decoded<T>(Option<T> value, List<String> invalid) {}
