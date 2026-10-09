package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Loadable;

import java.util.List;
import java.util.function.Function;

/**
 * What a list shows for data that is still loading or failed to load.
 */
final class Placeholders {

    static String loading() {
        return Messages.get("placeholder.loading");
    }

    private Placeholders() {
    }

    static String error(String message) {
        return Messages.get("placeholder.error", message);
    }

    static <T> List<String> items(Loadable<T> loadable, Function<T, List<String>> items) {
        return switch (loadable) {
            case Loadable.Loading<T>() -> List.of(loading());
            case Loadable.Failed<T>(String message) -> List.of(error(message));
            case Loadable.Loaded<T>(T value) -> items.apply(value);
        };
    }
}
