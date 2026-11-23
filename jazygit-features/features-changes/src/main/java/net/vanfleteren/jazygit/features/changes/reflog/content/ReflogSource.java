package net.vanfleteren.jazygit.features.changes.reflog.content;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.time.Duration;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogUpdate;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogView;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.view.Preview;
import net.vanfleteren.jazygit.kernel.ui.content.ContentArea;
import net.vanfleteren.jazygit.kernel.ui.content.ContentSource;
import net.vanfleteren.jazygit.kernel.ui.content.Heading;
import net.vanfleteren.jazygit.kernel.ui.content.Heading.Part;
import net.vanfleteren.jazygit.kernel.ui.content.Mirrored;
import net.vanfleteren.jazygit.kernel.ui.content.Shown;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * What the highlighted entry of the reflog is, above its commit, which the content panel shows as for any commit
 * (ADR 186, 187): its selector, how long ago HEAD was moved to it, its whole subject, and whether HEAD is at its
 * commit or the commit is lost. It shows nothing itself.
 */
public record ReflogSource() implements ContentSource {

    @Override
    public Option<Shown> shown(Model model, Mirrored pane) {
        return Option.none();
    }

    @Override
    public Option<Heading> heading(Model model, Mirrored pane) {
        if (!pane.equals(Mirrored.COMMITS) || !ReflogUpdate.shown(model)) {
            return Option.none();
        }
        ReflogView view = model.part(ReflogView.PART);
        Option<Sha> shown =
                model.repoView().preview().commitDetail().map(Preview::shown).map(Preview::key);
        Option<Sha> head =
                model.repo().status().toOption().flatMap(status -> status.head().commit());
        return view.highlightedEntry()
                .filter(entry -> shown.contains(entry.sha()))
                .map(entry -> of(entry, head.contains(entry.sha()), view.isLost(entry), model));
    }

    @Override
    public List<ContentArea> areas() {
        return List.empty();
    }

    /**
     * The heading of {@code entry}, whose commit HEAD is at when {@code atHead}, and no ref reaches when
     * {@code lost}, at the time {@code model} says it is: then a blank line before the commit.
     */
    static Heading of(ReflogEntry entry, boolean atHead, boolean lost, Model model) {
        List<List<Part>> said = List.of(
                List.of(
                        Part.styled(entry.selector(), Theme.Classes.SHA),
                        Part.styled(
                                " · " + TimeText.ago(Duration.between(entry.time(), model.now())), Theme.Classes.DIM)),
                List.of(Part.styled(entry.subject(), Theme.Classes.BOLD)));
        return new Heading(said.appendAll(state(atHead, lost)).append(List.empty()));
    }

    /**
     * What is said of where the commit is: a line when HEAD is at it, or when it is lost, else none.
     */
    private static List<List<Part>> state(boolean atHead, boolean lost) {
        if (atHead) {
            return List.of(List.of(Part.styled(
                    Messages.get(lost ? "changes.reflog.heading.atHeadOnly" : "changes.reflog.heading.atHead"),
                    Theme.Classes.CURRENT_BRANCH)));
        }
        return lost
                ? List.of(List.of(Part.styled(Messages.get("changes.reflog.heading.lost"), Theme.Classes.ERROR)))
                : List.empty();
    }
}
