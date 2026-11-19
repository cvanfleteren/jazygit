package net.vanfleteren.jazygit.app.keys;

import net.vanfleteren.jazygit.kernel.content.PanelSources;
import net.vanfleteren.jazygit.kernel.ui.Feature;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSources;

/**
 * What the content panel shows: the sources of the features, in the order of {@link Features#ALL}, and then the
 * content panel's own, which answer for every pane it mirrors (ADR 154). It is the {@link ContentSources#standard()}
 * sources, given to the content panel as a service, as the keymap is (ADR 136).
 */
public final class StandardContent implements ContentSources.Provider {

    private static final ContentSources SOURCES =
            new ContentSources(Features.ALL.flatMap(Feature::contents).appendAll(PanelSources.ALL));

    @Override
    public ContentSources sources() {
        return SOURCES;
    }
}
