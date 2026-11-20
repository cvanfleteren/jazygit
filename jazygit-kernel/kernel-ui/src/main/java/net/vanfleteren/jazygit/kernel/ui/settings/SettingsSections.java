package net.vanfleteren.jazygit.kernel.ui.settings;

import io.vavr.collection.List;
import java.util.ServiceLoader;

/**
 * The sections the features add to the settings panel, in the order of their features (ADR 163).
 */
public final class SettingsSections {

    private SettingsSections() {}

    /**
     * The sections of the app: those of the {@link Provider} on the classpath, which the app gives. Without the app, as
     * in the tests of a group of features, there are none.
     */
    public static List<SettingsSection> standard() {
        return Found.SECTIONS;
    }

    /**
     * Gives the sections of the app, found as a service ({@code META-INF/services}), so that the settings panel asks
     * for them without naming the app, as the content panel finds its sources (ADR 154).
     */
    public interface Provider {

        /**
         * The sections of the features, in the order of their features.
         */
        List<SettingsSection> sections();
    }

    private static final class Found {
        // Looked up once, when the sections are first asked for.
        private static final List<SettingsSection> SECTIONS = ServiceLoader.load(Provider.class)
                .findFirst()
                .map(Provider::sections)
                .orElseGet(List::empty);
    }
}
