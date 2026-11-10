package kr.neev.preconditioning;

import java.util.ArrayList;
import java.util.List;

final class FavoriteKeys {
    static List<String> parse(String stored) {
        List<String> keys = new ArrayList<>();
        for (String key : stored.split("\n")) if (!key.isEmpty() && !keys.contains(key)) keys.add(key);
        return keys;
    }
    static String serialize(List<String> keys) {
        StringBuilder out = new StringBuilder();
        for (String key : keys) out.append(key).append('\n');
        return out.toString();
    }
}
