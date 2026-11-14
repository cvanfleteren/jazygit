package net.vanfleteren.jazygit.ui.widgets;

import static org.junit.jupiter.api.Assertions.*;

import io.vavr.collection.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemoTest {

    @Test
    void anEqualKeyGivesTheSameValueWithoutComputingItAgain() {
        Memo<List<Integer>, List<String>> memo = new Memo<>();
        AtomicInteger computed = new AtomicInteger();

        List<String> first = memo.get(List.of(1, 2), key -> {
            computed.incrementAndGet();
            return key.map(String::valueOf);
        });
        List<String> again = memo.get(List.of(1, 2), key -> {
            computed.incrementAndGet();
            return key.map(String::valueOf);
        });

        assertSame(first, again);
        assertEquals(1, computed.get());
    }

    @Test
    void anotherKeyIsComputedAndKeptInstead() {
        Memo<Integer, String> memo = new Memo<>();
        AtomicInteger computed = new AtomicInteger();

        memo.get(1, key -> "v" + key + computed.incrementAndGet());
        String other = memo.get(2, key -> "v" + key + computed.incrementAndGet());
        String back = memo.get(1, key -> "v" + key + computed.incrementAndGet());

        assertEquals("v22", other);
        assertEquals("v13", back);
    }
}
