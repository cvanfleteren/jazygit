package net.vanfleteren.jazygit.core.git.history;

import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.Action;
import net.vanfleteren.jazygit.core.repo.ReflogEntry.CommitKind;

/**
 * What the subject of a reflog entry says was done (ADR 186). git writes these subjects untranslated, as
 * {@code GIT_REFLOG_ACTION} and its own commands give them. A branch name has no spaces, so the names in a checkout's
 * subject end at one.
 */
final class ReflogSubjects {

    private static final List<Reading> READINGS = List.of(
            new Reading(
                    Pattern.compile("^checkout: moving from (\\S+) to (\\S+)$"),
                    m -> new Action.Checkout(m.group(1), m.group(2))),
            new Reading(
                    Pattern.compile("^commit(?: \\((amend|initial|merge|cherry-pick)\\))?: (.*)$", Pattern.DOTALL),
                    m -> new Action.Committed(kind(m.group(1)), m.group(2))),
            new Reading(Pattern.compile("^reset: moving to (.*)$", Pattern.DOTALL), m -> new Action.Reset(m.group(1))),
            new Reading(
                    Pattern.compile("^rebase(?: -[im])?(?: \\(([a-z-]+)\\))?(?:: (.*))?$", Pattern.DOTALL),
                    m -> new Action.Rebase(orEmpty(m.group(1)), orEmpty(m.group(2)))),
            new Reading(
                    Pattern.compile("^merge (\\S+): (.*)$", Pattern.DOTALL),
                    m -> new Action.Merge(m.group(1), m.group(2))),
            new Reading(
                    Pattern.compile("^pull(?: [^:(]*)?(?: \\(([a-z-]+)\\))?(?:: (.*))?$", Pattern.DOTALL),
                    m -> new Action.Pull(Option.of(m.group(1)), orEmpty(m.group(2)))),
            new Reading(
                    Pattern.compile("^cherry-pick: (.*)$", Pattern.DOTALL), m -> new Action.CherryPick(m.group(1))));

    private ReflogSubjects() {}

    /**
     * What {@code subject} says was done; {@link Action.Other} when it is none of the kinds read.
     */
    static Action action(String subject) {
        return READINGS.toStream()
                .map(reading -> reading.read(subject))
                .find(Option::isDefined)
                .flatMap(Function.identity())
                .getOrElse(() -> new Action.Other(subject));
    }

    private static CommitKind kind(String qualifier) {
        return qualifier == null
                ? CommitKind.PLAIN
                : switch (qualifier) {
                    case "amend" -> CommitKind.AMEND;
                    case "initial" -> CommitKind.INITIAL;
                    case "merge" -> CommitKind.MERGE;
                    // The pattern leaves no other qualifier.
                    default -> CommitKind.CHERRY_PICK;
                };
    }

    private static String orEmpty(String group) {
        return group == null ? "" : group;
    }

    /**
     * A kind of subject: the pattern it matches, and the action made of the match.
     */
    private record Reading(Pattern pattern, Function<Matcher, Action> action) {

        Option<Action> read(String subject) {
            Matcher matcher = pattern.matcher(subject);
            return matcher.matches() ? Option.of(action.apply(matcher)) : Option.none();
        }
    }
}
