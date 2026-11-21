package net.vanfleteren.jazygit.app;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Either;
import net.vanfleteren.jazygit.core.gh.StubGitHub;
import net.vanfleteren.jazygit.core.git.Git;
import net.vanfleteren.jazygit.core.git.branches.StubRemotes;
import net.vanfleteren.jazygit.core.git.model.SampleData;
import net.vanfleteren.jazygit.core.git.process.GitError;
import net.vanfleteren.jazygit.core.repo.RemoteName;
import net.vanfleteren.jazygit.features.github.GitHubFeature;
import net.vanfleteren.jazygit.features.github.GitHubServices;
import net.vanfleteren.jazygit.kernel.state.Env;
import net.vanfleteren.jazygit.kernel.state.FeatureMsg;
import net.vanfleteren.jazygit.kernel.state.Model;
import net.vanfleteren.jazygit.kernel.state.Msg;

/**
 * What the tests of the app with GitHub in it share (ADR 171): GitHub's keys send what its feature owns, and GitHub
 * applies to a repository with a remote on it.
 */
public final class GitHubKeys {

    private GitHubKeys() {}

    /**
     * {@code msg}, as a key or a timer of GitHub's sends it.
     */
    public static Msg github(Msg msg) {
        return new FeatureMsg.Owned(GitHubFeature.ID, msg);
    }

    /**
     * What {@code msg} does, without the feature that owns it, if one does.
     */
    public static Msg unwrapped(Msg msg) {
        return msg instanceof FeatureMsg.Owned(String feature, Msg owned) ? owned : msg;
    }

    /**
     * {@code git}, with its origin on GitHub, so that GitHub applies to it.
     */
    public static Git onGitHub(Git git) {
        return git.withRemotes(new StubRemotes() {
            @Override
            public Either<GitError, Map<RemoteName, String>> urls() {
                return Either.right(HashMap.of(new RemoteName("origin"), "https://github.com/owner/repo.git"));
            }
        });
    }

    /**
     * The sample repository, on GitHub, against a GitHub without pull requests.
     */
    public static Env env() {
        return new Env(onGitHub(SampleData.git())).with(GitHubServices.GH, StubGitHub.of());
    }

    /**
     * {@code model} with GitHub on, as once it found it applies.
     */
    public static Model on(Model model) {
        return net.vanfleteren.jazygit.kernel.state.Update.update(model, new FeatureMsg.Applies(GitHubFeature.ID, true))
                .model();
    }
}
