package net.vanfleteren.jazygit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TerminalHandOverTest {

    @Test
    void textIsCopiedWithOsc52InBase64OfItsUtf8() {
        assertEquals("\u001b]52;c;bGluZSDDqQpuZXh0\u0007", TerminalHandOver.osc52("line é\nnext"));
    }
}
