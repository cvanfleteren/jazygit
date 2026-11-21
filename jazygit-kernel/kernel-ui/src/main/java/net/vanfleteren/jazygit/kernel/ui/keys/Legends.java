package net.vanfleteren.jazygit.kernel.ui.keys;

import io.vavr.collection.List;
import java.util.ServiceLoader;

/**
 * What the features add to the legends of the panels, in the order of their features (ADR 168), as they add sections
 * to the settings panel (ADR 163). The help knows no feature: the app gives them as a service.
 *
 * @param all each line a feature adds, in the order of the features
 */
public record Legends(List<Legend> all) {

    /**
     * No feature adds anything, as the tests of the views have it without the app.
     */
    public static final Legends NONE = new Legends(List.empty());

    /**
     * What the features add to the legend of the panel of {@code scopeId}, in their order.
     */
    public List<Legend> of(String scopeId) {
        return all.filter(legend -> legend.scopeId().equals(scopeId));
    }

    /**
     * The legends of the app: those of the {@link Provider} on the classpath. Without the app, {@link #NONE}.
     */
    public static Legends standard() {
        return Found.LEGENDS;
    }

    /**
     * Gives what the app's features add to the legends, as a service.
     */
    public interface Provider {

        Legends legends();
    }

    private static final class Found {

        private static final Legends LEGENDS = ServiceLoader.load(Provider.class)
                .findFirst()
                .map(Provider::legends)
                .orElse(NONE);
    }
}
