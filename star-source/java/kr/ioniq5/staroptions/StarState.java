// SPDX-License-Identifier: AGPL-3.0-or-later
package kr.ioniq5.staroptions;

/** Read current OEM favorite-button assignments without changing them. */
public final class StarState {
    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("No arguments");
        Class<?> binder = Class.forName("android.os.IBinder");
        Object service = Class.forName("android.os.ServiceManager").getMethod("getService", String.class)
            .invoke(null, "com.hkmc.micom.mode");
        if (service == null) throw new IllegalStateException("Mode service unavailable");
        Object manager = Class.forName("com.hkmc.mode.IModeManager$Stub")
            .getMethod("asInterface", binder).invoke(null, service);
        Object head = Class.forName("com.hkmc.mode.IModeManager").getMethod("getCustomKeyAction").invoke(manager);
        Object wheel = Class.forName("android.os.SystemProperties").getMethod("get", String.class, String.class)
            .invoke(null, "persist.sys.key.swrccustom", "");
        System.out.println("head=" + head);
        System.out.println("wheel=" + wheel);
    }
}
