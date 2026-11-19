package net.vanfleteren.jazygit.features.agent.investigate;

import io.vavr.collection.List;
import io.vavr.control.Option;
import net.vanfleteren.jazygit.kernel.ui.FeatureAction;
import net.vanfleteren.jazygit.kernel.ui.PopupSlot;
import net.vanfleteren.jazygit.kernel.ui.keys.Keymap.Binding;
import net.vanfleteren.jazygit.kernel.ui.keys.Scopes;
import net.vanfleteren.jazygit.kernel.ui.widgets.PickDialog;

/**
 * {@code I} on a failed build, in every view that hosts its keys: the builds of a branch and the checks popup
 * (ADR 161).
 */
public final class InvestigateAction {

    public static final FeatureAction ACTION = new FeatureAction(
            // Without a build handed over, it still says why it does nothing (ADR 161).
            keys -> keys.add(
                    Scopes.FAILED_BUILD,
                    new Binding<>(
                            "I",
                            "agent.help.failedBuild.investigate",
                            build -> Option.of(build.<InvestigateMsg>map(InvestigateMsg.Requested::new)
                                    .getOrElse(InvestigateMsg.Unavailable::new)))),
            dispatch -> List.of(PopupSlot.returningToFocus(new PickDialog<>(InvestigateDialog.MENU, dispatch))));

    private InvestigateAction() {}
}
