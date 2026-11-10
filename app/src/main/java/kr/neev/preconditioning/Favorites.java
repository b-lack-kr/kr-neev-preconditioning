package kr.neev.preconditioning;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** Ordered favorite components, stored as flattened component names in app-private prefs. */
final class Favorites {
    private static final String KEY = "favorites";
    private final SharedPreferences prefs;
    private final List<String> keys = new ArrayList<>();

    Favorites(Context context) {
        prefs = context.getSharedPreferences("launchpad", Context.MODE_PRIVATE);
        keys.addAll(FavoriteKeys.parse(prefs.getString(KEY, "")));
    }

    List<String> keys() { return keys; }

    boolean contains(String key) { return keys.contains(key); }

    /** @return true if the key is now a favorite. */
    boolean toggle(String key) {
        boolean added;
        if (keys.remove(key)) {
            added = false;
        } else {
            keys.add(key);
            added = true;
        }
        prefs.edit().putString(KEY, FavoriteKeys.serialize(keys)).apply();
        return added;
    }
}
