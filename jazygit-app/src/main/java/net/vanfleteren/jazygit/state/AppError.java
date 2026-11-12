package net.vanfleteren.jazygit.state;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Branch;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.git.model.FileEntry;
import net.vanfleteren.jazygit.git.model.InProgress;
import net.vanfleteren.jazygit.settings.SettingsProblem;
import net.vanfleteren.jazygit.state.operation.Operation;

/**
 * What the user is told went wrong, as data, which says itself as a {@link Phrase}; the view turns that into text. A
 * feature keeps its own errors, as a sealed interface of its own, as it keeps its messages (see {@link Msg}): adding
 * one changes nothing here. What stays here belongs to no feature, or is told by the core itself.
 */
public interface AppError {

    /**
     * What the user is told: the message, and what goes into it.
     */
    Phrase phrase();

    /**
     * Why {@code branch} has no upstream: none was set, or it was deleted on the remote.
     */
    static AppError noUpstream(Branch branch) {
        return branch.goneUpstream()
                .<AppError>map(upstream -> new UpstreamGone(branch.name(), upstream.name()))
                .getOrElse(() -> new NoUpstream(branch.name()));
    }

    /**
     * What {@code problem} with the settings file says.
     */
    static Phrase settingsProblem(SettingsProblem problem) {
        return switch (problem) {
            case SettingsProblem.Unreadable(var file, String reason) ->
                Phrase.of("settings.unreadable", String.valueOf(file), reason);
            case SettingsProblem.Invalid(var file, var keys) ->
                Phrase.of("settings.invalid", String.valueOf(file), keys.mkString(", "));
            case SettingsProblem.Unwritable(var file, String reason) ->
                Phrase.of("settings.unwritable", String.valueOf(file), reason);
        };
    }

    /**
     * What a subject, if any, and then {@code last} say, as the error key of an operation takes them.
     */
    private static Phrase[] subjectThen(Option<String> subject, Phrase last) {
        return subject.map(Phrase::verbatim).toList().append(last).toJavaArray(Phrase[]::new);
    }

    /**
     * An operation on the repository failed.
     *
     * @param subject what it was done to, e.g. the branch that was pushed, if the operation names one
     */
    record OperationFailed(Operation operation, Option<String> subject, Failure failure) implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of(operation.errorKey(), subjectThen(subject, Phrase.failure(failure)));
        }
    }

    /**
     * An operation was asked for that waits until the repository is in the middle of nothing, see
     * {@link Operation#waitsForUnfinished()}, but it is in the middle of {@code inProgress}, or {@code conflicts}
     * files still have conflicts.
     *
     * @param subject what it was to be done to, as for {@link OperationFailed}
     */
    record Unfinished(Operation operation, Option<String> subject, Option<InProgress> inProgress, int conflicts)
            implements AppError {

        /**
         * What kind of operation {@code inProgress} is, e.g. "rebase".
         */
        public static Phrase kind(InProgress inProgress) {
            return Phrase.of(
                    switch (inProgress) {
                        case InProgress.Merging m -> "inProgress.merge";
                        case InProgress.Rebasing r -> "inProgress.rebase";
                        case InProgress.CherryPicking c -> "inProgress.cherryPick";
                        case InProgress.Reverting r -> "inProgress.revert";
                    });
        }

        @Override
        public Phrase phrase() {
            return Phrase.of(
                    operation.errorKey(),
                    subjectThen(
                            subject,
                            inProgress
                                    .map(op -> Phrase.of("error.inProgress", kind(op)))
                                    .getOrElse(() -> Phrase.of("error.conflictsRemain", Phrase.count(conflicts)))));
        }
    }

    /**
     * The file or directory at {@code path}, listed with all files of the working tree, was asked to be staged or
     * discarded, but nothing in it has changed (ADR 108).
     */
    record Unchanged(String path) implements AppError {

        /**
         * That {@code files} have no changes: the {@code directory} they were asked for as, else the one file, else
         * the deepest directory that holds them all.
         */
        public static Unchanged of(List<FileEntry> files, Option<String> directory) {
            return new Unchanged(directory.getOrElse(() -> files.size() == 1
                    ? files.head().path()
                    : files.map(FileEntry::path).reduceOption(Unchanged::shared).getOrElse("")));
        }

        /**
         * The deepest directory that holds both {@code a} and {@code b}, or the root.
         */
        private static String shared(String a, String b) {
            List<String> left = List.of(a.split("/")).init();
            List<String> right = List.of(b.split("/")).init();
            return left.zip(right)
                    .takeWhile(pair -> pair._1.equals(pair._2))
                    .map(pair -> pair._1)
                    .mkString("/");
        }

        @Override
        public Phrase phrase() {
            return Phrase.of("error.unchanged", path.isEmpty() ? "/" : path);
        }
    }

    /**
     * A pull, or a reset onto the upstream, was asked for, but the branch has no upstream.
     */
    record NoUpstream(BranchName branch) implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.upstream.none", branch.value());
        }
    }

    /**
     * A pull, or a reset onto the upstream, was asked for, but the upstream of the branch was deleted on the
     * remote.
     *
     * @param upstream the short name the upstream had, e.g. {@code origin/feature}
     */
    record UpstreamGone(BranchName branch, String upstream) implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.upstream.gone", branch.value(), upstream);
        }
    }

    /**
     * Comparing a branch with the main branch was asked for, but there is no main branch to compare it with:
     * none is known, or it is gone.
     */
    record NoBaseBranch(BranchName branch) implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.divergence.noBase", branch.value());
        }
    }

    /**
     * Comparing a branch with the main branch was asked for, but it is the main branch.
     */
    record IsBaseBranch(BranchName branch) implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.divergence.isBase", branch.value());
        }
    }

    /**
     * A pull request was asked for before the pull requests were read, so it is unknown whether the branch has
     * one yet.
     */
    record PullRequestsNotRead() implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.pullRequest.notRead");
        }
    }

    /**
     * A pull request was asked for, but the pull requests could not be read, so it is unknown whether the branch
     * has one.
     */
    record PullRequestsUnavailable(Failure failure) implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.pullRequest.unavailable", Phrase.failure(failure));
        }
    }

    /**
     * The settings could not be read, or not all of them; the defaults are used instead.
     */
    record SettingsNotLoaded(SettingsProblem problem) implements AppError {

        @Override
        public Phrase phrase() {
            return settingsProblem(problem);
        }
    }

    /**
     * The settings file, read again, could not be read, or has values that are not valid; the settings in use stay for
     * those (ADR 111).
     */
    record SettingsNotReread(SettingsProblem problem) implements AppError {

        @Override
        public Phrase phrase() {
            return switch (problem) {
                case SettingsProblem.Unreadable(var file, String reason) ->
                    Phrase.of("settings.reread.unreadable", String.valueOf(file), reason);
                case SettingsProblem.Invalid(var file, var keys) ->
                    Phrase.of("settings.reread.invalid", String.valueOf(file), keys.mkString(", "));
                case SettingsProblem.Unwritable unwritable -> settingsProblem(unwritable);
            };
        }
    }

    /**
     * A changed setting could not be kept for the next session; it still holds in this one.
     */
    record SettingsNotSaved(Failure failure) implements AppError {

        @Override
        public Phrase phrase() {
            return Phrase.of("error.settings.notSaved", Phrase.failure(failure));
        }
    }
}
