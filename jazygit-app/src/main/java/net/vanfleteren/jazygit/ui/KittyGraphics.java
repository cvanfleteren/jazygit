package net.vanfleteren.jazygit.ui;

import dev.tamboui.buffer.Buffer;
import dev.tamboui.buffer.Cell;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.collection.HashSet;
import io.vavr.collection.List;
import io.vavr.collection.Set;
import io.vavr.collection.Traversable;
import io.vavr.collection.Vector;
import io.vavr.control.Option;
import java.util.function.Consumer;
import net.vanfleteren.jazygit.image.Picture;
import net.vanfleteren.jazygit.image.Sharp;

/**
 * Pictures drawn by the terminal itself, with the Kitty graphics protocol and its Unicode placeholders (ADR 86).
 *
 * <p>Each cell of a picture is the character {@code U+10EEEE}, with a diacritic for its row, one for its column and
 * one for the highest 8 bits of the id of its image, in the colour whose 24 bits are the rest of that id. The terminal
 * draws that part of the image there. These are cells like any other: they scroll, are cut off at the edge of a pane
 * and are covered by a popup as text is, and the image is gone where they are.
 *
 * <p>The id of an image says which it is and in how many cells it is drawn: its lowest 12 bits how many columns less
 * one (7 bits) and rows less one (5 bits), the rest a hash of its pixels. TamboUI keeps only some combining characters
 * with the character before them, among which the first {@link #USABLE} diacritics, so no more are used: the highest
 * byte of an id is less than that. After a frame is drawn, {@link #sendFor} sends each image
 * a cell of which is on the screen and that was not sent yet, before the frame reaches the terminal: once, as a
 * virtual placement of as many cells, so that nothing is drawn but by the cells.
 */
public final class KittyGraphics {

    /**
     * Deletes every image of the terminal, and what it keeps of them.
     */
    public static final String DELETE_ALL = "\u001b_Ga=D,d=A,q=2\u001b\\";

    // How many of the diacritics TamboUI keeps in the cell of the character before them.
    static final int USABLE = 100;

    // The character a cell of an image is, which the terminal replaces by that part of the image.
    private static final String PLACEHOLDER = Character.toString(0x10EEEE);
    // How many base64 characters a chunk of an image is at most, as the protocol says.
    private static final int CHUNK = 4096;
    // How the id of an image holds how many columns and rows it is drawn in.
    private static final int COLUMN_BITS = 7;
    private static final int ROW_BITS = 5;
    private static final int SIZE_BITS = COLUMN_BITS + ROW_BITS;
    // How many hashes an id has room for: the 12 bits under the highest byte, by as many as that byte can be.
    private static final int HASHES = USABLE << 24 - SIZE_BITS;
    // The diacritics that say the row, the column and the highest byte of the id of a cell of an image, the nth for
    // the nth: Kitty's rowcolumn-diacritics.txt.
    private static final Vector<String> DIACRITICS =
            Vector.of("""
            0305 030D 030E 0310 0312 033D 033E 033F 0346 034A 034B 034C 0350 0351 0352 0357 035B 0363
            0364 0365 0366 0367 0368 0369 036A 036B 036C 036D 036E 036F 0483 0484 0485 0486 0487 0592
            0593 0594 0595 0597 0598 0599 059C 059D 059E 059F 05A0 05A1 05A8 05A9 05AB 05AC 05AF 05C4
            0610 0611 0612 0613 0614 0615 0616 0617 0657 0658 0659 065A 065B 065D 065E 06D6 06D7 06D8
            06D9 06DA 06DB 06DC 06DF 06E0 06E1 06E2 06E4 06E7 06E8 06EB 06EC 0730 0732 0733 0735 0736
            073A 073D 073F 0740 0741 0743 0745 0747 0749 074A 07EB 07EC 07ED 07EE 07EF 07F0 07F1 07F3
            0816 0817 0818 0819 081B 081C 081D 081E 081F 0820 0821 0822 0823 0825 0826 0827 0829 082A
            082B 082C 082D 0951 0953 0954 0F82 0F83 0F86 0F87 135D 135E 135F 17DD 193A 1A17 1A75 1A76
            1A77 1A78 1A79 1A7A 1A7B 1A7C 1B6B 1B6D 1B6E 1B6F 1B70 1B71 1B72 1B73 1CD0 1CD1 1CD2 1CDA
            1CDB 1CE0 1DC0 1DC1 1DC3 1DC4 1DC5 1DC6 1DC7 1DC8 1DC9 1DCB 1DCC 1DD1 1DD2 1DD3 1DD4 1DD5
            1DD6 1DD7 1DD8 1DD9 1DDA 1DDB 1DDC 1DDD 1DDE 1DDF 1DE0 1DE1 1DE2 1DE3 1DE4 1DE5 1DE6 1DFE
            20D0 20D1 20D4 20D5 20D6 20D7 20DB 20DC 20E1 20E7 20E9 20F0 2CEF 2CF0 2CF1 2DE0 2DE1 2DE2
            2DE3 2DE4 2DE5 2DE6 2DE7 2DE8 2DE9 2DEA 2DEB 2DEC 2DED 2DEE 2DEF 2DF0 2DF1 2DF2 2DF3 2DF4
            2DF5 2DF6 2DF7 2DF8 2DF9 2DFA 2DFB 2DFC 2DFD 2DFE 2DFF A66F A67C A67D A6F0 A6F1 A8E0 A8E1
            A8E2 A8E3 A8E4 A8E5 A8E6 A8E7 A8E8 A8E9 A8EA A8EB A8EC A8ED A8EE A8EF A8F0 A8F1 AAB0 AAB2
            AAB3 AAB7 AAB8 AABE AABF AAC1 FE20 FE21 FE22 FE23 FE24 FE25 FE26 10A0F 10A38 1D185 1D186
            1D187 1D188 1D189 1D1AA 1D1AB 1D1AC 1D1AD 1D242 1D243 1D244
                    """.strip().split("\\s+")).map(hex -> Character.toString(Integer.parseInt(hex, 16)));

    // The ids of the images sent to the terminal, and not deleted since. Only touched on the render thread.
    private Set<Integer> sent = HashSet.empty();

    /**
     * Whether {@code picture} can be drawn as an image of the terminal: it has a sharp image, and its id can tell how
     * many cells it takes.
     */
    static boolean draws(Picture picture) {
        return picture.sharp().isDefined()
                && picture.width() <= Math.min(USABLE, 1 << COLUMN_BITS)
                && picture.rows() <= Math.min(USABLE, 1 << ROW_BITS);
    }

    /**
     * The style and symbol of the {@code n}th cell of {@code picture}, row by row, as a placeholder of its image; a
     * blank one past its end. {@code picture} has to be {@link #draws drawn} so.
     */
    static Tuple2<Style, String> cell(Picture picture, int n) {
        int column = n % picture.width();
        int row = n / picture.width();
        if (row >= picture.rows()) {
            return Tuple.of(Style.EMPTY, " ");
        }
        int id = id(picture.sharp().get(), picture.width(), picture.rows());
        return Tuple.of(
                Style.EMPTY.fg(Color.rgb(id >> 16 & 0xff, id >> 8 & 0xff, id & 0xff)), placeholder(row, column, id));
    }

    /**
     * The symbol of the cell at {@code row} and {@code column} of image {@code id}.
     */
    static String placeholder(int row, int column, int id) {
        return PLACEHOLDER + DIACRITICS.get(row) + DIACRITICS.get(column) + DIACRITICS.get(id >>> 24);
    }

    /**
     * Whether {@code cell} is a cell of an image, whose colour is not to be changed.
     */
    public static boolean isPlaceholder(Cell cell) {
        return cell.symbol().startsWith(PLACEHOLDER);
    }

    /**
     * The id of {@code sharp} drawn in {@code columns} by {@code rows} cells: never 0, which is no image.
     */
    static int id(Sharp sharp, int columns, int rows) {
        return hash(sharp) << SIZE_BITS | columns - 1 << ROW_BITS | rows - 1;
    }

    // A hash of the pixels of an image, from 1 up to HASHES, which leaves the highest byte of an id less than USABLE.
    private static int hash(Sharp sharp) {
        return 1 + Math.floorMod(sharp.rgba().hashCode(), HASHES - 1);
    }

    /**
     * Sends each image a cell of {@code buffer} is of, of those {@code shown}, that was not sent yet, to
     * {@code terminal}.
     */
    public void sendFor(Buffer buffer, Traversable<Sharp> shown, Consumer<String> terminal) {
        Set<Integer> wanted = ids(buffer).removeAll(sent);
        if (wanted.isEmpty()) {
            return;
        }
        wanted.forEach(id -> imageOf(id, shown).forEach(sharp -> {
            terminal.accept(transmission(id, sharp));
            sent = sent.add(id);
        }));
    }

    /**
     * Forgets which images were sent, as the terminal deleted them: they are sent again once drawn.
     */
    public void forget() {
        sent = HashSet.empty();
    }

    /**
     * The ids of the images {@code buffer} has a cell of.
     */
    private static Set<Integer> ids(Buffer buffer) {
        return List.range(buffer.area().top(), buffer.area().bottom())
                .flatMap(y ->
                        List.range(buffer.area().left(), buffer.area().right()).map(x -> buffer.get(x, y)))
                .filter(KittyGraphics::isPlaceholder)
                .flatMap(KittyGraphics::idOf)
                .toSet();
    }

    /**
     * The id of the image {@code cell} is of: the highest byte its third diacritic, the rest its colour.
     */
    private static Option<Integer> idOf(Cell cell) {
        String marks = cell.symbol().substring(PLACEHOLDER.length());
        Vector<String> diacritics =
                Vector.ofAll(marks.codePoints().boxed().toList()).map(Character::toString);
        Option<Integer> high =
                diacritics.drop(2).headOption().map(DIACRITICS::indexOf).filter(index -> index >= 0);
        return high.flatMap(top -> Option.ofOptional(cell.style().fg())
                .filter(Color.Rgb.class::isInstance)
                .map(Color.Rgb.class::cast)
                .map(rgb -> top << 24 | rgb.r() << 16 | rgb.g() << 8 | rgb.b()));
    }

    /**
     * Where {@code cell} is in an image, if it is a cell of one: the image's id, and the row and the column of the cell
     * in it.
     */
    static Option<Spot> spotOf(Cell cell) {
        if (!isPlaceholder(cell)) {
            return Option.none();
        }
        Vector<Integer> marks = Vector.ofAll(cell.symbol()
                        .substring(PLACEHOLDER.length())
                        .codePoints()
                        .boxed()
                        .toList())
                .map(mark -> DIACRITICS.indexOf(Character.toString(mark)));
        return marks.size() == 3 && !marks.contains(-1)
                ? idOf(cell).map(id -> new Spot(id, marks.get(0), marks.get(1)))
                : Option.none();
    }

    /**
     * How many columns image {@code id} is drawn in.
     */
    static int columnsOf(int id) {
        return (id >>> ROW_BITS & (1 << COLUMN_BITS) - 1) + 1;
    }

    /**
     * How many rows image {@code id} is drawn in.
     */
    static int rowsOf(int id) {
        return (id & (1 << ROW_BITS) - 1) + 1;
    }

    /**
     * The image of {@code shown} that image {@code id} is, by the hash of its pixels.
     */
    static Option<Sharp> imageOf(int id, Traversable<Sharp> shown) {
        return shown.find(sharp -> hash(sharp) == id >>> SIZE_BITS);
    }

    /**
     * The sequences that send {@code sharp} as image {@code id}, and place it virtually, in as many cells as the id
     * says: {@code a=T,U=1}, its RGBA pixels compressed with zlib, in chunks, and without the terminal answering,
     * {@code q=2}, which would be read as keys typed.
     */
    static String transmission(int id, Sharp sharp) {
        String data = sharp.rgba();
        int chunks = Math.max(1, Math.ceilDiv(data.length(), CHUNK));
        int columns = columnsOf(id);
        int rows = rowsOf(id);
        return List.range(0, chunks)
                .map(chunk -> {
                    String part = data.substring(chunk * CHUNK, Math.min(data.length(), (chunk + 1) * CHUNK));
                    int more = chunk < chunks - 1 ? 1 : 0;
                    String control = chunk == 0
                            ? "a=T,U=1,q=2,f=32,o=z,i=%d,s=%d,v=%d,c=%d,r=%d,m=%d"
                                    .formatted(
                                            Integer.toUnsignedLong(id),
                                            sharp.width(),
                                            sharp.height(),
                                            columns,
                                            rows,
                                            more)
                            : "q=2,m=" + more;
                    return "\u001b_G" + control + ";" + part + "\u001b\\";
                })
                .mkString();
    }

    /**
     * A cell of an image: the image's id, and the cell's row and column in it.
     */
    record Spot(int id, int row, int column) {}
}
