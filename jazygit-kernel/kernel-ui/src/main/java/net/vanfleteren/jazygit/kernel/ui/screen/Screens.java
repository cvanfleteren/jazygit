package net.vanfleteren.jazygit.kernel.ui.screen;

import io.vavr.collection.List;
import java.util.ServiceLoader;
import net.vanfleteren.jazygit.kernel.state.preferences.Screen;

/**
 * The screens the left of the window can show (ADR 168), in the order the title of the status names them: the
 * repository first, then those of the features, in their order. The views know no feature: the app gives the screens
 * of its features as a service, as it gives the keymap (ADR 136).
 *
 * @param all the screens, the repository first
 */
public record Screens(List<ScreenSpec> all) {

    /**
     * The repository screen alone, as the tests of the views have it without the app.
     */
    public static final Screens REPOSITORY = new Screens(List.of(ScreenSpec.REPOSITORY));

    /**
     * The repository screen, then {@code specs}, in that order.
     */
    public static Screens of(List<ScreenSpec> specs) {
        return new Screens(specs.prepend(ScreenSpec.REPOSITORY));
    }

    /**
     * How {@code screen} is drawn; the repository screen's for one no feature brings.
     */
    public ScreenSpec spec(Screen screen) {
        return all.find(spec -> spec.screen().equals(screen)).getOrElse(ScreenSpec.REPOSITORY);
    }

    /**
     * The screens of the app, as declared by its features: those of the {@link Provider} on the classpath. Without the
     * app, as in the tests of the views, only the {@link #REPOSITORY} one.
     */
    public static Screens standard() {
        return Found.SCREENS;
    }

    /**
     * Gives the screens of the app's features, as a service.
     */
    public interface Provider {

        Screens screens();
    }

    private static final class Found {

        private static final Screens SCREENS = ServiceLoader.load(Provider.class)
                .findFirst()
                .map(Provider::screens)
                .orElse(REPOSITORY);
    }
}
