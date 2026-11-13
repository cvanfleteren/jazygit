package net.vanfleteren.jazygit.feature.github.review;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.With;
import net.vanfleteren.jazygit.gh.model.NewReviewThread;
import net.vanfleteren.jazygit.gh.model.ReviewThread;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AreaId;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Update.Next;
import net.vanfleteren.jazygit.state.compose.ComposePopup;
import net.vanfleteren.jazygit.state.compose.EditorFile;
import net.vanfleteren.jazygit.state.diff.Scrolled;

/**
 * Asking for a comment on the lines of the diff of a pull request's files, a reply to one of its threads (ADR 60), or
 * the edit of a comment of one (ADR 65).
 *
 * @param number  the number of the pull request
 * @param target  what the comment goes to
 * @param where   where it goes, as the title says it: the path of the file, and the lines
 * @param quoted  the lines the comment is about, or the last comment of the thread replied to, or the comment before
 *                the one edited
 * @param text    the comment as far as it was typed, or as it came back from the editor; an edit starts out from the
 *                body of the comment
 * @param sending whether it is being sent; it can then not be changed, nor sent again
 * @param area    the id of the area of the content panel the diff is shown in
 * @param at      where that area was scrolled to, and its cursor, when it was asked for
 */
@With
public record ReviewPopup(
        int number,
        Target target,
        Where where,
        List<String> quoted,
        String text,
        boolean sending,
        AreaId area,
        Scrolled at)
        implements ComposePopup {

    // An edit starts out from a body that is not blank, which does not stop a quit until it is changed (ADR 65).
    @Override
    public boolean holdsInput() {
        return target instanceof Target.Edit edit ? !text.equals(edit.body()) : !text.isBlank();
    }

    @Override
    public EditorFile editorFile() {
        return ReviewCmd.EDITOR;
    }

    @Override
    public Next send(Model model) {
        return ReviewUpdate.confirm(model, this);
    }

    /**
     * Back on the diff, the comment dropped.
     */
    @Override
    public Model back(Model model) {
        return model.withoutPopup(ReviewPopup.class);
    }

    /**
     * What a comment goes to.
     */
    public sealed interface Target {

        /**
         * A reply to the thread with the id {@code thread}, whose first comment has the REST id {@code comment}.
         */
        record Reply(ReviewThread.Id thread, ReviewThread.Comment.RestId comment) implements Target {}

        /**
         * A thread to start on the pull request at its head commit {@code commit}, on {@code lines} of the file
         * {@code path}, or on the whole file.
         */
        record Start(Sha commit, String path, Option<NewReviewThread.Lines> lines) implements Target {}

        /**
         * The comment with the node id {@code comment} of the thread with the id {@code thread}, edited from
         * {@code body}, what it said when the popup opened (ADR 65).
         */
        record Edit(ReviewThread.Id thread, ReviewThread.Comment.Id comment, String body) implements Target {}
    }

    /**
     * Where a comment goes, as the title of the popup says it.
     *
     * @param path  the path of its file
     * @param lines the numbers of its first and its last line, if it is on lines
     * @param reply whether it is a reply
     */
    public record Where(String path, Option<Lines> lines, boolean reply) {}

    /**
     * The numbers of the first and the last line of a comment.
     */
    public record Lines(int first, int last) {}
}
