package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.feature.help.HelpMsg;
import net.vanfleteren.jazygit.feature.theme.ThemeMsg;
import net.vanfleteren.jazygit.model.SampleData;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.TestModels;
import net.vanfleteren.jazygit.state.Update;
import net.vanfleteren.jazygit.ui.FilesPanel;
import org.junit.jupiter.api.Test;

/**
 * Every popup of the app, of which at most one is open.
 */
class PopupsTest {

    private final Popups popups = new Popups(msg -> {});
    private final Model loaded = TestModels.loaded(new SampleData());

    @Test
    void whatIsBehindAnOpenPopupIsDimmed() {
        Popups.Rendered rendered = render(new HelpMsg.Requested(FilesPanel.ID, List.empty()));

        assertTrue(rendered.dialog().isDefined());
        assertTrue(rendered.dimsBehind());
    }

    @Test
    void whatIsBehindTheThemePopupIsNotDimmedSoTheThemeHighlightedShowsAsItWillBe() {
        Popups.Rendered rendered = render(new ThemeMsg.Requested());

        assertTrue(rendered.dialog().isDefined());
        assertFalse(rendered.dimsBehind());
    }

    @Test
    void nothingIsDimmedWithoutAPopup() {
        assertFalse(popups.render(loaded, Option.none()).dimsBehind());
    }

    private Popups.Rendered render(Msg opening) {
        return popups.render(Update.update(loaded, opening).model(), Option.none());
    }
}
