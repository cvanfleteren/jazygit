package net.vanfleteren.jazygit.feature.github.edit;

import io.vavr.collection.List;
import io.vavr.control.Either;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.gh.GhFailure;
import net.vanfleteren.jazygit.gh.model.PullRequestEdit;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.process.ExecutedCommand;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.Operation;
import net.vanfleteren.jazygit.state.OperationCmd;

/**
 * Writing the body of a pull request in the editor, and saving what was changed of it (ADR 59).
 */
public sealed interface EditPullRequestCmd extends Cmd {

    // The branch is not busy meanwhile: an edit does not touch it, so it waits for nothing done to it, nor holds
    // anything up; the popup tells it is being saved.
    Operation EDIT_PR = Operation.of("log.title.editPr", "error.editPr.failed");

    Operation EDIT_BODY = Operation.of("log.title.editPrBody", "error.editPrBody.failed");

    /**
     * The file the body is written in, in the editor; editors take it for Markdown, as GitHub does.
     */
    String FILE_NAME = "PR_BODY.md";

    /**
     * Opens {@code text} in git's editor, with the terminal handed over to it, and reports how it was saved.
     */
    record EditBody(String text) implements EditPullRequestCmd {

        @Override
        public Msg run(Env env) {
            return env.terminal()
                    .handOver(() -> env.git().editText(FILE_NAME, text))
                    .fold(this::failedWith, edited -> new EditPullRequestMsg.EditorClosed(edited._1, edited._2));
        }

        @Override
        public Msg failed(Failure failure) {
            return new EditPullRequestMsg.EditorFailed(failure);
        }
    }

    /**
     * Saves what was changed of pull request {@code number}, from {@code branch}: what {@code edit} changes of its
     * title, body and base, then, when {@code draft} is given, makes it a draft or ready for review, as gh does that
     * apart.
     */
    record SavePullRequest(BranchName branch, int number, PullRequestEdit edit, Option<Boolean> draft)
            implements EditPullRequestCmd, OperationCmd {

        @Override
        public Operation operation() {
            return EDIT_PR;
        }

        @Override
        public Option<String> subject() {
            return Option.of(branch.value());
        }

        /**
         * A new base or draft shows on the branch, so the pull requests are read again.
         */
        @Override
        public boolean changesGitHub() {
            return edit.base().isDefined() || draft.isDefined();
        }

        @Override
        public Msg run(Env env) {
            Either<GhFailure, List<ExecutedCommand>> saved =
                    edit.changes() ? env.gitHub().editPullRequest(number, edit) : Either.right(List.empty());
            return saved.fold(
                    this::failedWith,
                    edited -> draft.map(drafting -> env.gitHub()
                                    .setDraft(number, drafting)
                                    .fold(
                                            failure -> partly(edited, failure),
                                            drafted -> succeeded(edited.appendAll(drafted))))
                            .getOrElse(() -> succeeded(edited)));
        }

        /**
         * Making it a draft or ready failed, after {@code edited} ran, which saved what {@code edit} changes, if it
         * changes anything.
         */
        private Msg partly(List<ExecutedCommand> edited, GhFailure failure) {
            return new EditPullRequestMsg.SaveFailed(
                    this,
                    Failure.of(new GhFailure(failure.error(), edited.appendAll(failure.commands()))),
                    edit.changes());
        }

        @Override
        public Msg succeeded(List<ExecutedCommand> commands) {
            return new EditPullRequestMsg.Saved(this, commands);
        }

        @Override
        public Msg failed(Failure failure) {
            return new EditPullRequestMsg.SaveFailed(this, failure, false);
        }
    }
}
