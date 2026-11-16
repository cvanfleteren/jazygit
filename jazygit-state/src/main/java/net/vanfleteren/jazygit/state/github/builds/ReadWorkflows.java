package net.vanfleteren.jazygit.state.github.builds;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.Cmd;
import net.vanfleteren.jazygit.state.Env;
import net.vanfleteren.jazygit.state.Failure;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.view.BuildsView.FileAt;
import net.vanfleteren.jazygit.state.view.Workflow;

/**
 * Reads the file of a workflow at the commit of a build, and those of the workflows of this repository it calls, to
 * learn which job needs which (ADR 131): from git, else from GitHub (ADR 138). A file that neither has, or that is no
 * workflow, is none: its build then shows
 * its jobs without what they wait for. It runs in the background, as nobody waits for it.
 */
public record ReadWorkflows(FileAt file) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Option<Workflow> workflow = read(env, file);
        Map<FileAt, Option<Workflow>> called = HashMap.ofEntries(workflow.toList()
                .flatMap(Workflow::called)
                .map(path -> new FileAt(file.sha(), path))
                .map(at -> Tuple.of(at, read(env, at))));
        return new BuildsViewMsg.WorkflowsRead(called.put(file, workflow));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsViewMsg.WorkflowsRead(HashMap.of(file, Option.none()));
    }

    /**
     * The workflow of the file {@code at}, from git, else from GitHub: the commit of a build of a pull request is not
     * fetched once the pull request is merged and its branch deleted, and GitHub still has it (ADR 138).
     */
    private static Option<Workflow> read(Env env, FileAt at) {
        return Sha.parse(at.sha())
                .toEither(() -> "")
                .flatMap(sha -> env.git().diffs().treeText(sha, at.path()).mapLeft(error -> ""))
                .orElse(() ->
                        env.gitHub().checks().workflowFile(at.sha(), at.path()).mapLeft(error -> ""))
                .toOption()
                .flatMap(Workflow::parse);
    }
}
