package net.vanfleteren.jazygit.feature.cherrypick;

import io.vavr.collection.List;
import net.vanfleteren.jazygit.git.model.Sha;
import net.vanfleteren.jazygit.state.AppError;

/**
 * What cherry-picking refuses up front.
 */
public sealed interface CherryPickRefusal extends AppError.Refusal {

    /**
     * The copied commit was asked to be cherry-picked, but the checked out branch already has it.
     */
    record AlreadyPicked(Sha sha) implements CherryPickRefusal {
        @Override
        public String key() {
            return "error.cherryPick.already";
        }

        @Override
        public List<Object> args() {
            return List.of(sha);
        }
    }
}
