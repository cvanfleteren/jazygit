package net.vanfleteren.jazygit.feature.branch.stack;

import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.text;

import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.StyledElement;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import io.vavr.collection.List;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.feature.branch.stack.StackChoice.Refusal;
import net.vanfleteren.jazygit.git.model.BranchName;
import net.vanfleteren.jazygit.i18n.Messages;
import net.vanfleteren.jazygit.state.Model;
import net.vanfleteren.jazygit.state.Msg;
import net.vanfleteren.jazygit.state.popup.ListCursor;
import net.vanfleteren.jazygit.state.popup.PopupMsg;
import net.vanfleteren.jazygit.ui.text.TextWidth;
import net.vanfleteren.jazygit.ui.theme.Theme;
import net.vanfleteren.jazygit.ui.widgets.DialogFrame;
import net.vanfleteren.jazygit.ui.widgets.PopupView;

/**
 * Choosing the bottom and the top of a stack to make (ADR 73): the line of the parents of the branch, the branch, and
 * the tree of the branches built on it, with what is between the bottom and the top marked. {@code b} makes the
 * highlighted branch the bottom, {@code t} the top, and {@code Enter} makes the stack; why what is chosen makes none
 * shows under the branches.
 */
final class StackPickDialog implements PopupView {

    static final String ID = "stack-pick";

    private static final String HINT = "dialog.makeStack.hint";

    // The columns a branch is indented by per branch it is built on, past the branch the popup is opened on.
    private static final int INDENT = 2;

    private final Consumer<Msg> dispatch;

    private final ListElement<?> list = list().id(ID).focusable().highlightSymbol("");

    StackPickDialog(Consumer<Msg> dispatch) {
        this.dispatch = dispatch;
    }

    @Override
    public List<String> ids() {
        return List.of(ID);
    }

    @Override
    public Option<Element> render(Model model) {
        return model.popup(StackPopups.Pick.class).map(pick -> {
            StackChoice choice = pick.choice();
            ListCursor cursor = model.listCursor();
            int line = Math.clamp(
                    cursor.line().getOrElse(choice.branch()), 0, choice.rows().size() - 1);
            List<String> lines = choice.rows()
                    .zipWithIndex()
                    .map(at -> line(choice, at._1(), at._2(), behind(model, choice, at._1())));
            list.elements(lines.zipWithIndex()
                            .map(at -> styled(choice, at._1(), at._2()))
                            .toJavaArray(StyledElement[]::new))
                    .selected(line)
                    .onKeyEvent(event -> keys(event, cursor, line, choice.rows().size()));
            Option<String> why = pick.refused()
                    .map(Messages::get)
                    .orElse(() -> pick.plan()
                            .swap()
                            .toOption()
                            .map(refusal -> why(refusal, pick.addTo().isDefined())));
            List<StyledElement<?>> under = why.map(text ->
                            List.<StyledElement<?>>of(text(""), text(text).addClass(Theme.Classes.ERROR)))
                    .getOrElse(List.empty());
            String title = pick.addTo()
                    .map(stack -> Messages.get("dialog.addToStack.title", String.valueOf(stack)))
                    .getOrElse(() -> choice.base()
                            .map(base -> Messages.get("dialog.makeStack.title", base.value()))
                            .getOrElse(() -> Messages.get("dialog.makeStack.titleNoBase")));
            int width = lines.appendAll(why)
                    .append(Messages.get(HINT))
                    .append(title)
                    .map(TextWidth::of)
                    .max()
                    .getOrElse(0);
            return DialogFrame.withHint(
                            title,
                            Math.max(DialogFrame.LIST_WIDTH, width + DialogFrame.FRAME + 2),
                            HINT,
                            List.<Element>of(list.length(choice.rows().size())).appendAll(under))
                    // The branches, why they make no stack, the blank line and the hint, plus the border.
                    .length(choice.rows().size() + under.size() + 2 + 2);
        });
    }

    /**
     * How many commits of the branch under it in {@code choice} the branch of {@code row} does not have, which GitHub
     * does not merge a stack with until it is rebased (ADR 73); none for the lowest, or one that has them all.
     */
    private static Option<String> behind(Model model, StackChoice choice, StackChoice.Row row) {
        return row.parent()
                .flatMap(below -> model.repo()
                        .behindParent(row.branch().name())
                        .map(behind -> Messages.get(
                                "dialog.makeStack.notOn",
                                String.valueOf(behind),
                                choice.rows().get(below).branch().name().value())));
    }

    /**
     * A branch as the popup lists it: its marks, indented as far as it is built on the branch, its name, its pull
     * request, and how far it is behind the branch under it, if it is.
     */
    static String line(StackChoice choice, StackChoice.Row row, int index, Option<String> behind) {
        String mark = index == choice.bottom() && index == choice.top()
                ? "▶"
                : index == choice.bottom() ? "┗" : index == choice.top() ? "┏" : choice.isChosen(index) ? "┃" : " ";
        String pullRequest = row.pullRequest()
                .map(pr -> "#" + pr.number())
                .getOrElse(() -> Messages.get("dialog.makeStack.noPullRequest"));
        String here = index == choice.branch() ? "  " + Messages.get("dialog.makeStack.thisBranch") : "";
        return mark + " " + " ".repeat(INDENT * row.depth())
                + row.branch().name().value() + "  " + pullRequest
                + behind.map(text -> "  " + text).getOrElse("")
                + here;
    }

    private static StyledElement<?> styled(StackChoice choice, String line, int index) {
        StyledElement<?> text = text(line);
        return choice.isChosen(index) ? text : text.addClass(Theme.Classes.DIM);
    }

    /**
     * Why the chosen branches make no stack, or none to add when {@code adding}.
     */
    static String why(Refusal refusal, boolean adding) {
        return switch (refusal) {
            case Refusal.NoPullRequest(List<BranchName> branches) ->
                Messages.get("dialog.makeStack.noPullRequests", names(branches));
            case Refusal.Stacked(List<BranchName> branches) ->
                Messages.get("dialog.makeStack.stacked", names(branches));
            case Refusal.TooFew() -> Messages.get(adding ? "dialog.addToStack.nothing" : "dialog.makeStack.tooFew");
        };
    }

    private static String names(List<BranchName> branches) {
        return branches.map(BranchName::value).mkString(", ");
    }

    private EventResult keys(KeyEvent event, ListCursor cursor, int line, int rows) {
        if (event.isCancel()) {
            dispatch.accept(new PopupMsg.Dismissed(StackPopups.Pick.class));
        } else if (event.isConfirm()) {
            dispatch.accept(new StackMsg.PickConfirmed());
        } else if (event.isChar('b')) {
            dispatch.accept(new StackMsg.BottomChosen(line));
        } else if (event.isChar('t')) {
            dispatch.accept(new StackMsg.TopChosen(line));
        } else if (event.isChar('j') || event.code() == KeyCode.DOWN) {
            dispatch.accept(new PopupMsg.ListCursorChanged(cursor.at(Math.min(rows - 1, line + 1))));
        } else if (event.isChar('k') || event.code() == KeyCode.UP) {
            dispatch.accept(new PopupMsg.ListCursorChanged(cursor.at(Math.max(0, line - 1))));
        } else {
            return EventResult.UNHANDLED;
        }
        return EventResult.HANDLED;
    }
}
