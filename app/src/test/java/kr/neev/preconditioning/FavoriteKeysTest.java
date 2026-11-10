package kr.neev.preconditioning;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class FavoriteKeysTest {
    @Test public void existingPreferenceEncodingAndOrderRoundTripUnchanged() {
        String old = "com.mnsoft.navi/.NaviApp\ncom.unknown/.Gone\ncom.nemesis.manager/.MainActivity\n";
        List<String> keys = FavoriteKeys.parse(old);
        assertEquals(old, FavoriteKeys.serialize(keys));
        assertEquals("com.unknown/.Gone", keys.get(1));
    }
    @Test public void emptyAndDuplicateKeysFollowOriginalBehavior() {
        assertTrue(FavoriteKeys.parse("").isEmpty());
        assertEquals("a/.A\nb/.B\n", FavoriteKeys.serialize(FavoriteKeys.parse("a/.A\n\nb/.B\na/.A\n")));
    }
}
