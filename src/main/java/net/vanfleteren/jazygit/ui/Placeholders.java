package net.vanfleteren.jazygit.ui;

import net.vanfleteren.jazygit.state.Loadable;

import java.util.List;
import java.util.function.Function;

/**
 * What a list shows for data that is still loading or failed to load.
 */
final class Placeholders {

    static final String LOADING = "Loading…";

    private Placeholders() {
    }

    static String error(String message) {
        return "Error: " + message;
    }

    static <T> List<String> items(Loadable<T> loadable, Function<T, List<String>> items) {
        return switch (loadable) {
            case Loadable.Loading<T>() -> List.of(LOADING);
            case Loadable.Failed<T>(String message) -> List.of(error(message));
            case Loadable.Loaded<T>(T value) -> items.apply(value);
        };
    }
}
