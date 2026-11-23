package net.vanfleteren.jazygit.features.changes.reflog.content;

import io.vavr.Tuple;
import io.vavr.control.Option;
import java.time.Duration;
import java.time.Instant;
import net.vanfleteren.jazygit.core.repo.ReflogEntry;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogMsg;
import net.vanfleteren.jazygit.features.changes.reflog.ReflogView;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;
import net.vanfleteren.jazygit.kernel.state.load.SelectionMsg;
import net.vanfleteren.jazygit.kernel.ui.i18n.Messages;
import net.vanfleteren.jazygit.kernel.ui.keys.Legend;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.tabs.PaneTab;
import net.vanfleteren.jazygit.kernel.ui.text.TimeText;
import net.vanfleteren.jazygit.kernel.ui.theme.Theme;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList;
import net.vanfleteren.jazygit.kernel.ui.widgets.LoadableList.Seg;
import net.vanfleteren.jazygit.kernel.ui.widgets.PaneKeys;

/**
 * The reflog of HEAD as the commits pane shows it in a tab (ADR 186): a row an entry, newest first, marked when it is
 * at HEAD's commit or its commit is lost, with the time of the entry, what was done, its subject made short and the
 * short sha; and the commit of the highlighted entry in the content panel, as for a commit of the log, under what the
 * entry is ({@link ReflogSource}).
 */
public final class ReflogShown {

    /**
     * What marks an entry at the commit HEAD is at.
     */
    static final String AT_HEAD = "◆";

    /**
     * What marks an entry whose commit no branch, tag or remote reaches, unless HEAD is at it, which keeps it.
     */
    static final String LOST = "✗";

    /**
     * What the mark of an entry at HEAD's commit means, under the keys of the reflog.
     */
    public static final Legend HEAD_LEGEND = new Legend(Scopes.REFLOG.id(), AT_HEAD, "changes.help.reflog.legend.head");

    /**
     * What the mark of an entry whose commit is lost means, under the keys of the reflog.
     */
    public static final Legend LOST_LEGEND = new Legend(Scopes.REFLOG.id(), LOST, "changes.help.reflog.legend.lost");

    private static final String REFS_HEADS = "refs/heads/";
    // Wide enough for the longest age, as "365d", and a space.
    private static final int AGE_WIDTH = 5;
    // Wide enough for the longest action but a rare one, as "rebase finish", and a space.
    private static final int ACTION_WIDTH = 14;

    private ReflogShown() {}

    /**
     * The tab of the reflog, which the commits pane always has.
     */
    public static Option<PaneTab<?>> tab(Model model) {
        ReflogView view = model.part(ReflogView.PART);
        Option<Sha> head =
                model.repo().status().toOption().flatMap(status -> status.head().commit());
        return Option.of(new PaneTab<>(
                ReflogView.TAB,
                Messages.get("changes.reflog.title"),
                Scopes.REFLOG,
                view.entries(),
                entry -> LoadableList.Item.later(1, () -> row(entry, head, view.isLost(entry), model.now())),
                emptyText(view),
                entry -> selected(model, view, entry),
                entry -> Tuple.of(entry.time(), entry.subject(), entry.sha())));
    }

    /**
     * The row of {@code entry}: marked when it is at {@code head}, else when it is {@code lost}, its age at
     * {@code now}, the action, its subject made short, which is cut first where the pane is narrow, and the short sha.
     */
    static LoadableList.Row row(ReflogEntry entry, Option<Sha> head, boolean lost, Instant now) {
        String age = TimeText.age(Duration.between(entry.time(), now));
        return LoadableList.Row.of(
                mark(head.contains(entry.sha()), lost),
                Seg.styled(pad(age, AGE_WIDTH), Theme.Classes.DIM),
                Seg.styled(pad(action(entry.action()), ACTION_WIDTH), Theme.Classes.BOLD),
                Seg.of(subject(entry.action()) + " ").shrinking(),
                Seg.styled(entry.sha().shortSha(), Theme.Classes.SHA));
    }

    /**
     * The mark of an entry, and a space: {@link #AT_HEAD} at HEAD's commit, which HEAD keeps whether a ref reaches it
     * or not, else {@link #LOST} when no ref reaches it.
     */
    private static Seg mark(boolean atHead, boolean lost) {
        if (atHead) {
            return Seg.styled(AT_HEAD + " ", Theme.Classes.CURRENT_BRANCH);
        }
        return lost ? Seg.styled(LOST + " ", Theme.Classes.ERROR) : Seg.of("  ");
    }

    /**
     * What was done, as a word or two.
     */
    static String action(Action action) {
        return switch (action) {
            case Action.Checkout _ -> Messages.get("changes.reflog.action.checkout");
            case Action.Committed(var kind, var message) ->
                switch (kind) {
                    case PLAIN -> Messages.get("changes.reflog.action.commit");
                    case AMEND -> Messages.get("changes.reflog.action.amend");
                    case INITIAL -> Messages.get("changes.reflog.action.initial");
                    case MERGE -> Messages.get("changes.reflog.action.mergeCommit");
                    case CHERRY_PICK -> Messages.get("changes.reflog.action.cherryPick");
                };
            case Action.Reset _ -> Messages.get("changes.reflog.action.reset");
            case Action.Rebase(var phase, var detail) ->
                phase.isEmpty()
                        ? Messages.get("changes.reflog.action.rebase")
                        : Messages.get("changes.reflog.action.rebaseStep", phase);
            case Action.Merge _ -> Messages.get("changes.reflog.action.merge");
            case Action.Pull(var phase, var detail) ->
                phase.map(step -> Messages.get("changes.reflog.action.pullStep", step))
                        .getOrElse(() -> Messages.get("changes.reflog.action.pull"));
            case Action.CherryPick _ -> Messages.get("changes.reflog.action.cherryPick");
            case Action.Other _ -> "";
        };
    }

    /**
     * The subject of an entry made short: where HEAD went from and to, the message of a commit, what was merged.
     */
    static String subject(Action action) {
        return switch (action) {
            case Action.Checkout(var from, var to) -> branch(from) + " → " + branch(to);
            case Action.Committed(var kind, var message) -> message;
            case Action.Reset(var to) -> branch(to);
            case Action.Rebase(var phase, var detail) -> shortRefs(detail);
            case Action.Merge(var merged, var detail) -> merged;
            case Action.Pull(var phase, var detail) -> shortRefs(detail);
            case Action.CherryPick(var message) -> message;
            case Action.Other(var subject) -> subject;
        };
    }

    /**
     * What git says when {@code view} lists no entries: that HEAD has no commit yet, that reflogs are off, or that
     * the reflog is empty.
     */
    static Option<String> emptyText(ReflogView view) {
        return Option.of(view.empty()
                .map(empty -> switch (empty) {
                    case NO_COMMITS -> Messages.get("changes.reflog.noCommits");
                    case OFF -> Messages.get("changes.reflog.off");
                })
                .getOrElse(() -> Messages.get("changes.reflog.empty")));
    }

    /**
     * What highlighting {@code entry} asks for: its commit in the content panel, unless the model shows it already;
     * then that the reflog knows it is highlighted, unless it does, which reads a page ahead of it when it is the
     * oldest highlighted.
     */
    static Option<Msg> selected(Model model, ReflogView view, Option<ReflogEntry> entry) {
        return entry.flatMap(highlighted -> PaneKeys.selectionChange(
                        Option.of(highlighted.sha()),
                        model.repoView().preview().commitDetail(),
                        SelectionMsg.CommitSelected::new)
                .orElse(() -> Option.<Msg>when(
                        !view.highlighted().contains(highlighted.place()),
                        new ReflogMsg.Highlighted(highlighted.place()))));
    }

    private static String branch(String name) {
        return name.startsWith(REFS_HEADS) ? name.substring(REFS_HEADS.length()) : name;
    }

    private static String shortRefs(String text) {
        return text.replace(REFS_HEADS, "");
    }

    /**
     * {@code text} and the spaces after it that make it {@code width} wide, and one space at least.
     */
    private static String pad(String text, int width) {
        return text + " ".repeat(Math.max(1, width - text.length()));
    }
}
