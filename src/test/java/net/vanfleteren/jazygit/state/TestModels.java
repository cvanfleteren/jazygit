package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.GitInfoProvider;

import java.util.Optional;

/**
 * Builds models for UI tests by running the real {@link Program} synchronously.
 */
public final class TestModels {

    private TestModels() {
    }

    /**
     * The model after all of the provider's data has been loaded.
     */
    public static Model loaded(GitInfoProvider provider) {
        return Program.start(provider, Runnable::run, Runnable::run).model();
    }

    /**
     * The model after all of the provider's data, and the log of {@code branch}, has been loaded.
     */
    public static Model loaded(GitInfoProvider provider, String branch) {
        Program program = Program.start(provider, Runnable::run, Runnable::run);
        program.dispatch(new Msg.BranchSelected(branch));
        return program.model();
    }

    /**
     * The model before anything has been loaded.
     */
    public static Model loading(String repositoryName) {
        return Model.initial(repositoryName);
    }

    /**
     * {@code model} after an operation failed with {@code error}.
     */
    public static Model withError(Model model, String error) {
        return model.withError(Optional.of(error));
    }
}
