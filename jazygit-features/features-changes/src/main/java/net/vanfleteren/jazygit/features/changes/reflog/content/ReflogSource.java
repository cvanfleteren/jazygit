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
                .map(entry -> of(entry, keeping(head.contains(entry.sha()), view, entry), model));
    }

    @Override
    public List<ContentArea> areas() {
        return List.empty();
    }

    /**
     * The heading of {@code entry}, saying {@code kept} of its commit if anything, at the time {@code model} says it
     * is: then a blank line before the commit.
     */
    static Heading of(ReflogEntry entry, Option<Keeping> kept, Model model) {
        List<List<Part>> said = List.of(
                List.of(
                        Part.styled(entry.selector(), Theme.Classes.SHA),
                        Part.styled(
                                " · " + TimeText.ago(Duration.between(entry.time(), model.now())), Theme.Classes.DIM)),
                List.of(Part.styled(entry.subject(), Theme.Classes.BOLD)));
        return new Heading(said.appendAll(kept.map(Keeping::said)).append(List.empty()));
    }

    /**
     * What keeps the commit of {@code entry}, worth saying: HEAD, at it when {@code atHead}, or nothing but the
     * reflog; none when a branch, tag or remote has it, or that is not known yet.
     */
    static Option<Keeping> keeping(boolean atHead, ReflogView view, ReflogEntry entry) {
        if (atHead) {
            return Option.of(view.isKeptByHead(entry) ? Keeping.HEAD_ONLY_AT_IT : Keeping.HEAD_AT_IT);
        }
        if (view.isLost(entry)) {
            return Option.of(Keeping.NOTHING);
        }
        return Option.when(view.isKeptByHead(entry), Keeping.HEAD_ONLY);
    }

    /**
     * What keeps a commit, as the heading says it.
     */
    enum Keeping {
        /** HEAD is at it, and a branch, tag or remote may have it too. */
        HEAD_AT_IT("changes.reflog.heading.atHead", Theme.Classes.CURRENT_BRANCH),
        /** HEAD is at it, and nothing else has it. */
        HEAD_ONLY_AT_IT("changes.reflog.heading.atHeadOnly", Theme.Classes.CURRENT_BRANCH),
        /** HEAD reaches it, from a commit after it, and nothing else does. */
        HEAD_ONLY("changes.reflog.heading.byHeadOnly", Theme.Classes.NOTICE),
        /** Nothing but the reflog. */
        NOTHING("changes.reflog.heading.lost", Theme.Classes.ERROR);

        private final String key;
        private final String style;

        Keeping(String key, String style) {
            this.key = key;
            this.style = style;
        }

        private List<Part> said() {
            return List.of(Part.styled(Messages.get(key), style));
        }
    }
}
