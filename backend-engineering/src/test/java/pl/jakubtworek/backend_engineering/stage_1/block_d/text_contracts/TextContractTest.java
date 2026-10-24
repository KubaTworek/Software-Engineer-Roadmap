package pl.jakubtworek.backend_engineering.stage_1.block_d.text_contracts;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TextContractTest {
    @Test void visuallyEquivalentNamesNeedExplicitNormalizationButCompatibilityFormsMayChangeMeaning() {
        assertNotEquals("José", "Jose\u0301");
        assertEquals("José", TextContract.displayName(" Jose\u0301 ", 20, 80));
        assertEquals("①", TextContract.displayName("①", 20, 80)); // NFC nie zmienia symbolu w cyfrę 1.
    }
    @Test void codeUnitsCodePointsAndBytesAreDifferentBudgetsAndMalformedDataIsRejected() {
        assertEquals(2, "😀".length());
        assertEquals(1, "😀".codePointCount(0, 2));
        assertEquals(4, TextContract.utf8("😀").length);
        assertThrows(IllegalArgumentException.class, () -> TextContract.displayName("😀", 1, 3));
        assertThrows(IllegalArgumentException.class, () -> TextContract.displayName("x\uD800", 10, 40));
        assertThrows(IllegalArgumentException.class, () -> TextContract.decodeUtf8(new byte[]{(byte) 0xC3, 0x28}));
    }
    @Test void localeAndStorageOrderCannotBeImplicitPartsOfAnIdentifierOrPaginationContract() {
        assertNotEquals("TITLE".toLowerCase(Locale.ROOT), "TITLE".toLowerCase(Locale.forLanguageTag("tr")));
        List<String> javaOrder = new ArrayList<>(List.of("\uE000", "😀"));
        javaOrder.sort(Comparator.naturalOrder());
        List<String> databaseOrder = new ArrayList<>(javaOrder); databaseOrder.sort(TextContract.utf8ByteOrder());
        assertNotEquals(javaOrder, databaseOrder);
        assertEquals(List.of("\uE000", "😀"), databaseOrder);
    }
}
