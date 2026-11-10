package kr.ioniq5.staroptions;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Filesystem transaction fixtures; never contacts Android or a vehicle. */
public final class StarOptionsTest {
    static File seeds;
    static int passed;
    interface Check { void run() throws Exception; }
    static void expect(boolean value, String why) { if (!value) throw new AssertionError(why); }
    static void rejected(Check c, String why) throws Exception {
        try { c.run(); } catch (IOException expected) { return; }
        throw new AssertionError("accepted " + why);
    }
    static void test(String name, Check c) throws Exception { c.run(); passed++; System.out.println("PASS " + name); }
    static Map<String,Object> obj(Object... pairs) {
        Map<String,Object> m = new LinkedHashMap<String,Object>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String)pairs[i], pairs[i+1]); return m;
    }
    static List<Object> arr(Object... values) { return new ArrayList<Object>(Arrays.asList(values)); }
    static final class ArrayFixture {
        boolean[] checked;
        int[] titles;
        ArrayFixture(boolean[] checked, int[] titles) { this.checked=checked; this.titles=titles; }
        void setBooleanList(boolean[] checked) { this.checked=checked; }
    }
    static void put(File f, Map<String,Object> value) throws Exception { StarOptions.mkdir(f.getParentFile()); StarOptions.jsonWrite(f, value); }
    static void text(File f, String s) throws Exception { StarOptions.mkdir(f.getParentFile()); StarOptions.write(f, s.getBytes("UTF-8")); }
    static final class Fixture implements Closeable {
        final File root, payload, map, txn;
        final StarOptions h;
        final Map<String,Object> before;
        final String beforeCheck;
        Fixture() throws Exception {
            root = Files.createTempDirectory("star-options-test-").toFile().getCanonicalFile();
            h = new StarOptions(root, false); payload = new File(root, "payload"); txn = h.live(StarOptions.TXN);
            map = h.live(StarOptions.MAPS + "/hyundai/neev/kor/260902");
            Map<String,Object> framework=obj("framework.resources.get_drawable", obj("method","getDrawable"), "framework.resources.get_text",obj("method","getText"));
            for(int i=0;i<372;i++)framework.put("framework.fixture_"+i,obj("class","KeepBaseline"+i));
            put(new File(map, "framework.json"),framework);
            put(new File(map, "gpm.json"), obj("gpm.keep", obj("class", "KeepCarPlay")));
            put(new File(map, "car.json"), obj("firmware","260902", "products",arr("NEEV.KOR."), "name","Ioniq 5", "status","probed", "verified",null,
                "apps",arr("framework","gpm"), "files",obj("/system/framework/framework.odex","oldframework", "/system/app/HKMC_GPM.odex","oldgpm"),
                "app_files",obj("framework",obj("package",null,"files",arr("/system/framework/framework.odex")), "gpm",obj("package","com.google.android.projection.sink","files",arr("/system/app/HKMC_GPM.odex"))),
                "missing",obj("buttonsettings.adapter","missing", "gpm.future","keep missing"), "owner_extra",obj("keep",17L)));
            text(new File(map, "unrelated-note.txt"), "do not alter\n");
            put(h.live(StarOptions.CHECK), obj("prior","preserve this byte-for-byte"));
            StarOptions.mkdir(new File(payload, "maps"));
            Map<String,Object> files = obj(), afs = obj();
            for (int i = 0; i < StarOptions.APPS.length; i++) {
                StarOptions.copy(new File(seeds, StarOptions.APPS[i] + ".json"), new File(payload,"maps/" + StarOptions.APPS[i] + ".json"));
                files.put(StarOptions.ODEX[i], StarOptions.MD5[i]);
                afs.put(StarOptions.APPS[i],obj("package",StarOptions.PACKAGES[i],"files",arr(StarOptions.ODEX[i])));
            }
            put(new File(payload,"maps/car.json"),obj("firmware","260902","files",files,"app_files",afs));
            put(new File(payload,"mod/mod.json"), obj("id","neev-star-precondition","version","0.1.0","provides",obj("star_key",arr(obj("id","precondition","title","프리컨디셔닝","activity",obj("package","kr.neev.preconditioning","class","kr.neev.preconditioning.MainActivity"))))));
            put(new File(payload,"mod/META-HMGAPI/manifest.json"),obj("fixture","signed bytes stand-in"));
            text(new File(payload,"mod/META-HMGAPI/signature"),"fixture");
            before = StarOptions.tree(map); beforeCheck = StarOptions.hash(h.live(StarOptions.CHECK),"SHA-256");
        }
        void prepare() throws Exception { h.prepare(payload, txn); }
        void publish() throws Exception { prepare(); h.publish(txn); }
        void verify(boolean success) throws Exception {
            Map<String,Object> meta = h.metadata(txn);
            put(h.live(StarOptions.CHECK),obj("folder", meta.get("folder"),"files",meta.get("filesAfter"),"ok",success,
                "points",meta.get("pointsAfter"),"failed",success?arr():arr("buttonsettings.adapter: failed"),"date","fixture now","product",StarOptions.PRODUCT));
        }
        void install() throws Exception { StarOptions.mkdir(h.live(StarOptions.MOD).getParentFile()); StarOptions.copy(new File(payload,"mod"),h.live(StarOptions.MOD)); }
        void restored() throws Exception {
            expect(before.equals(StarOptions.tree(map)),"map preimage restored");
            expect(beforeCheck.equals(StarOptions.hash(h.live(StarOptions.CHECK),"SHA-256")),"check preimage restored");
            expect(!h.live(StarOptions.MOD).exists(),"new addon removed");
            expect("RESTORED".equals(h.status(txn)),"restore status");
        }
        public void close() throws IOException { StarOptions.remove(root); }
    }
    public static void main(String[] args) throws Exception {
        seeds = new File(args[0]).getCanonicalFile();
        test("merge preserves all unrelated map data", new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                Map<String,Object> original=StarOptions.json(new File(f.map,"car.json")); f.publish();
                Map<String,Object> merged=StarOptions.json(new File(f.map,"car.json"));
                expect(original.get("owner_extra").equals(merged.get("owner_extra")),"unknown car property");
                expect("oldgpm".equals(StarOptions.map(merged.get("files")).get("/system/app/HKMC_GPM.odex")),"gpm hash");
                expect(StarOptions.map(merged.get("missing")).containsKey("gpm.future"),"unrelated missing");
                expect(!StarOptions.map(merged.get("missing")).containsKey("buttonsettings.adapter"),"resolved missing removed");
                expect(f.before.get("gpm.json").equals(StarOptions.tree(f.map).get("gpm.json")),"gpm byte preservation");
                expect(f.before.get("unrelated-note.txt").equals(StarOptions.tree(f.map).get("unrelated-note.txt")),"extra file preservation");
                expect(f.before.get("framework.json").equals(StarOptions.tree(f.map).get("framework.json")),"all374 framework points preserved byte-for-byte");
                expect(((Number)f.h.metadata(f.txn).get("pointsAfter")).longValue()==406,"375 original plus31 buttonsettings points");
                expect(!StarOptions.list(merged.get("apps")).contains("launcher")&&!StarOptions.list(merged.get("apps")).contains("homesetting"),"no home menu apps enabled");
            }
        }});
        test("full commit and rollback are idempotent",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){f.publish();f.verify(true);f.install();f.h.commit(f.txn);f.h.commit(f.txn);
                Map<String,Object> recorded=StarOptions.tree(f.txn);f.h.installed(f.txn);expect(recorded.equals(StarOptions.tree(f.txn)),"installed verifier is read-only");
                f.h.publish(f.txn);f.h.prepare(f.payload,f.txn);f.h.rollback(f.txn);f.h.rollback(f.txn);f.restored();
                Map<String,Object> restored=StarOptions.tree(f.txn);f.prepare();expect("PREPARED".equals(f.h.status(f.txn)),"can prepare after restore");
                File[] siblings=StarOptions.children(f.txn.getParentFile());expect(siblings.length==2,"complete previous transaction archived");
                for(File sibling:siblings)if(!sibling.equals(f.txn))expect(sibling.getName().startsWith(".transaction-restored-")&&restored.equals(StarOptions.tree(sibling)),"prior restored transaction preserved");}
        }});
        test("interrupted prepare sibling does not block fresh preparation",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                File orphan=new File(f.txn.getParentFile(),".transaction-prepare-interrupted");text(new File(orphan,"before-map/partial.json"),"partial snapshot");
                Map<String,Object> orphanBefore=StarOptions.tree(orphan);expect("ABSENT".equals(f.h.status(f.txn)),"orphan is not the active transaction");
                f.prepare();expect("PREPARED".equals(f.h.status(f.txn)),"fresh complete transaction published");
                expect(orphanBefore.equals(StarOptions.tree(orphan)),"unknown interrupted stage kept untouched");
                expect(f.before.equals(StarOptions.tree(f.map))&&f.beforeCheck.equals(StarOptions.hash(f.h.live(StarOptions.CHECK),"SHA-256")),"prepare never changes live map or check");
                expect(f.before.equals(StarOptions.tree(new File(f.txn,"before-map"))),"complete backup present when metadata is visible");
                expect(StarOptions.children(f.txn.getParentFile()).length==2,"only orphan and complete transaction remain");
            }
        }});
        test("complete prepare rename survives either crash boundary",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                f.prepare();File ready=new File(f.txn.getParentFile(),".transaction-prepare-ready");StarOptions.move(f.txn,ready);
                StarOptions restarted=new StarOptions(f.root,false);expect("ABSENT".equals(restarted.status(f.txn)),"before final rename no incomplete active transaction");
                expect(f.before.equals(StarOptions.tree(f.map)),"before rename live map unchanged");
                StarOptions.move(ready,f.txn);expect("PREPARED".equals(restarted.status(f.txn)),"after atomic rename complete metadata is readable");
                restarted.publish(f.txn);restarted.rollback(f.txn);f.restored();
            }
        }});
        test("partial publication is recoverable",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){f.prepare();StarOptions.move(f.map,new File(f.txn,"displaced-map"));f.h.rollback(f.txn);f.restored();}
        }});
        test("partial publication can resume",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){f.prepare();StarOptions.move(f.map,new File(f.txn,"displaced-map"));f.h.publish(f.txn);expect("PUBLISHED".equals(f.h.status(f.txn)),"resume");f.h.rollback(f.txn);f.restored();}
        }});
        test("verify failure restores previous successful check",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){f.publish();f.verify(false);rejected(new Check(){public void run()throws Exception{f.h.commit(f.txn);}},"failed verification");f.h.rollback(f.txn);f.restored();}
        }});
        test("reduced stock verification point count rolls back before commit",new Check(){public void run()throws Exception{
            for(boolean success:new boolean[]{false,true})try(Fixture f=new Fixture()){
                f.publish();f.verify(success);f.install();Map<String,Object> report=StarOptions.json(f.h.live(StarOptions.CHECK));
                report.put("points",((Number)report.get("points")).longValue()-1);put(f.h.live(StarOptions.CHECK),report);
                rejected(new Check(){public void run()throws Exception{f.h.commit(f.txn);}},"incomplete point coverage even if stock reports success");
                f.h.rollback(f.txn);f.restored();
            }
        }});
        test("point conflict never changes live map",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){put(new File(f.map,"buttonsettings.json"),obj("buttonsettings.adapter",obj("class","Other")));
                File orphan=new File(f.txn.getParentFile(),".transaction-prepare-older");text(new File(orphan,"keep.txt"),"unknown stage");Map<String,Object> orphanBefore=StarOptions.tree(orphan);
                final Map<String,Object> before=StarOptions.tree(f.map);rejected(new Check(){public void run()throws Exception{f.prepare();}},"conflicting point");expect(before.equals(StarOptions.tree(f.map)),"live untouched");expect(!f.txn.exists(),"failed staging never published");
                expect(StarOptions.children(f.txn.getParentFile()).length==1&&orphanBefore.equals(StarOptions.tree(orphan)),"only own failed stage cleaned, orphan preserved");}
        }});
        test("duplicate point IDs across proposal apps are rejected",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                Map<String,Object> framework=StarOptions.json(new File(f.map,"framework.json"));
                framework.remove("framework.fixture_0");
                framework.put("buttonsettings.adapter",StarOptions.json(new File(f.payload,"maps/buttonsettings.json")).get("buttonsettings.adapter"));put(new File(f.map,"framework.json"),framework);
                Map<String,Object> before=StarOptions.tree(f.map);rejected(new Check(){public void run()throws Exception{f.prepare();}},"duplicate framework and buttonsettings point");
                expect(before.equals(StarOptions.tree(f.map))&&!f.txn.exists(),"duplicate point rejected before any publication");
                expect(StarOptions.children(f.txn.getParentFile()).length==0,"failed duplicate stage cleaned");
            }
        }});
        test("hash conflict never changes live map",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){Map<String,Object> c=StarOptions.json(new File(f.map,"car.json"));StarOptions.map(c.get("files")).put(StarOptions.ODEX[0],"wrong");put(new File(f.map,"car.json"),c);
                final Map<String,Object> before=StarOptions.tree(f.map);rejected(new Check(){public void run()throws Exception{f.prepare();}},"conflicting hash");expect(before.equals(StarOptions.tree(f.map)),"live untouched");}
        }});
        test("concurrent map edit blocks publish and rollback",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){f.prepare();text(new File(f.map,"someone-else.txt"),"new");
                rejected(new Check(){public void run()throws Exception{f.h.publish(f.txn);}},"changed preimage");rejected(new Check(){public void run()throws Exception{f.h.rollback(f.txn);}},"concurrent map");expect(new File(f.map,"someone-else.txt").isFile(),"preserved concurrent edit");}
        }});
        test("concurrent addon edit blocks rollback before any mutation",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){f.publish();f.verify(true);f.install();text(new File(f.h.live(StarOptions.MOD),"other.txt"),"owner change");
                final Map<String,Object> published=StarOptions.tree(f.map);rejected(new Check(){public void run()throws Exception{f.h.rollback(f.txn);}},"concurrent addon");expect(published.equals(StarOptions.tree(f.map)),"no partial rollback");}
        }});
        test("concurrent map-check edit blocks rollback",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){f.publish();put(f.h.live(StarOptions.CHECK),obj("other","check"));rejected(new Check(){public void run()throws Exception{f.h.rollback(f.txn);}},"concurrent check");}
        }});
        test("symlink source and transaction rejected",new Check(){public void run()throws Exception{
            Fixture f=new Fixture();try{
                File link=new File(f.map,"external");Files.createSymbolicLink(link.toPath(),f.payload.toPath());
                rejected(new Check(){public void run()throws Exception{f.prepare();}},"map symlink");Files.delete(link.toPath());
                StarOptions.mkdir(f.txn.getParentFile());Files.createSymbolicLink(f.txn.toPath(),f.payload.toPath());
                rejected(new Check(){public void run()throws Exception{f.h.status(f.txn);}},"transaction symlink");Files.delete(f.txn.toPath());
            }finally{f.close();}
        }});
        test("unknown addon and duplicate matching maps rejected",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){put(new File(f.h.live(StarOptions.MOD),"mod.json"),obj("id","someone-else"));rejected(new Check(){public void run()throws Exception{f.prepare();}},"unknown existing addon");StarOptions.remove(f.h.live(StarOptions.MOD));
                File duplicate=f.h.live(StarOptions.MAPS+"/hyundai/ioniq5/kor/260902");StarOptions.mkdir(duplicate.getParentFile());StarOptions.copy(f.map,duplicate);rejected(new Check(){public void run()throws Exception{f.h.probe(f.payload);}},"ambiguous matching maps");}
        }});
        test("missing resource hooks fail before writing",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){Map<String,Object> framework=StarOptions.json(new File(f.map,"framework.json"));framework.remove("framework.resources.get_drawable");framework.put("framework.extra",obj("class","keep count"));put(new File(f.map,"framework.json"),framework);
                rejected(new Check(){public void run()throws Exception{f.prepare();}},"missing drawable hook");expect(!f.txn.exists(),"nothing staged");}
        }});
        test("primitive array fields methods and constructors reflect exactly",new Check(){public void run()throws Exception{
            ClassLoader cl=StarOptionsTest.class.getClassLoader();
            expect(StarOptions.type(cl,"boolean[]")==boolean[].class,"boolean array type");
            expect(StarOptions.type(cl,"int[]")==int[].class,"integer array type");
            expect(StarOptions.type(cl,"java.lang.String[]")==String[].class,"reference array type");
            Map<String,Object> points=obj("buttonsettings.fixture",obj("class",ArrayFixture.class.getName()),
                "buttonsettings.fixture.checked",obj("field","checked","type","boolean[]","static",false),
                "buttonsettings.fixture.titles",obj("field","titles","type","int[]","static",false),
                "buttonsettings.fixture.setter",obj("method","setBooleanList","params",arr("boolean[]"),"ret","void","static",false),
                "buttonsettings.fixture.new",obj("ctor",arr("boolean[]","int[]")));
            for(Map.Entry<String,Object> point:points.entrySet())StarOptions.reflect(cl,points,point.getKey(),StarOptions.map(point.getValue()));
            rejected(new Check(){public void run()throws Exception{StarOptions.reflect(cl,points,"buttonsettings.fixture.checked",obj("field","checked","type","int[]","static",false));}},"wrong primitive array field type");
        }});
        test("previous home menu app mappings are refused before writing",new Check(){public void run()throws Exception{
            for(String forbidden:new String[]{"launcher","homesetting"})try(Fixture f=new Fixture()){
                Map<String,Object> car=StarOptions.json(new File(f.map,"car.json"));StarOptions.list(car.get("apps")).add(forbidden);put(new File(f.map,"car.json"),car);
                Map<String,Object> before=StarOptions.tree(f.map);rejected(new Check(){public void run()throws Exception{f.prepare();}},"active home menu map");
                expect(before.equals(StarOptions.tree(f.map))&&!f.txn.exists(),"home menu baseline left untouched");
            }
        }});
        test("unexpected baseline count is refused before writing",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                Map<String,Object> framework=StarOptions.json(new File(f.map,"framework.json"));framework.remove("framework.fixture_0");put(new File(f.map,"framework.json"),framework);
                Map<String,Object> before=StarOptions.tree(f.map);rejected(new Check(){public void run()throws Exception{f.prepare();}},"unexpected374 baseline");
                expect(before.equals(StarOptions.tree(f.map))&&!f.txn.exists(),"unexpected baseline left untouched");
            }
        }});
        test("payload rejects home menu points and forced key declarations",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                File extra=new File(f.payload,"maps/launcher.json");put(extra,obj("launcher.menu_list",obj("class","Forbidden")));
                rejected(new Check(){public void run()throws Exception{f.prepare();}},"extra home map payload");StarOptions.remove(extra);
                Map<String,Object> mod=StarOptions.json(new File(f.payload,"mod/mod.json"));mod.put("input",obj("key","star"));put(new File(f.payload,"mod/mod.json"),mod);
                rejected(new Check(){public void run()throws Exception{f.prepare();}},"forced key declaration");
                expect(f.before.equals(StarOptions.tree(f.map))&&!f.txn.exists(),"payload scope failure leaves baseline intact");
            }
        }});
        test("fully written atomic temp resumes but unknown temp refuses",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                File target=new File(f.root,"state.txt"),tmp=new File(f.root,"state.txt.new");
                text(tmp,"new state");StarOptions.write(target,"new state".getBytes("UTF-8"));expect(!tmp.exists(),"known pending write finished");
                text(tmp,"someone else's data");rejected(new Check(){public void run()throws Exception{StarOptions.write(target,"another state".getBytes("UTF-8"));}},"unknown pending write");
                expect("new state".equals(new String(StarOptions.bytes(target),"UTF-8")),"original write kept");
            }
        }});
        test("committed report rewrite of identical shape is not owned",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                f.publish();f.verify(true);f.install();f.h.commit(f.txn);
                Map<String,Object> changed=StarOptions.json(f.h.live(StarOptions.CHECK));changed.put("date","another verify-map run");put(f.h.live(StarOptions.CHECK),changed);
                final Map<String,Object> mapBefore=StarOptions.tree(f.map),modBefore=StarOptions.tree(f.h.live(StarOptions.MOD)),txnBefore=StarOptions.tree(f.txn);
                final String checkBefore=StarOptions.hash(f.h.live(StarOptions.CHECK),"SHA-256");
                rejected(new Check(){public void run()throws Exception{f.h.installed(f.txn);}},"changed committed check");
                rejected(new Check(){public void run()throws Exception{f.h.commit(f.txn);}},"commit cannot adopt changed check");
                rejected(new Check(){public void run()throws Exception{f.h.rollback(f.txn);}},"rollback cannot overwrite changed committed check");
                expect(mapBefore.equals(StarOptions.tree(f.map))&&modBefore.equals(StarOptions.tree(f.h.live(StarOptions.MOD)))&&txnBefore.equals(StarOptions.tree(f.txn))
                    &&checkBefore.equals(StarOptions.hash(f.h.live(StarOptions.CHECK),"SHA-256")),"all state preserved before refusal");
            }
        }});
        test("committed map change blocks installed and rollback",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                f.publish();f.verify(true);f.install();f.h.commit(f.txn);text(new File(f.map,"later-mod.json"),"another mod");
                final Map<String,Object> mapBefore=StarOptions.tree(f.map),modBefore=StarOptions.tree(f.h.live(StarOptions.MOD)),txnBefore=StarOptions.tree(f.txn);
                rejected(new Check(){public void run()throws Exception{f.h.installed(f.txn);}},"changed committed map");
                rejected(new Check(){public void run()throws Exception{f.h.rollback(f.txn);}},"rollback changed committed map");
                expect(mapBefore.equals(StarOptions.tree(f.map))&&modBefore.equals(StarOptions.tree(f.h.live(StarOptions.MOD)))&&txnBefore.equals(StarOptions.tree(f.txn)),"all state preserved");
            }
        }});
        test("committed addon change blocks installed and rollback",new Check(){public void run()throws Exception{
            try(Fixture f=new Fixture()){
                f.publish();f.verify(true);f.install();f.h.commit(f.txn);text(new File(f.h.live(StarOptions.MOD),"later-owner.txt"),"owner edit");
                final Map<String,Object> mapBefore=StarOptions.tree(f.map),modBefore=StarOptions.tree(f.h.live(StarOptions.MOD)),txnBefore=StarOptions.tree(f.txn);
                rejected(new Check(){public void run()throws Exception{f.h.installed(f.txn);}},"changed committed addon");
                rejected(new Check(){public void run()throws Exception{f.h.rollback(f.txn);}},"rollback changed committed addon");
                expect(mapBefore.equals(StarOptions.tree(f.map))&&modBefore.equals(StarOptions.tree(f.h.live(StarOptions.MOD)))&&txnBefore.equals(StarOptions.tree(f.txn)),"all state preserved");
            }
        }});
        System.out.println(passed + " star-options transaction scenarios passed (host fixtures, no vehicle access)");
    }
}
