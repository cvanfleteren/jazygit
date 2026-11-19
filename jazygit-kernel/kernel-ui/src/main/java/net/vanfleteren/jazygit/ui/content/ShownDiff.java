package net.vanfleteren.jazygit.ui.content;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.DiffContext;
import net.vanfleteren.jazygit.git.model.DiffText;
import net.vanfleteren.jazygit.git.model.ParsedDiff;
import net.vanfleteren.jazygit.state.diff.Expansion;
import net.vanfleteren.jazygit.state.diff.Filtering;
import net.vanfleteren.jazygit.state.diff.Version;

/**
 * A diff as it is shown: {@code diff}, then the line {@code note}, which git did not print, if there is one, with
 * the lines {@code expansion} expanded around its hunks, folded to {@code context} lines (ADR 77), and its file
 * shown as the {@code version} of it the area shows, if it shows one (ADR 102).
 */
public record ShownDiff(
        DiffText diff,
        Option<String> note,
        Option<Expansion> expansion,
        DiffContext context,
        Option<Version> version,
        Filtering filtering) {

    /**
     * {@code diff} and its note, as git gives it.
     */
    public ShownDiff(DiffText diff, Option<String> note) {
        this(diff, note, Option.none(), DiffContext.DEFAULT, Option.none(), Filtering.NONE);
    }

    /**
     * This diff with the lines of {@code expanded} shown, folded to {@code folding} lines of context (ADR 77).
     */
    public ShownDiff expanded(Option<Expansion> expanded, DiffContext folding) {
        return new ShownDiff(diff, note, expanded, folding, version, filtering);
    }

    /**
     * This diff with its file shown as {@code shown} has it, if it shows a version of it (ADR 102).
     */
    public ShownDiff versioned(Option<Version> shown) {
        return new ShownDiff(diff, note, expansion, context, shown, filtering);
    }

    /**
     * This diff with the hunks {@code hiding} hides cut out (ADR 144).
     */
    public ShownDiff filtered(Filtering hiding) {
        return new ShownDiff(diff, note, expansion, context, version, hiding);
    }

    /**
     * The diff shown parsed, its files read whole folded, with what is known of each of them (ADR 77), its file
     * replaced by the version of it shown, if one is (ADR 102).
     */
    public Expansion.Folded folded() {
        Expansion.Folded expanded = expansion
                .map(kept -> kept.folded(diff.parsed(), context))
                .getOrElse(() -> Expansion.Folded.of(diff.parsed()));
        Expansion.Folded folded =
                filtering.hide(version.map(shown -> shown.applied(expanded)).getOrElse(expanded), diff.parsed());
        return note.map(line -> folded.withDiff(folded.diff().withNote(line))).getOrElse(folded);
    }

    /**
     * Whether nothing is shown.
     */
    public boolean isBlank() {
        return diff.isBlank() && note.isEmpty();
    }

    /**
     * The text shown, as the plain view draws it.
     */
    public String text() {
        return diff.text() + note.map(line -> line + "\n").getOrElse("");
    }

    /**
     * The diff shown parsed, as the rich view draws it; parsed when it was read, but for the note.
     */
    public ParsedDiff parsed() {
        return folded().diff();
    }
}
