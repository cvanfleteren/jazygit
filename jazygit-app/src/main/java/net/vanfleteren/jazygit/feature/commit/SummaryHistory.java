package net.vanfleteren.jazygit.feature.commit;

import io.vavr.collection.List;
import io.vavr.control.Option;
import lombok.AccessLevel;
import lombok.With;
import net.vanfleteren.jazygit.git.model.Commit;

/**
 * Going through the summaries of earlier commits in the summary field of the commit dialog, like a shell's history:
 * Up shows an older one, Down a newer one, and Down past the newest one brings back what was typed before the first
 * Up. An edit to a summary shown this way is dropped once Up or Down is pressed again.
 *
 * @param summaries the summaries to go through, newest first
 * @param index     the position of the summary shown among those not equal to {@code typed}, or -1 while showing
 *                  {@code typed}
 * @param typed     what was typed before the first Up
 */
public record SummaryHistory(
        List<String> summaries, @With(AccessLevel.PRIVATE) int index, String typed) {

    private static final int TYPED = -1;

    /**
     * Going through the summaries of {@code commits}, newest first, leaving out merges and older duplicates.
     */
    public static SummaryHistory of(List<Commit> commits) {
        return new SummaryHistory(
                commits.filter(commit -> !commit.isMerge()).map(Commit::message).distinct(), TYPED, "");
    }

    /**
     * The next older summary, starting from {@code current} if none is shown yet; none if there is no older one.
     * A summary equal to {@code current} is left out then, so Up always changes what is shown.
     */
    public Option<SummaryHistory> older(String current) {
        SummaryHistory from = index == TYPED ? new SummaryHistory(summaries, TYPED, current) : this;
        return Option.when(from.index + 1 < from.others().size(), () -> from.withIndex(from.index + 1));
    }

    /**
     * The next newer summary, or what was typed past the newest one; none if that is shown already.
     */
    public Option<SummaryHistory> newer() {
        return Option.when(index != TYPED, () -> withIndex(index - 1));
    }

    /**
     * The summary to show in the field.
     */
    public String shown() {
        return index == TYPED ? typed : others().get(index);
    }

    private List<String> others() {
        return summaries.filter(summary -> !summary.equals(typed));
    }
}
