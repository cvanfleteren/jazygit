package net.vanfleteren.jazygit.features.branches.branch.base;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.core.repo.BranchName;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.core.repo.Sha;
import net.vanfleteren.jazygit.kernel.state.Cmd;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.Failure;
import net.vanfleteren.jazygit.kernel.state.Label;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * Reads the branches {@code branch}, at {@code tip}, could be built on, before they are offered (ADR 143); those on {@code base}, the base remote, are the
 * remote ones.
 */
public record ReadBaseCandidates(BranchName branch, Sha tip, Option<RemoteName> base) implements Cmd {

    @Override
    public Option<Label> label() {
        return Option.of(Label.of("branches.work.baseCandidates", branch.value()));
    }

    @Override
    public Msg run(Env env) {
        return env.git()
                .branches()
                .baseCandidates(branch, tip, base)
                .fold(error -> failed(Failure.of(error)), found -> new BaseMsg.CandidatesRead(branch, found));
    }

    @Override
    public Msg failed(Failure failure) {
        return new BaseMsg.CandidatesFailed(branch, failure);
    }
}
