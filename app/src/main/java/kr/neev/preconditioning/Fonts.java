package kr.neev.preconditioning;

import android.content.Context;
import android.graphics.Typeface;

/** System font families preserve KitKat's Korean glyph fallback and readable weight contrast. */
final class Fonts {
    enum Weight { EXTRA_LIGHT, LIGHT, REGULAR, MEDIUM, SEMI_BOLD, BOLD }
    private static final Typeface[] loaded = new Typeface[Weight.values().length];

    static synchronized Typeface get(Context context, Weight weight) {
        Typeface face = loaded[weight.ordinal()];
        if (face == null) {
            // Bundled Latin-only Google Sans instances have no Hangul glyphs. The system families
            // use the head unit's Korean fallback; unknown family names safely fall back on API 19.
            String family = weight == Weight.EXTRA_LIGHT || weight == Weight.LIGHT
                ? "sans-serif-light" : "sans-serif";
            int style = weight == Weight.SEMI_BOLD || weight == Weight.BOLD ? Typeface.BOLD : Typeface.NORMAL;
            face = Typeface.create(family, style);
            loaded[weight.ordinal()] = face;
        }
        return face;
    }

    private Fonts() { }
}
