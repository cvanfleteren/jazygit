package net.vanfleteren.jazygit.ui.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.ServiceLoader;
import net.vanfleteren.jazygit.state.AreaId;

/**
 * The sources of what the content panel shows, in the order they are asked (ADR 154), and the areas they declare.
 *
 * @param sources the sources, the first that answers for a pane showing what it gives
 */
public record ContentSources(List<ContentSource> sources) {

    /**
     * The sources of the app: those of the {@link Provider} on the classpath, which the app gives. Without the app, as
     * in the tests of the views, there are none.
     */
    public static ContentSources standard() {
        return Found.SOURCES;
    }

    /**
     * Every area of the sources, each once, in the order the sources declare them.
     */
    public List<ContentArea> areas() {
        return sources.flatMap(ContentSource::areas).distinctBy(ContentArea::id);
    }

    /**
     * The area {@code id} is the id of, if a source declares it.
     */
    public Option<ContentArea> area(AreaId id) {
        return areas().find(area -> area.id().equals(id));
    }

    /**
     * The area whose element has the id {@code id}, which can have the focus, if a source declares it.
     */
    public Option<ContentArea> focusable(String id) {
        return area(new AreaId(id));
    }

    /**
     * Gives the sources of the app, found as a service ({@code META-INF/services}), so that the content panel asks for
     * them without naming the app, as the keymap is found (ADR 136).
     */
    public interface Provider {

        /**
         * The sources of the app, in the order they are asked.
         */
        ContentSources sources();
    }

    private static final class Found {
        // Looked up once, when the sources are first asked for.
        private static final ContentSources SOURCES = ServiceLoader.load(Provider.class)
                .findFirst()
                .map(Provider::sources)
                .orElseGet(() -> new ContentSources(List.empty()));
    }
}
