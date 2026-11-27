package net.vanfleteren.jazygit.features.github.builds.screen;

import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.features.github.builds.BuildsView.FileAt;
import net.vanfleteren.jazygit.features.github.builds.ReadWorkflows;
import net.vanfleteren.jazygit.features.github.builds.Workflow;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the file of the workflow of the run on the time line at its commit, and those of the workflows of this
 * repository it calls, to learn which job needs which (ADR 131, 138, 212). It runs in the background, as nobody waits
 * for it.
 */
public record ReadScreenFiles(FileAt file) implements Cmd {

    @Override
    public boolean background() {
        return true;
    }

    @Override
    public Msg run(Env env) {
        Option<Workflow> workflow = ReadWorkflows.read(env, file);
        Map<FileAt, Option<Workflow>> called = HashMap.ofEntries(workflow.toList()
                .flatMap(Workflow::called)
                .map(path -> new FileAt(file.sha(), path))
                .map(at -> Tuple.of(at, ReadWorkflows.read(env, at))));
        return new BuildsScreenMsg.FilesRead(called.put(file, workflow));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BuildsScreenMsg.FilesRead(HashMap.of(file, Option.none()));
    }
}
