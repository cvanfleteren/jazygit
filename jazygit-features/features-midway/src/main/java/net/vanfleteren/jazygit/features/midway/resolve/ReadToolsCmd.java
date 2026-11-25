package net.vanfleteren.jazygit.features.midway.resolve;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.git.files.Index;
import net.vanfleteren.jazygit.core.git.model.ConflictFile;
import net.vanfleteren.jazygit.core.git.model.ConflictTools;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads, for the resolve dialog of the file at {@code path}, what it can be handed to, where its first conflict
 * starts, and, when mergiraf runs, how it stands against the conflict git wrote of it (ADR 201).
 */
public record ReadToolsCmd(String path) implements Cmd {

    @Override
    public Msg run(Env env) {
        Index index = env.git().index();
        ConflictTools tools = index.tools().getOrElse(ConflictTools.NONE);
        return new ResolveMsg.ToolsRead(
                path,
                tools,
                index.markers(List.of(path))
                        .toOption()
                        .flatMap(List::headOption)
                        .flatMap(file -> file.lines().headOption()),
                // Only mergiraf needs it; what cannot be read is taken as edited, so it asks before losing anything.
                tools.mergiraf() ? index.conflictFile(path).getOrElse(ConflictFile.EDITED) : ConflictFile.AS_WRITTEN);
    }

    /**
     * What could not be read offers nothing more than the dialog does without it.
     */
    @Override
    public Msg failed(Failure failure) {
        return new ResolveMsg.ToolsRead(path, ConflictTools.NONE, Option.none(), ConflictFile.AS_WRITTEN);
    }
}
