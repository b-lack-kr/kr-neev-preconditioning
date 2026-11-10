package kr.neev.preconditioning;

/** Advanced → Appearance. Follow car mirrors the head unit's day/night state. */
enum AppearanceMode {
    FOLLOW_CAR("차량 설정 따름"), LIGHT("밝게"), DARK("어둡게");
    final String title;
    AppearanceMode(String title) { this.title = title; }

    static AppearanceMode fromPreference(String value) {
        for (AppearanceMode mode : values()) if (mode.name().equals(value)) return mode;
        return FOLLOW_CAR;
    }
    boolean dark(boolean carIsDark) {
        return this == DARK || (this == FOLLOW_CAR && carIsDark);
    }
}
