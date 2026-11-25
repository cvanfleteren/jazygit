package net.vanfleteren.jazygit.kernel.ui.text;

import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.state.Model;

/**
 * The notice a model shows, as the user reads it, for the tests of the features.
 */
public final class NoticeTexts {

    private NoticeTexts() {}

    /**
     * The notice {@code model} shows, as the user sees it.
     */
    public static Option<String> noticeText(Model model) {
        return model.outcome().notice().map(NoticeText::of);
    }
}
