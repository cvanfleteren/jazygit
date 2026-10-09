package net.vanfleteren.jazygit.state;

import net.vanfleteren.jazygit.model.GitInfoProvider;

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
     * The model before anything has been loaded.
     */
    public static Model loading(String repositoryName) {
        return Model.initial(repositoryName);
    }
}
