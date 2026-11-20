package net.vanfleteren.jazygit.kernel.state.settings;

import io.vavr.collection.List;
import java.util.ServiceLoader;

/**
 * What the sections the features add to the settings panel read as it opens, in the order of their features
 * (ADR 163). The sections are views; this is what the update that opens the panel needs of them.
 */
public final class PanelReads {

    private PanelReads() {}

    /**
     * The reads of the sections of the app: those of the {@link Provider} on the classpath, which the app gives.
     * Without the app, as in the tests of a group of features, there are none.
     */
    public static List<SectionReads> standard() {
        return Found.READS;
    }

    /**
     * Gives the reads of the sections of the app, found as a service ({@code META-INF/services}), so the panel opens
     * without naming the app, as the content panel finds its sources (ADR 154).
     */
    public interface Provider {

        /**
         * What each section of the features reads, in the order of their features.
         */
        List<SectionReads> reads();
    }

    private static final class Found {
        // Looked up once, when the reads are first asked for.
        private static final List<SectionReads> READS = ServiceLoader.load(Provider.class)
                .findFirst()
                .map(Provider::reads)
                .orElseGet(List::empty);
    }
}
