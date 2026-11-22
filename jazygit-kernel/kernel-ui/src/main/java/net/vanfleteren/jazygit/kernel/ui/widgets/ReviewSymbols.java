package net.vanfleteren.jazygit.kernel.ui.widgets;

import net.vanfleteren.jazygit.kernel.ui.theme.Theme;

/**
 * Where the review of a change stands, drawn the same by every forge (ADR 45, 175): one column wide, told apart by
 * its shape as well as its colour (ADR 45).
 */
public final class ReviewSymbols {

    public static final Symbol APPROVED = new Symbol("+", Theme.Classes.REVIEW_APPROVED);
    public static final Symbol CHANGES = new Symbol("−", Theme.Classes.REVIEW_CHANGES);
    public static final Symbol TO_REVIEW = new Symbol("?", Theme.Classes.DIM);

    private ReviewSymbols() {}
}
