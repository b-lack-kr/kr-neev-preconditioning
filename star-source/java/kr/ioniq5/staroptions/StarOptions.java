// SPDX-License-Identifier: AGPL-3.0-or-later
package kr.ioniq5.staroptions;

import hmgapi.engine.Json;
import java.io.*;
import java.lang.reflect.*;
import java.security.MessageDigest;
import java.util.*;

/** Button-settings map transaction for NEEV KOR 260902. No key binding or vehicle commands. */
public final class StarOptions {
    static final String PRODUCT = "NEEV.KOR.0000.V039.001.260902";
    static final String TXN = "/data/nemesis/neev-star-options/transaction";
    static final String MOD = "/data/hmgapi/mods/neev-star-precondition";
    static final String CHECK = "/data/hmgapi/state/map-check.json";
    static final String MAPS = "/data/hmgapi/maps";
    static final String[] APPS = {"buttonsettings"};
    static final String[] ODEX = {"/system/app/ButtonSettings.odex"};
    static final String[] MD5 = {"ea2e733ea011d05246c76e1b1a9116ea"};
    static final String[] PACKAGES = {"com.android.buttonsettings"};
    final File root;
    final boolean device;

    StarOptions(File root, boolean device) throws IOException {
        this.root = root.getCanonicalFile(); this.device = device;
    }
    File live(String path) { return new File(root, path.substring(1)); }
    File transaction(File requested) throws IOException {
        safe(requested);
        if (!requested.getAbsoluteFile().equals(live(TXN).getAbsoluteFile())) throw error("TRANSACTION_PATH");
        return requested;
    }
    static IOException error(String code) { return new IOException(code); }
    static void safe(File file) throws IOException {
        if (!file.getCanonicalFile().equals(file.getAbsoluteFile())) throw error("SYMLINK_OR_NONCANONICAL_PATH");
    }
    static void directory(File d) throws IOException { safe(d); if (!d.isDirectory()) throw error("DIRECTORY_MISSING:" + d.getName()); }
    static void regular(File f) throws IOException { safe(f); if (!f.isFile()) throw error("FILE_MISSING:" + f.getName()); }
    static void mkdir(File d) throws IOException {
        safe(d); if (!d.isDirectory() && !d.mkdirs()) throw error("MKDIR"); privateMode(d);
    }
    static void privateMode(File f) throws IOException {
        if (!f.setReadable(false, false) || !f.setWritable(false, false) || !f.setExecutable(false, false)
                || !f.setReadable(true, true) || !f.setWritable(true, true)
                || (f.isDirectory() && !f.setExecutable(true, true))) throw error("PRIVATE_PERMISSIONS");
    }
    static void publicTree(File f) throws IOException {
        safe(f);
        if (!f.setReadable(true, false) || !f.setWritable(false, false) || !f.setWritable(true, true)
                || !f.setExecutable(f.isDirectory(), false)) throw error("PUBLIC_PERMISSIONS");
        if (f.isDirectory()) for (File c : children(f)) publicTree(c);
    }
    static File[] children(File d) throws IOException {
        directory(d); File[] files = d.listFiles(); if (files == null) throw error("LIST_FAILED");
        Arrays.sort(files); return files;
    }
    static byte[] bytes(File f) throws IOException {
        regular(f); if (f.length() > 4 * 1024 * 1024) throw error("FILE_TOO_LARGE");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = new FileInputStream(f);
        try { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); }
        finally { in.close(); }
        return out.toByteArray();
    }
    static String hash(File f, String algorithm) throws IOException {
        regular(f);
        try {
            MessageDigest d = MessageDigest.getInstance(algorithm); InputStream in = new FileInputStream(f);
            try { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) != -1) d.update(b, 0, n); }
            finally { in.close(); }
            StringBuilder s = new StringBuilder(); for (byte b : d.digest()) s.append(String.format(Locale.US, "%02x", b & 255));
            return s.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    static void write(File f, byte[] value) throws IOException {
        safe(f); directory(f.getParentFile()); File tmp = new File(f.getParentFile(), f.getName() + ".new");
        safe(tmp);
        if (tmp.exists()) {
            // A crash after fsync but before rename may leave the exact pending write.
            if (!Arrays.equals(bytes(tmp), value) || !tmp.renameTo(f)) throw error("TEMP_EXISTS");
            privateMode(f); return;
        }
        FileOutputStream out = new FileOutputStream(tmp);
        try { out.write(value); out.getFD().sync(); } finally { out.close(); }
        privateMode(tmp);
        if (!tmp.renameTo(f)) throw error("ATOMIC_WRITE");
    }
    static Map<String,Object> json(File f) throws IOException {
        try { return Json.object(new String(bytes(f), "UTF-8")); }
        catch (RuntimeException e) { throw new IOException("INVALID_JSON:" + f.getName(), e); }
    }
    static void jsonWrite(File f, Map<String,Object> value) throws IOException { write(f, Json.write(value).getBytes("UTF-8")); }
    @SuppressWarnings("unchecked") static Map<String,Object> map(Object v) throws IOException {
        if (!(v instanceof Map)) throw error("EXPECTED_OBJECT"); return (Map<String,Object>) v;
    }
    @SuppressWarnings("unchecked") static List<Object> list(Object v) throws IOException {
        if (!(v instanceof List)) throw error("EXPECTED_ARRAY"); return (List<Object>) v;
    }
    static Map<String,Object> tree(File d) throws IOException {
        directory(d); Map<String,Object> out = new TreeMap<String,Object>(); walk(d, d, out); return out;
    }
    static void walk(File base, File dir, Map<String,Object> out) throws IOException {
        for (File f : children(dir)) {
            safe(f); String rel = f.getPath().substring(base.getPath().length() + 1);
            if (f.isDirectory()) { out.put(rel + "/", "directory"); walk(base, f, out); }
            else if (f.isFile()) out.put(rel, hash(f, "SHA-256")); else throw error("SPECIAL_FILE");
        }
    }
    static Object maybeTree(File d) throws IOException { safe(d); return d.exists() ? tree(d) : null; }
    static Object maybeHash(File f) throws IOException { safe(f); return f.exists() ? hash(f, "SHA-256") : null; }
    static boolean same(Object a, Object b) { return a == null ? b == null : a.equals(b); }
    static void copy(File src, File dst) throws IOException {
        safe(src); safe(dst); if (dst.exists()) throw error("COPY_DEST_EXISTS");
        if (src.isDirectory()) { mkdir(dst); for (File f : children(src)) copy(f, new File(dst, f.getName())); }
        else { regular(src); write(dst, bytes(src)); }
    }
    static void remove(File f) throws IOException {
        safe(f); if (!f.exists()) return;
        if (f.isDirectory()) for (File c : children(f)) remove(c); else regular(f);
        if (!f.delete()) throw error("REMOVE_FAILED");
    }
    static void move(File from, File to) throws IOException {
        safe(from); safe(to); if (!from.exists() || to.exists() || !from.renameTo(to)) throw error("RENAME_FAILED");
    }
    static File unusedSibling(File txn, String purpose) throws IOException {
        directory(txn.getParentFile());
        for (int i = 0; i < 10; i++) {
            File candidate = new File(txn.getParentFile(), "." + txn.getName() + "-" + purpose + "-" + UUID.randomUUID());
            safe(candidate); if (!candidate.exists()) return candidate;
        }
        throw error("UNIQUE_TRANSACTION_PATH");
    }
    String folder(File selected) { return selected.getPath().substring(live(MAPS).getPath().length() + 1); }
    File selectedMap() throws IOException {
        List<File> found = new ArrayList<File>(); findMaps(live(MAPS), 0, found);
        if (found.size() != 1) throw error("EXPECTED_ONE_MATCHING_MAP:" + found.size()); return found.get(0);
    }
    void findMaps(File dir, int depth, List<File> found) throws IOException {
        if (depth > 4) return;
        for (File f : children(dir)) if (f.isDirectory()) findMaps(f, depth + 1, found);
        else if (f.getName().equals("car.json")) {
            Map<String,Object> c = json(f);
            if (!"260902".equals(c.get("firmware"))) continue;
            for (Object p : list(c.get("products"))) if (p instanceof String && !((String)p).isEmpty() && PRODUCT.startsWith((String)p)) {
                found.add(dir); break;
            }
        }
    }
    static String property(String name) throws Exception {
        return (String) Class.forName("android.os.SystemProperties").getMethod("get", String.class).invoke(null, name);
    }
    void identity() throws Exception {
        if (!device) return;
        if (Class.forName("android.os.Build$VERSION").getField("SDK_INT").getInt(null) != 19
                || !PRODUCT.equals(property("ro.build.product")) || !"tcc803x".equals(property("ro.board.platform"))) throw error("WRONG_HEAD_UNIT");
        for (int i = 0; i < ODEX.length; i++) if (!MD5[i].equals(hash(live(ODEX[i]), "MD5"))) throw error("STOCK_HASH_MISMATCH:" + APPS[i]);
    }
    static Object applicationInfo(String pkg) throws Exception {
        Object pm = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null);
        return Class.forName("android.content.pm.IPackageManager")
            .getMethod("getApplicationInfo", String.class, int.class, int.class).invoke(pm, pkg, 0, 0);
    }
    static boolean installed(String pkg, String cls) throws Exception {
        Object application = applicationInfo(pkg);
        if (application == null || !application.getClass().getField("enabled").getBoolean(application)) return false;
        Class<?> component = Class.forName("android.content.ComponentName");
        Object name = component.getConstructor(String.class, String.class).newInstance(pkg, cls);
        Object pm = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null);
        Object info = Class.forName("android.content.pm.IPackageManager")
            .getMethod("getActivityInfo", component, int.class, int.class).invoke(pm, name, 0, 0);
        return info != null && info.getClass().getField("exported").getBoolean(info)
            && info.getClass().getField("enabled").getBoolean(info);
    }
    static String batteryState() throws Exception {
        Object application = applicationInfo("kr.neev.preconditioning");
        if (application == null) return "ABSENT";
        if (!application.getClass().getField("enabled").getBoolean(application)
                || !installed("kr.neev.preconditioning", "kr.neev.preconditioning.MainActivity")) throw error("EXISTING_BATTERY_ACTIVITY_UNAVAILABLE");
        return "PRESENT";
    }
    void apps() throws Exception {
        if (!device) return;
        if (!installed("hmgapi.app", "hmgapi.app.MainActivity")) throw error("HMGAPI_ACTIVITY_MISSING");
        boolean battery = "PRESENT".equals(batteryState());
        if (!battery) throw error("BATTERY_ACTIVITY_MISSING");
        System.out.println("BATTERY_PRESENT=" + (battery ? "1" : "0"));
    }
    void probe(File payload) throws Exception {
        directory(payload); identity(); File selected = selectedMap();
        baseline(selected);
        Map<String,Object> framework = json(new File(selected, "framework.json"));
        if (!framework.containsKey("framework.resources.get_drawable") || !framework.containsKey("framework.resources.get_text")) throw error("RESOURCE_HOOKS_MISSING");
        validatePayload(payload); apps();
        if (device) for (int i = 0; i < APPS.length; i++) {
            ClassLoader boot = ClassLoader.getSystemClassLoader().getParent();
            ClassLoader cl = (ClassLoader) Class.forName("dalvik.system.PathClassLoader")
                .getConstructor(String.class, ClassLoader.class).newInstance(ODEX[i].replace(".odex", ".apk"), boot);
            Map<String,Object> points = json(new File(payload, "maps/" + APPS[i] + ".json"));
            for (Map.Entry<String,Object> e : points.entrySet()) reflect(cl, points, e.getKey(), map(e.getValue()));
        }
        System.out.println("PROBE_OK points=31 baseline=375 expected=406 map=" + folder(selected));
    }
    void baseline(File selected) throws IOException {
        List<Object> apps = list(json(new File(selected, "car.json")).get("apps"));
        if (apps.contains("launcher") || apps.contains("homesetting")) throw error("HOME_MENU_MAPS_MUST_BE_REMOVED");
        Set<String> untouched = new HashSet<String>();
        for (Object app : apps) if (!"buttonsettings".equals(app))
            for (String point : json(new File(selected, app + ".json")).keySet())
                if (!untouched.add(point)) throw error("DUPLICATE_POINT:" + point);
        if (untouched.size() != 375) throw error("BASELINE_POINT_COUNT:" + untouched.size());
    }
    void validatePayload(File payload) throws IOException {
        Map<String,Object> c = json(new File(payload, "maps/car.json"));
        if (!"260902".equals(c.get("firmware"))) throw error("PAYLOAD_FIRMWARE");
        if (!map(c.get("files")).keySet().equals(new HashSet<String>(Arrays.asList(ODEX)))
                || !map(c.get("app_files")).keySet().equals(new HashSet<String>(Arrays.asList(APPS)))) throw error("PAYLOAD_APP_SCOPE");
        Set<String> mapFiles = new HashSet<String>();
        for (File f : children(new File(payload, "maps"))) { regular(f); mapFiles.add(f.getName()); }
        if (!mapFiles.equals(new HashSet<String>(Arrays.asList("car.json", "buttonsettings.json")))) throw error("PAYLOAD_MAP_SCOPE");
        int n = 0;
        for (int i = 0; i < APPS.length; i++) {
            if (!MD5[i].equals(map(c.get("files")).get(ODEX[i]))) throw error("PAYLOAD_STOCK_HASH");
            Map<String,Object> af = map(map(c.get("app_files")).get(APPS[i]));
            if (!PACKAGES[i].equals(af.get("package")) || !list(af.get("files")).equals(Collections.<Object>singletonList(ODEX[i]))) throw error("PAYLOAD_APP_FILES");
            Map<String,Object> points = json(new File(payload, "maps/" + APPS[i] + ".json"));
            if (points.size() != 31) throw error("PAYLOAD_POINT_COUNT");
            for (String key : points.keySet()) if (!key.startsWith(APPS[i] + ".")) throw error("PAYLOAD_POINT_SCOPE");
            n += points.size();
        }
        Map<String,Object> mod = json(new File(payload, "mod/mod.json"));
        if (!"neev-star-precondition".equals(mod.get("id"))) throw error("PAYLOAD_MOD_ID");
        Map<String,Object> provides = map(mod.get("provides"));
        if (!provides.keySet().equals(Collections.singleton("star_key")) || mod.containsKey("input") || mod.containsKey("apps")) throw error("PAYLOAD_MOD_SCOPE");
        List<Object> entries = list(provides.get("star_key"));
        if (entries.size() != 1) throw error("PAYLOAD_STAR_ENTRY");
        Map<String,Object> entry = map(entries.get(0)), activity = map(entry.get("activity"));
        if (!"precondition".equals(entry.get("id")) || !"프리컨디셔닝".equals(entry.get("title"))
                || !"kr.neev.preconditioning".equals(activity.get("package"))
                || !"kr.neev.preconditioning.MainActivity".equals(activity.get("class"))) throw error("PAYLOAD_STAR_ENTRY");
        if (n != 31) throw error("PAYLOAD_POINT_COUNT");
    }
    static Class<?> type(ClassLoader cl, String name) throws Exception {
        if (name.endsWith("[]")) return Array.newInstance(type(cl, name.substring(0, name.length() - 2)), 0).getClass();
        if ("int".equals(name)) return int.class; if ("boolean".equals(name)) return boolean.class;
        if ("long".equals(name)) return long.class; if ("void".equals(name)) return void.class;
        if ("byte".equals(name)) return byte.class; if ("short".equals(name)) return short.class;
        if ("char".equals(name)) return char.class; if ("float".equals(name)) return float.class;
        if ("double".equals(name)) return double.class;
        return Class.forName(name, false, cl);
    }
    static Class<?>[] types(ClassLoader cl, Object raw) throws Exception {
        List<Object> names = list(raw); Class<?>[] out = new Class<?>[names.size()];
        for (int i = 0; i < out.length; i++) out[i] = type(cl, String.valueOf(names.get(i))); return out;
    }
    static void reflect(ClassLoader cl, Map<String,Object> points, String id, Map<String,Object> e) throws Exception {
        if (e.containsKey("class")) { type(cl, String.valueOf(e.get("class"))); return; }
        String owner = id.substring(0, id.lastIndexOf('.'));
        Class<?> c = type(cl, String.valueOf(map(points.get(owner)).get("class")));
        if (e.containsKey("ctor")) c.getDeclaredConstructor(types(cl, e.get("ctor")));
        else if (e.containsKey("method")) {
            Method m = c.getDeclaredMethod(String.valueOf(e.get("method")), types(cl, e.get("params")));
            if (m.getReturnType() != type(cl, String.valueOf(e.get("ret"))) || Modifier.isStatic(m.getModifiers()) != Boolean.TRUE.equals(e.get("static"))) throw error("METHOD_TYPE_MISMATCH:" + id);
        } else if (e.containsKey("field")) {
            Field f = c.getDeclaredField(String.valueOf(e.get("field")));
            if (f.getType() != type(cl, String.valueOf(e.get("type"))) || Modifier.isStatic(f.getModifiers()) != Boolean.TRUE.equals(e.get("static"))) throw error("FIELD_TYPE_MISMATCH:" + id);
        } else throw error("UNKNOWN_POINT:" + id);
    }
    static void merge(File target, File payload) throws IOException {
        Map<String,Object> c = json(new File(target, "car.json")), src = json(new File(payload, "maps/car.json"));
        List<Object> apps = list(c.get("apps")); Map<String,Object> files = map(c.get("files")), afs = map(c.get("app_files"));
        Map<String,Object> missing = c.containsKey("missing") ? map(c.get("missing")) : new LinkedHashMap<String,Object>();
        for (String app : APPS) {
            File dest = new File(target, app + ".json");
            Map<String,Object> points = dest.exists() ? json(dest) : new LinkedHashMap<String,Object>();
            Map<String,Object> added = json(new File(payload, "maps/" + app + ".json"));
            for (Map.Entry<String,Object> e : added.entrySet()) {
                if (points.containsKey(e.getKey()) && !same(points.get(e.getKey()), e.getValue())) throw error("MAP_POINT_CONFLICT:" + e.getKey());
                points.put(e.getKey(), e.getValue()); missing.remove(e.getKey());
            }
            Map<String,Object> wanted = map(map(src.get("app_files")).get(app));
            if (afs.containsKey(app) && !same(afs.get(app), wanted)) throw error("MAP_APP_CONFLICT:" + app);
            afs.put(app, wanted);
            for (Object path : list(wanted.get("files"))) {
                Object hash = map(src.get("files")).get(path);
                if (files.containsKey(path) && !same(files.get(path), hash)) throw error("MAP_HASH_CONFLICT:" + path);
                files.put(String.valueOf(path), hash);
            }
            if (!apps.contains(app)) apps.add(app); jsonWrite(dest, points);
        }
        c.put("missing", missing); c.put("status", "probed"); c.put("verified", null);
        jsonWrite(new File(target, "car.json"), c);
    }
    String status(File txn) throws IOException {
        transaction(txn); if (!txn.exists()) return "ABSENT";
        return String.valueOf(metadata(txn).get("status"));
    }
    Map<String,Object> metadata(File txn) throws IOException {
        transaction(txn); Map<String,Object> m = json(new File(txn, "transaction.json"));
        if (!PRODUCT.equals(m.get("product")) || !"neev-star-options-v1".equals(m.get("format"))) throw error("TRANSACTION_IDENTITY");
        if (!Arrays.asList("PREPARED", "PUBLISHED", "COMMITTED", "RESTORED").contains(m.get("status"))) throw error("TRANSACTION_STATUS");
        Object rel = m.get("folder");
        if (!(rel instanceof String) || ((String)rel).startsWith("/") || ((String)rel).contains("..")) throw error("TRANSACTION_MAP_PATH");
        return m;
    }
    File mapAt(Map<String,Object> m) { return new File(live(MAPS), String.valueOf(m.get("folder"))); }
    void state(File txn, Map<String,Object> m, String state) throws IOException { m.put("status", state); jsonWrite(new File(txn, "transaction.json"), m); }
    void prepare(File payload, File txn) throws Exception {
        transaction(txn); probe(payload);
        Map<String,Object> payloadBefore = tree(payload);
        Object previous = null;
        if (txn.exists()) {
            Map<String,Object> old = metadata(txn);
            if (!same(old.get("payload"), payloadBefore)) throw error("TRANSACTION_PAYLOAD_CHANGED");
            if (!"RESTORED".equals(old.get("status"))) { System.out.println(old.get("status")); return; }
            if (!same(old.get("before"), maybeTree(mapAt(old))) || !same(old.get("modBefore"), maybeTree(live(MOD)))
                    || !same(old.get("checkBefore"), maybeHash(live(CHECK)))) throw error("RESTORED_STATE_CHANGED");
            previous = tree(txn);
        }
        File selected = selectedMap(); Map<String,Object> expectedMod = tree(new File(payload, "mod"));
        Object modBefore = maybeTree(live(MOD));
        if (modBefore != null && !same(modBefore, expectedMod)) throw error("UNKNOWN_EXISTING_ADDON");
        Object checkBefore = maybeHash(live(CHECK)); Map<String,Object> before = tree(selected);
        mkdir(txn.getParentFile());
        File stage = unusedSibling(txn, "prepare"), archived = null;
        if (!stage.mkdir()) throw error("STAGING_MKDIR");
        try {
            privateMode(stage);
            copy(selected, new File(stage, "before-map")); copy(selected, new File(stage, "proposal"));
            if (modBefore != null) copy(live(MOD), new File(stage, "before-mod"));
            if (checkBefore != null) copy(live(CHECK), new File(stage, "before-check.json"));
            if (!same(before, tree(new File(stage, "before-map"))) || !same(before, tree(new File(stage, "proposal")))
                    || !same(modBefore, maybeTree(new File(stage, "before-mod")))
                    || !same(checkBefore, maybeHash(new File(stage, "before-check.json")))) throw error("PREPARE_SNAPSHOT_CHANGED");
            merge(new File(stage, "proposal"), payload);
            Map<String,Object> m = new LinkedHashMap<String,Object>();
            m.put("format", "neev-star-options-v1"); m.put("product", PRODUCT); m.put("folder", folder(selected));
            m.put("payload", payloadBefore); m.put("before", before); m.put("after", tree(new File(stage, "proposal")));
            m.put("modBefore", modBefore); m.put("modAfter", expectedMod); m.put("checkBefore", checkBefore);
            Map<String,Object> proposedCar = json(new File(stage, "proposal/car.json"));
            m.put("filesAfter", proposedCar.get("files"));
            Set<String> points = new HashSet<String>();
            for (Object app : list(proposedCar.get("apps"))) for (String point : json(new File(stage, "proposal/" + app + ".json")).keySet())
                if (!points.add(point)) throw error("DUPLICATE_POINT:" + point);
            if (points.size() != 406) throw error("PROPOSED_POINT_COUNT:" + points.size());
            m.put("pointsAfter", (long) points.size());
            state(stage, m, "PREPARED");
            if (!same(before, maybeTree(selected)) || !selected.equals(selectedMap()) || !same(modBefore, maybeTree(live(MOD)))
                    || !same(checkBefore, maybeHash(live(CHECK))) || !same(payloadBefore, tree(payload))) throw error("PREPARE_STATE_CHANGED");
            if (!same(previous, maybeTree(txn))) throw error("PREPARE_TRANSACTION_CHANGED");
            // Only complete transactions reach the fixed path. Never adopt or delete an orphan stage.
            if (previous != null) { archived = unusedSibling(txn, "restored"); move(txn, archived); }
            move(stage, txn);
        } catch (Exception e) {
            try { if (archived != null && archived.exists() && !txn.exists()) move(archived, txn); }
            catch (IOException restore) { e.addSuppressed(restore); }
            try { remove(stage); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
        System.out.println("PREPARED");
    }
    void publish(File txn) throws IOException {
        Map<String,Object> m = metadata(txn); File target = mapAt(m), displaced = new File(txn, "displaced-map"), proposal = new File(txn, "proposal");
        String status = String.valueOf(m.get("status"));
        if ("PUBLISHED".equals(status) || "COMMITTED".equals(status)) {
            if (!same(m.get("after"), maybeTree(target))) throw error("LIVE_MAP_CHANGED"); System.out.println(status); return;
        }
        if (!"PREPARED".equals(status)) throw error("NOT_PREPARED");
        if (!same(m.get("modBefore"), maybeTree(live(MOD))) || !same(m.get("checkBefore"), maybeHash(live(CHECK)))) throw error("PREPARED_STATE_CHANGED");
        if (target.exists() && same(m.get("after"), tree(target)) && displaced.exists() && same(m.get("before"), tree(displaced))) {
            state(txn, m, "PUBLISHED"); System.out.println("PUBLISHED"); return;
        }
        if (!same(m.get("after"), maybeTree(proposal))) throw error("PROPOSAL_CHANGED");
        if (target.exists()) {
            if (!same(m.get("before"), tree(target)) || displaced.exists()) throw error("LIVE_MAP_CHANGED");
            move(target, displaced);
        } else if (!same(m.get("before"), maybeTree(displaced))) throw error("PARTIAL_PUBLISH_CHANGED");
        publicTree(proposal); move(proposal, target); state(txn, m, "PUBLISHED");
        System.out.println("PUBLISHED");
    }
    boolean ownCheck(Map<String,Object> m, boolean requireSuccess) throws IOException {
        File check = live(CHECK); safe(check); if (!check.exists()) return false;
        Map<String,Object> c = json(check);
        Set<String> keys = new HashSet<String>(Arrays.asList("folder", "files", "ok", "points", "failed", "date", "product"));
        if (!(c.get("points") instanceof Number)) return false;
        Number rawPoints = (Number)c.get("points"); long points = rawPoints.longValue(), expected = ((Number)m.get("pointsAfter")).longValue();
        // Stock CarMap.load omits points from changed app files. Its uncommitted report still belongs to this verification.
        if (rawPoints.doubleValue() != points || points < 0 || points > expected || (requireSuccess && points != expected)) return false;
        return keys.equals(c.keySet()) && same(m.get("folder"), c.get("folder")) && same(m.get("filesAfter"), c.get("files"))
            && PRODUCT.equals(c.get("product")) && c.get("ok") instanceof Boolean
            && c.get("failed") instanceof List && (!requireSuccess || (Boolean.TRUE.equals(c.get("ok")) && list(c.get("failed")).isEmpty()));
    }
    void commit(File txn) throws IOException {
        Map<String,Object> m = metadata(txn);
        if ("COMMITTED".equals(m.get("status"))) { installed(txn); return; }
        if (!"PUBLISHED".equals(m.get("status"))) throw error("NOT_PUBLISHED");
        if (!same(m.get("after"), maybeTree(mapAt(m))) || !same(m.get("modAfter"), maybeTree(live(MOD))) || !ownCheck(m, true)) throw error("COMMIT_STATE_MISMATCH");
        m.put("checkAfter", hash(live(CHECK), "SHA-256")); state(txn, m, "COMMITTED"); System.out.println("COMMITTED");
    }
    /** Read-only fast-path validation. A later verify-map run is a changed postimage. */
    void installed(File txn) throws IOException {
        Map<String,Object> m = metadata(txn);
        if (!"COMMITTED".equals(m.get("status")) || !m.containsKey("checkAfter")
                || !same(m.get("after"), maybeTree(mapAt(m)))
                || !same(m.get("modAfter"), maybeTree(live(MOD)))
                || !same(m.get("checkAfter"), maybeHash(live(CHECK))) || !ownCheck(m, true)) throw error("INSTALLED_STATE_CHANGED");
        System.out.println("INSTALLED_OK");
    }
    void rollback(File txn) throws IOException {
        Map<String,Object> m = metadata(txn); File target = mapAt(m), displaced = new File(txn, "displaced-map");
        Object current = maybeTree(target), mod = maybeTree(live(MOD)), check = maybeHash(live(CHECK));
        boolean before = same(current, m.get("before")), after = same(current, m.get("after"));
        if (!before && !after && !(current == null && same(m.get("before"), maybeTree(displaced)))) throw error("ROLLBACK_MAP_CHANGED");
        if (!same(mod, m.get("modBefore")) && !same(mod, m.get("modAfter"))) throw error("ROLLBACK_MOD_CHANGED");
        if (!same(check, m.get("checkBefore")) && !(m.containsKey("checkAfter") && same(check, m.get("checkAfter")))
                && (m.containsKey("checkAfter") || !ownCheck(m, false))) throw error("ROLLBACK_CHECK_CHANGED");
        if (!same(m.get("before"), tree(new File(txn, "before-map")))) throw error("BACKUP_MAP_CHANGED");
        if (m.get("modBefore") != null && !same(m.get("modBefore"), tree(new File(txn, "before-mod")))) throw error("BACKUP_MOD_CHANGED");
        if (m.get("checkBefore") != null && !same(m.get("checkBefore"), hash(new File(txn, "before-check.json"), "SHA-256"))) throw error("BACKUP_CHECK_CHANGED");
        if (!before) {
            if (!same(m.get("before"), maybeTree(displaced))) throw error("DISPLACED_MAP_CHANGED");
            if (target.exists()) move(target, new File(txn, "removed-map"));
            publicTree(displaced); move(displaced, target);
        }
        if (!same(mod, m.get("modBefore"))) {
            move(live(MOD), new File(txn, "removed-mod"));
            if (m.get("modBefore") != null) { copy(new File(txn, "before-mod"), live(MOD)); publicTree(live(MOD)); }
        }
        if (!same(check, m.get("checkBefore"))) {
            if (m.get("checkBefore") == null) remove(live(CHECK));
            else write(live(CHECK), bytes(new File(txn, "before-check.json")));
        }
        state(txn, m, "RESTORED"); System.out.println("RESTORED");
    }
    public static void main(String[] args) {
        try {
            StarOptions h = new StarOptions(new File("/"), true);
            if (args.length == 1 && "apps".equals(args[0])) { h.identity(); h.apps(); System.out.println("APPS_OK"); }
            else if (args.length == 2 && "probe".equals(args[0])) h.probe(new File(args[1]));
            else if (args.length == 3 && "prepare".equals(args[0])) h.prepare(new File(args[1]), new File(args[2]));
            else if (args.length == 2 && "publish".equals(args[0])) h.publish(new File(args[1]));
            else if (args.length == 2 && "commit".equals(args[0])) h.commit(new File(args[1]));
            else if (args.length == 2 && "installed".equals(args[0])) h.installed(new File(args[1]));
            else if (args.length == 2 && "rollback".equals(args[0])) h.rollback(new File(args[1]));
            else if (args.length == 2 && "status".equals(args[0])) System.out.println(h.status(new File(args[1])));
            else throw error("USAGE");
        } catch (Throwable e) { System.err.println("STAR_OPTIONS_ERROR " + e); System.exit(1); }
    }
}
