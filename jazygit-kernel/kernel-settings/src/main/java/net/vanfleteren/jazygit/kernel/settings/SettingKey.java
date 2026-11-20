package net.vanfleteren.jazygit.kernel.settings;

import io.vavr.collection.LinkedHashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import java.util.function.Function;
import lombok.With;

/**
 * A setting, as whoever owns it declares it: where it is in the settings file, what it is until the user sets it,
 * how it is read and kept there, and when it is kept (ADR 163). Its value is in {@link Settings}, under its path.
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

    /**
     * {@code read} with this setting as {@code value} finds it in the file, and with the keys of what is wrong in it;
     * as it was when {@code value} finds nothing.
     */
    Settings.Read readInto(Settings.Read read, Function<String, Option<Object>> value) {
        return value.apply(path)
                .map(found -> codec.decode(path, found))
                .map(decoded -> new Settings.Read(
                        decoded.value()
                                .map(found -> read.settings().with(this, found))
                                .getOrElse(read.settings()),
                        read.invalid().appendAll(decoded.invalid())))
                .getOrElse(read);
    }

    /**
     * How {@code settings} keep this setting in the settings file: nothing when it is never kept, nor when it is at
     * its initial value, unless it is always kept.
     */
    Map<List<String>, Object> entriesOf(Settings settings) {
        T value = settings.get(this);
        boolean written = switch (kept) {
            case ALWAYS -> true;
            case WHEN_CHANGED -> !initial.equals(value);
            case NEVER -> false;
        };
        return written ? codec.encode(path, value) : LinkedHashMap.empty();
    }

    /**
     * {@code to}, with this setting as {@code from} has it.
     */
    Settings take(Settings to, Settings from) {
        return to.with(this, from.get(this));
    }

    /**
     * Whether {@code first} and {@code second} differ in this setting.
     */
    boolean differs(Settings first, Settings second) {
        return !first.get(this).equals(second.get(this));
    }
}
