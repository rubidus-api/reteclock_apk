package com.reteclock;

import android.content.*;
import android.graphics.*;
import com.reteclock.core.*;
import com.reteclock.core.layout.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

/** One bounded staging/validation path for device and network package imports. */
final class WebImports {
    private WebImports() {}
    static void discardAbandoned(Context c) {
        File[] files=c.getCacheDir().listFiles();
        if(files!=null)for(File f:files)if(f.getName().startsWith("web-upload-")||f.getName().startsWith("web-request-"))remove(f);
    }
    static File work(Context c)throws IOException {
        File f=new File(c.getCacheDir(),"web-upload-"+UUID.randomUUID().toString());
        if(!f.mkdirs())throw new IOException("Cannot stage upload");return f;
    }
    static Context staged(final Context c,final File dir){return new ContextWrapper(c){@Override public File getCacheDir(){return dir;}};}
    static SettingsPackage.Preview read(Context c,InputStream raw)throws IOException {
        File work=work(c),upload=new File(work,"input");
        try {
            OutputStream out=new FileOutputStream(upload);byte[] b=new byte[8192];long total=0;int n;
            try{while((n=raw.read(b))!=-1){if(n==0)continue;total+=n;if(total>WebHttp.UPLOAD_LIMIT)throw new IOException("Upload too large");out.write(b,0,n);}}finally{out.close();}
            SettingsPackage.Preview p=preview(c,work,upload,null,null);p.work=work;return p;
        }catch(IOException e){remove(work);throw e;}catch(RuntimeException e){remove(work);throw new Refused("This is not a settings file or a package this app can read.");}
    }
    static SettingsPackage.Preview preview(Context c,File work,File upload,String kind,String name)throws IOException {
        File normalized=new File(work,"normalized.zip");boolean zip;
        InputStream probe=new FileInputStream(upload);try{zip=probe.read()=='P'&&probe.read()=='K';}finally{probe.close();}
        if(!zip&&kind!=null&&(kind.equals("fonts")||kind.equals("pictures")||kind.equals("sounds"))) {
            if(name==null||SafeName.complaint(name)!=null||upload.length()>WebArchive.FILE_LIMIT)throw new Refused("The file's name is not allowed, or it is larger than 32 MB.");
            validateMedia(c,kind,upload,name);ZipOutputStream z=new ZipOutputStream(new FileOutputStream(normalized));
            try{entry(z,kind.equals("pictures")?"img/"+name:kind+"/"+name,upload);}finally{z.close();}
        }else if(zip) {
            List<WebArchive.Item> items=WebArchive.unpack(upload,new File(work,"expanded"),WebArchive.EXPANDED_LIMIT);
            ZipOutputStream z=new ZipOutputStream(new FileOutputStream(normalized));Set<String> destinations=new HashSet<String>();
            try{
                for(WebArchive.Item item:items) {
                    String path=item.path,lower=path.toLowerCase(Locale.US);String media=null;
                    if(path.indexOf('/')<0&&(lower.endsWith(".ttf")||lower.endsWith(".otf")))path="fonts/"+path;
                    if(path.startsWith("font/"))path="fonts/"+path.substring(5);if(path.startsWith("imgs/"))path="img/"+path.substring(5);
                    if(path.startsWith("pictures/"))path="img/"+path.substring(9);if(path.startsWith("sound/"))path="sounds/"+path.substring(6);
                    boolean settings=lower.equals("settings.ini")||lower.equals("settings.txt")||lower.equals("reteclock.ini");
                    if(settings){path="settings.ini";String text=text(item.file);Map<String,String> values=WebSettings.readIni(text);
                        List<SettingsIni.Entry> es=new ArrayList<SettingsIni.Entry>();for(Map.Entry<String,String> e:values.entrySet()){WebSettings.Field f=WebSettings.field(e.getKey());if(SettingsIni.sectionOf(e.getKey())!=null)es.add(new SettingsIni.Entry(e.getKey(),f.kind,e.getValue(),f.page));}
                        if(!destinations.add(path))throw new IOException("Duplicate settings document");z.putNextEntry(new ZipEntry(path));z.write(SettingsIni.write(es).getBytes("UTF-8"));z.closeEntry();continue;}
                    String[] skin=LayoutFiles.entryInFolder(path);
                    if(skin!=null){if(skin[1].equals(LayoutFiles.PRESET_FILE)){WebSettings.layouts(text(item.file),false);}else{validateMedia(c,"pictures",item.file,skin[1]);}}
                    else if(LayoutFiles.entryName(path)!=null){WebSettings.layouts(text(item.file),false);}
                    else if(path.startsWith("fonts/")&&path.indexOf('/',6)<0)media="fonts";
                    else if(path.startsWith("img/")&&path.indexOf('/',4)<0)media="pictures";
                    else if(path.startsWith("sounds/")&&path.indexOf('/',7)<0)media="sounds";
                    else continue;
                    if(media!=null)validateMedia(c,media,item.file,path.substring(path.indexOf('/')+1));
                    if(!destinations.add(path))throw new IOException("Duplicate media destination");entry(z,path,item.file);
                }
                if(destinations.isEmpty()){z.putNextEntry(new ZipEntry("README.txt"));z.write("No recognized entries.\n".getBytes("UTF-8"));z.closeEntry();}
            }catch(IllegalArgumentException e){throw new Refused("A layout in the package is not valid.");}finally{z.close();}
        }else {
            String text=text(upload);WebSettings.readIni(text);
            InputStream in=new FileInputStream(upload);try{SettingsPackage.Preview p=SettingsPackage.readUnchecked(staged(c,work),in);filter(p);return p;}finally{in.close();}
        }
        InputStream in=new FileInputStream(normalized);try{SettingsPackage.Preview p=SettingsPackage.readUnchecked(staged(c,work),in);filter(p);return p;}finally{in.close();}
    }
    private static void filter(SettingsPackage.Preview preview)throws IOException {
        Map<String,String> raw=new LinkedHashMap<String,String>();for(SettingsIni.Entry e:preview.settings.entries){if(raw.containsKey(e.key))throw new IOException("Duplicate setting");raw.put(e.key,e.value);}
        Map<String,String> valid;try{valid=WebSettings.validate(raw,true);}catch(IllegalArgumentException e){throw new Refused(e.getMessage()+".");}
        List<SettingsIni.Entry> typed=new ArrayList<SettingsIni.Entry>();
        for(SettingsIni.Entry entry:preview.settings.entries)if(valid.containsKey(entry.key)) {
            WebSettings.Field field=WebSettings.field(entry.key);
            typed.add(new SettingsIni.Entry(entry.key,field.kind,valid.get(entry.key),field.page,entry.notes));
        }
        preview.settings.entries.clear();preview.settings.entries.addAll(typed);
    }
    private static String text(File file)throws IOException{InputStream in=new FileInputStream(file);try{return WebHttp.utf8(WebHttp.bounded(in,WebHttp.TEXT_LIMIT));}finally{in.close();}}
    private static void validateMedia(Context c,String kind,File file,String name)throws IOException {
        if(SafeName.complaint(name)!=null)throw new Refused("A file's name is not allowed: "+Refused.quote(name)+".");
        String who=Refused.quote(name);
        if(file.length()==0||file.length()>WebArchive.FILE_LIMIT)throw new Refused(who+" is empty or larger than 32 MB.");
        try {
            if(kind.equals("fonts")){String lower=name.toLowerCase(Locale.US);if(!lower.endsWith(".ttf")&&!lower.endsWith(".otf"))throw new Refused(who+" is not a .ttf or .otf font.");
                InputStream in=new FileInputStream(file);byte[] h=new byte[4];try{if(in.read(h)!=4)throw new Refused(who+" is not a font.");}finally{in.close();}
                if(!((h[0]==0&&h[1]==1&&h[2]==0&&h[3]==0)||(h[0]=='O'&&h[1]=='T'&&h[2]=='T'&&h[3]=='O')))throw new Refused(who+" is not a TrueType or OpenType font.");Typeface.createFromFile(file);
            }else if(kind.equals("pictures")) {
                WebMedia.checkAnimation(file);
                BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);
                if(o.outWidth<1||o.outHeight<1)throw new Refused(who+" is not a picture this device can read.");
                if(o.outWidth>8192||o.outHeight>8192||(long)o.outWidth*o.outHeight>16000000L)throw new Refused(who+" is too large a picture ("+o.outWidth+"×"+o.outHeight+"); the limit is 16 million pixels.");
                String lower=name.toLowerCase(Locale.US);if(lower.endsWith(".gif")&&((long)o.outWidth*o.outHeight>1000000L||file.length()>4L*1024*1024))throw new Refused(who+" is too large an animation; the limit is one million pixels and 4 MB.");
                o.inJustDecodeBounds=false;o.inSampleSize=1;while(o.outWidth/o.inSampleSize>512||o.outHeight/o.inSampleSize>512)o.inSampleSize*=2;
                Bitmap b=BitmapFactory.decodeFile(file.getPath(),o);if(b==null)throw new Refused(who+" is not a picture this device can read.");b.recycle();
            }else if(kind.equals("sounds")&&!SoundPlayer.playable(file))throw new Refused(who+" is not a sound this device can play.");
        }catch(Refused refused){throw new Refused(refused.getMessage().startsWith(who)?refused.getMessage():who+": "+refused.getMessage());
        }catch(OutOfMemoryError e){throw new Refused(who+" needs more memory than this device has.");}catch(RuntimeException e){throw new Refused(who+" cannot be read by this device.");}
    }
    private static void entry(ZipOutputStream zip,String path,File file)throws IOException{zip.putNextEntry(new ZipEntry(path));InputStream in=new FileInputStream(file);try{WebHttp.copy(in,zip,file.length());}finally{in.close();}zip.closeEntry();}
    static String describe(SettingsPackage.Preview p){return "Validated: "+p.settings.entries.size()+" settings, "+p.fonts.size()+" fonts, "+p.images.size()+" pictures, "+p.sounds.size()+" sounds, "+p.layouts.size()+" themes. Import stops a running timer. Existing layouts are merged. Unavailable file references use the existing import policy.";}
    static WebServer.Response export(Context c,Map<String,String> options)throws Exception {
        final File work=work(c);File output=new File(work,"export");String kind=options.get("kind");
        try {
            OutputStream raw=new FileOutputStream(output);OutputStream limited=new FilterOutputStream(raw){long count;@Override public void write(int b)throws IOException{if(++count>WebHttp.UPLOAD_LIMIT)throw new IOException("Export exceeds 64 MiB; select fewer files");out.write(b);}@Override public void write(byte[] b,int off,int len)throws IOException{if((count+=len)>WebHttp.UPLOAD_LIMIT)throw new IOException("Export exceeds 64 MiB; select fewer files");out.write(b,off,len);}};
            Set<String> sections=new HashSet<String>(Arrays.asList(SettingsIni.SECTIONS));if(options.containsKey("sections"))sections=new HashSet<String>(Arrays.asList(options.get("sections").split(",")));
            String download;String type;
            try {
                if("ini".equals(kind)){limited.write(SettingsPackage.settingsText(c,sections).getBytes("UTF-8"));download="reteclock-settings.ini";type="text/plain; charset=utf-8";}
                else if("log".equals(kind)){if(TimerLog.isEmpty(c))emptyZip(limited,"No timer records.\n");else TimerLog.exportTo(c,limited);download="reteclock-timer-log.zip";type="application/zip";}
                else if("fonts".equals(kind)){ZipOutputStream z=new ZipOutputStream(limited);z.putNextEntry(new ZipEntry("README.txt"));z.write("ReteClock font library.\n".getBytes("UTF-8"));z.closeEntry();for(FontLibrary.Entry e:Settings.fonts(c).list())entry(z,"fonts/"+e.name,Settings.fonts(c).file(e.name));z.finish();download="reteclock-fonts.zip";type="application/zip";}
                else {if("themes".equals(kind)){sections.clear();sections.add("clock");}
                    boolean[] files={ !"false".equals(options.get("fonts")),!"false".equals(options.get("pictures")),!"false".equals(options.get("sounds"))};
                    if("themes".equals(kind))files=new boolean[]{false,false,false};SettingsPackage.write(c,limited,sections,files);download="themes".equals(kind)?"reteclock-themes.zip":"reteclock-settings.zip";type="application/zip";}
            }finally{limited.close();}
            WebServer.Response response=WebServer.Response.file(type,output,download);response.cleanup=new Runnable(){public void run(){remove(work);}};return response;
        }catch(Exception e){remove(work);throw e;}
    }
    private static void emptyZip(OutputStream out,String note)throws IOException {
        ZipOutputStream zip=new ZipOutputStream(out);zip.putNextEntry(new ZipEntry("README.txt"));zip.write(note.getBytes("UTF-8"));zip.closeEntry();zip.finish();
    }
    /** Imports only add files under free names. Journal old keys and filenames before any writes. */
    static synchronized SettingsPackage.Result apply(Context c,SettingsPackage.Preview p,Set<String> sections,boolean[] files) {
        if(p.isEmpty()){if(p.work!=null)remove(p.work);return new SettingsPackage.Result();}
        File journal=new File(c.getFilesDir(),"web-import-journal");
        try {
            // Room for every file that will be copied into the libraries, asked before the first.
            long needed=WebHttp.SPARE_BYTES;
            for(List<SettingsPackage.Carried> list:Arrays.asList(p.fonts,p.images,p.sounds))for(SettingsPackage.Carried carried:list)if(carried.file!=null)needed+=carried.file.length();
            if(!WebHttp.roomFor(c.getFilesDir(),needed))throw new Refused("There is not enough free space on the clock for these files.");
            begin(c);
            SettingsPackage.Result result=SettingsPackage.applyUnchecked(c,p,sections,files);
            Map<String,String> actual=new HashMap<String,String>();for(Map.Entry<String,Object> e:Settings.everything(c).entrySet())actual.put(e.getKey(),String.valueOf(e.getValue()));WebSettings.dependencies(actual);
            finish(c);return result;
        }catch(Exception failure){
            String why=failure instanceof Refused?failure.getMessage()+" ":"";
            throw new IllegalArgumentException(recover(c)?why+"Nothing was imported; the settings are as they were."
                    :why+"The import failed and could not be undone automatically; check the settings.");
        }
        finally{if(p.work!=null)remove(p.work);}
    }
    static synchronized void begin(Context c)throws Exception {
        File journal=new File(c.getFilesDir(),"web-import-journal");
            if(journal.exists())recover(c);
            if(!journal.mkdirs())throw new IOException("Cannot create import journal");
            JSONObject old=new JSONObject();JSONArray prefs=new JSONArray();
            for(Map.Entry<String,?> e:Settings.all(c).entrySet()){Object v=e.getValue();JSONArray a=new JSONArray();a.put(e.getKey());a.put(v instanceof Boolean?"b":v instanceof Integer?"i":v instanceof Long?"l":v instanceof Float?"f":"s");a.put(v);prefs.put(a);}old.put("prefs",prefs);
            JSONArray names=new JSONArray();for(String path:liveFiles(c))names.put(path);old.put("files",names);
            File marker=new File(journal,"before.json"),pending=new File(journal,"before.pending");FileOutputStream out=new FileOutputStream(pending);try{out.write(old.toString().getBytes("UTF-8"));out.getFD().sync();}finally{out.close();}
            if(!pending.renameTo(marker))throw new IOException("Cannot save import journal");
    }
    static synchronized void finish(Context c)throws IOException {
        File journal=new File(c.getFilesDir(),"web-import-journal");
        if(!new File(journal,"before.json").delete())throw new IOException("Cannot finish import journal");remove(journal);
    }
    /**
     * Undoes an import that was interrupted, from its journal — and never throws.
     *
     * This runs as the app starts. It used to throw when the journal could not be read, "to keep
     * the journal intact", and since every screen starts through here the app then died on every
     * start until its data was cleared (review of 2026-10-06, seen on API 19). A journal that
     * cannot be read is now set aside under {@link #DAMAGED}, where it is still there to be looked
     * at, and the app goes on with the settings it has.
     *
     * @return false when a journal was found and could not be applied
     */
    static synchronized boolean recover(Context c) {
        File journal=new File(c.getFilesDir(),"web-import-journal"),marker=new File(journal,"before.json");
        if(!marker.isFile()){remove(journal);return true;}
        try {
            InputStream journalIn=new FileInputStream(marker);String journalText;
            try{journalText=WebHttp.utf8(WebHttp.bounded(journalIn,8*1024*1024));}finally{journalIn.close();}
            JSONObject old=new JSONObject(journalText);JSONArray files=old.getJSONArray("files");Set<String> keep=new HashSet<String>();for(int i=0;i<files.length();i++)keep.add(files.getString(i));
            JSONArray prefs=old.getJSONArray("prefs");
            // Everything is read before anything is changed: a journal half understood is not half applied.
            List<Object[]> rows=new ArrayList<Object[]>();
            for(int i=0;i<prefs.length();i++){JSONArray a=prefs.getJSONArray(i);String key=a.getString(0),type=a.getString(1);
                rows.add(new Object[]{key,type,type.equals("b")?(Object)Boolean.valueOf(a.getBoolean(2)):type.equals("i")?(Object)Integer.valueOf(a.getInt(2)):type.equals("l")?(Object)Long.valueOf(a.getLong(2)):type.equals("f")?(Object)Float.valueOf((float)a.getDouble(2)):(Object)a.getString(2)});}
            for(String path:liveFiles(c))if(!keep.contains(path))new File(c.getFilesDir(),path).delete();
            SharedPreferences.Editor edit=Settings.edit(c).clear();
            for(Object[] row:rows){String key=(String)row[0];Object v=row[2];if(v instanceof Boolean)edit.putBoolean(key,(Boolean)v);else if(v instanceof Integer)edit.putInt(key,(Integer)v);else if(v instanceof Long)edit.putLong(key,(Long)v);else if(v instanceof Float)edit.putFloat(key,(Float)v);else edit.putString(key,(String)v);}
            if(edit.commit()){remove(journal);WakeBells.reconcile(c);}
            return true;
        }catch(Exception unreadable){
            File aside=new File(c.getFilesDir(),DAMAGED);remove(aside);
            if(!journal.renameTo(aside))remove(journal);
            return false;
        }
    }
    /** Where a journal that could not be read is kept: one, the latest, out of the way of the next import. */
    static final String DAMAGED="web-import-journal.damaged";
    private static Set<String> liveFiles(Context c){Set<String> out=new HashSet<String>();for(String d:new String[]{"fonts","images","sounds","layouts"})collect(new File(c.getFilesDir(),d),d,out);return out;}
    private static void collect(File f,String relative,Set<String> out){if(f.isFile()){out.add(relative);return;}File[] list=f.listFiles();if(list!=null)for(File child:list)collect(child,relative+"/"+child.getName(),out);}
    static void remove(File f){if(f==null)return;File[] children=f.listFiles();if(children!=null)for(File c:children)remove(c);f.delete();}
}
