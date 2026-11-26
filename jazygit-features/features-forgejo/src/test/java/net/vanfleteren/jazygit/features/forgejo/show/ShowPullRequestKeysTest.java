package net.vanfleteren.jazygit.features.forgejo.show;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tamboui.tui.event.KeyEvent;
import io.vavr.control.Option;
import java.nio.file.Path;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFailure;
import net.vanfleteren.jazygit.features.forgejo.ForgejoFeature;
import net.vanfleteren.jazygit.features.forgejo.browse.Browser;
import net.vanfleteren.jazygit.features.forgejo.pulls.PullRequestMsg;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import org.junit.jupiter.api.Test;

/**
 * The key of the pull request of a branch on Forgejo, and the browser it opens it in (ADR 175, 176).
 */
class ShowPullRequestKeysTest {

    @Test
    void oOnABranchAsksForItsPullRequestWhileForgejoIsOn() {
        Keymap keys = ForgejoFeature.FEATURE.keys(Keymap.empty());

        assertEquals(
                Option.of(new FeatureMsg.Owned(
                        ForgejoFeature.ID, new PullRequestMsg.Requested(new BranchName("feature")))),
                keys.handle(Scopes.BRANCHES, KeyEvent.ofChar('o'), Option.of(new BranchName("feature"))));
    }

    @Test
    void theBrowserIsThatOfBrowserElseTheDesktops() {
        assertEquals(Option.of("firefox -P work"), Browser.chosen(Option.of(" firefox -P work "), "Linux"));
        assertEquals(Option.of("xdg-open"), Browser.chosen(Option.of("  "), "Linux"));
        assertEquals(Option.of("open"), Browser.chosen(Option.none(), "Mac OS X"));
        assertEquals(Option.of("rundll32 url.dll,FileProtocolHandler"), Browser.chosen(Option.none(), "Windows 11"));
        assertEquals(Option.none(), Browser.chosen(Option.none(), "FreeBSD"));
        assertEquals(
                new ForgejoFailure.NoBrowser(),
                Browser.of(Path.of("."), Option.none(), "FreeBSD")
                        .open("https://x")
                        .getLeft());
    }
}
