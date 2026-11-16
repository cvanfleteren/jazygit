package net.vanfleteren.jazygit.ui.text;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.state.Model;

/**
 * The errors of a model as the user reads them, for the tests of the views and the features.
 */
public final class ErrorTexts {

    private ErrorTexts() {}

    /**
     * The error {@code model} shows, as the user sees it.
     */
    public static Option<String> errorText(Model model) {
        return model.outcome().error().map(ErrorText::of);
    }
}
