package net.vanfleteren.jazygit.kernel.settings;

import lombok.With;

/**
 * A setting, as whoever owns it declares it: where it is in the settings file, what it is until the user sets it,
 * how it is read and kept there, and when it is kept.
 *
 * @param path    its key in the settings file, dotted, such as {@code ui.theme}; also how a problem with it names it
 * @param initial what it is when the file does not set it, or sets it to nothing valid
 * @param codec   how it is read from the file and kept there
 * @param kept    when it is kept in the file
 * @param <T>     what it is
 */
@With
public record SettingKey<T>(String path, T initial, Codec<T> codec, Kept kept) {

    /**
     * A setting at {@code path}, kept in the file once it differs from {@code initial}.
     */
    public static <T> SettingKey<T> of(String path, T initial, Codec<T> codec) {
        return new SettingKey<>(path, initial, codec, Kept.WHEN_CHANGED);
    }

    /**
     * A setting at {@code path} that only the user writes, as a list of programs: the app reads it and never keeps
     * it.
     */
    public static <T> SettingKey<T> userOwned(String path, T initial, Codec<T> codec) {
        return new SettingKey<>(path, initial, codec, Kept.NEVER);
    }
}
