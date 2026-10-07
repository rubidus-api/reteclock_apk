package com.reteclock;

import android.content.*;
import android.graphics.BitmapFactory;
import android.os.*;
import com.reteclock.core.*;
import com.reteclock.core.layout.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Android bridge. Network parsing and file validation run on bounded workers; mutations serialize on the UI loop. */
final class WebAdmin {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Set<Object> HOSTS=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    private static final List<WebServer> SERVERS=new ArrayList<WebServer>();
    private static final AtomicLong REVISION=new AtomicLong(1);
    private static final ExecutorService CONTROL=Executors.newSingleThreadExecutor();
    private static Context app;
    private static volatile String status="Stopped";
    private static String configuration="";
    private static volatile long generation;
    private static final List<VoiceOptions.Option> VOICES=new ArrayList<VoiceOptions.Option>();
    private static boolean voicesReady,voicesStarted;
    private static String defaultEngine="";
    private static SoundPlayer testSound;
    private static TimerVoice testVoice;
    private static final SharedPreferences.OnSharedPreferenceChangeListener CHANGED=new SharedPreferences.OnSharedPreferenceChangeListener(){
        public void onSharedPreferenceChanged(SharedPreferences p,String key){if(WebSettings.field(key)!=null)REVISION.incrementAndGet();}
    };
    private static final Runnable STOP=new Runnable(){public void run(){if(!wanted())stop();}};
    /** Whether the server should be up just now: switched on, and the screens its lifetime asks for showing (R140). */
    private static boolean wanted() {
        if(app==null)return false;
        boolean page=false;for(Object host:HOSTS)if(host instanceof WebSettingsActivity)page=true;
        return WebLifetime.listens(lifetime(app),enabled(app),!HOSTS.isEmpty(),page);
    }
    static String lifetime(Context c){return WebLifetime.known(account(c).getString("lifetime",null));}
    static void enter(Context context,Object host) {
        if(app==null){app=context.getApplicationContext();Settings.prefs(app).registerOnSharedPreferenceChangeListener(CHANGED);
            // Never fatal: this is every screen's way in. A journal that cannot be undone is set
            // aside and said, once, and the clock comes up with the settings it has.
            if(!WebImports.recover(app))android.widget.Toast.makeText(app,R.string.web_journal_damaged,android.widget.Toast.LENGTH_LONG).show();
            WebImports.discardAbandoned(app);

        }
        HOSTS.add(host); MAIN.removeCallbacks(STOP); reconcile();
    }
    static void leave(Object host){HOSTS.remove(host);if(!wanted()){MAIN.removeCallbacks(STOP);MAIN.postDelayed(STOP,500);}}
    static SharedPreferences account(Context c){return c.getSharedPreferences("web_admin",Context.MODE_PRIVATE);}
    static String stateText(){return status;}
    /**
     * The addresses to open, for the device page's rows: kind and URL, IPv4 first, then IPv6.
     *
     * Link-local IPv6 (fe80::) is listened on but not listed: it is only an address together with
     * the name of *this* device's interface, which another device cannot use and most browsers will
     * not take in a URL at all — listed, it was the first line and the one that could not work.
     */
    static List<String[]> urls() {
        List<String[]> four=new ArrayList<String[]>(),six=new ArrayList<String[]>();
        synchronized(SERVERS){for(WebServer s:SERVERS){
            String authority=s.authority();
            if(authority.startsWith("127.")||authority.startsWith("[fe80:")||authority.startsWith("[::1]"))continue;
            (authority.startsWith("[")?six:four).add(new String[]{authority.startsWith("[")?"IPv6":"IPv4","http://"+authority+"/"});
        }}
        four.addAll(six);return four;
    }
    /**
     * What the listeners last saw, in words for the clock's own page (issue #69).
     *
     * When a browser "does not open the address", this is how to tell where it stopped: nothing
     * here means nothing reached the clock at all, and the network is where to look.
     */
    static String contactText(Context c) {
        WebServer.Contact last=null;int count=0;
        synchronized(SERVERS){for(WebServer s:SERVERS){count+=s.contacts();WebServer.Contact one=s.lastContact();if(one!=null&&(last==null||one.at>last.at))last=one;}}
        if(SERVERS.isEmpty())return "";
        if(last==null)return c.getString(R.string.web_contact_none);
        String when=new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new java.util.Date(last.at));
        int what=WebServer.Contact.ANSWERED.equals(last.outcome)?R.string.web_contact_answered
                :WebServer.Contact.OTHER_NETWORK.equals(last.outcome)?R.string.web_contact_other_network
                :WebServer.Contact.OTHER_ADDRESS.equals(last.outcome)?R.string.web_contact_other_address
                :WebServer.Contact.HTTPS.equals(last.outcome)?R.string.web_contact_https:R.string.web_contact_unreadable;
        return c.getString(R.string.web_contact_last,last.peer,when,c.getString(what),count);
    }
    static boolean enabled(Context c){return account(c).getBoolean("enabled",false);}
    static int port(Context c){return account(c).getInt("port",8080);}
    static void configure(final Context c,final boolean enabled,final String lifetime,final int port,final String user,final String password,final Runnable done) {
        if(port<1024||port>65535)throw new IllegalArgumentException("Port must be 1024-65535");
        if(!password.isEmpty())WebAuth.checkAccount(user,password);
        else if(!user.equals(account(c).getString("user",""))||WebAuth.restore(user,account(c).getString("verifier",""))==null)
            throw new IllegalArgumentException("Set administrator and password first");
        CONTROL.execute(new Runnable(){public void run(){
            try {
                SharedPreferences.Editor e=account(c).edit().putBoolean("enabled",enabled).putString("lifetime",WebLifetime.known(lifetime)).putInt("port",port).putString("user",user);
                if(!password.isEmpty())e.putString("verifier",WebAuth.create(user,password).encoded());
                if(!e.commit())throw new IllegalStateException("Cannot save administrator settings");
                MAIN.post(new Runnable(){public void run(){configuration="";reconcile();if(done!=null)done.run();}});
            } catch(final RuntimeException failure){MAIN.post(new Runnable(){public void run(){status="Cannot save administrator settings";if(done!=null)done.run();}});}
        }});
    }
    private static void stop(){if(testSound!=null){testSound.stopNow();testSound=null;}if(testVoice!=null){testVoice.release();testVoice=null;}generation++;MAIN.removeCallbacks(NETWORK);synchronized(SERVERS){for(WebServer s:SERVERS)s.close();SERVERS.clear();}configuration="";status="Stopped";}
    private static List<InetAddress> addresses() {
        List<InetAddress> result=new ArrayList<InetAddress>();
        try {
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces!=null&&interfaces.hasMoreElements()) {
                NetworkInterface n=interfaces.nextElement();if(!n.isUp()||n.isLoopback())continue;
                // Only what is known to be local: Wi-Fi, Ethernet, the hotspot (WebNetwork).
                if(!WebNetwork.local(n.getName()))continue;
                for(Enumeration<InetAddress> e=n.getInetAddresses();e.hasMoreElements();) {
                    InetAddress a=e.nextElement();if(a.isSiteLocalAddress()||a.isLinkLocalAddress()||isUniqueLocal(a))result.add(a);
                }
            }
            result.add(InetAddress.getByName("127.0.0.1"));
        }catch(Exception unavailable){}
        return result;
    }
    private static boolean isUniqueLocal(InetAddress a){byte[] b=a.getAddress();return b.length==16&&(b[0]&0xfe)==0xfc;}
    static String localAddresses(){StringBuilder out=new StringBuilder();for(InetAddress a:addresses())if(!a.isLoopbackAddress())out.append(a.getHostAddress()).append('\n');return out.length()==0?"No local network address":out.toString();}
    private static boolean localPeer(InetAddress local,InetAddress peer) {
        if(local.isLoopbackAddress())return peer.isLoopbackAddress();
        try {
            NetworkInterface network=NetworkInterface.getByInetAddress(local);
            for(InterfaceAddress ia:network.getInterfaceAddresses())if(ia.getAddress().equals(local)){
                byte[] a=local.getAddress(),b=peer.getAddress();if(a.length!=b.length)return false;int bits=ia.getNetworkPrefixLength();
                if(bits<0)return false;for(int i=0;i<a.length;i++){int mask=bits>=8?255:bits<=0?0:(255<<(8-bits))&255;if((a[i]&mask)!=(b[i]&mask))return false;bits-=8;}return true;
            }
        }catch(Exception ignored){}return false;
    }
    static void reconcile() {
        if(!wanted()){stop();return;}
        if(!voicesStarted){voicesStarted=true;VoiceChoices.catalogue(app,new VoiceChoices.Catalogue(){public void found(List<VoiceOptions.Option> rows,String def){VOICES.clear();VOICES.addAll(rows);defaultEngine=def;voicesReady=true;}});}
        final List<InetAddress> addresses=addresses();final int port=port(app);
        final SharedPreferences p=account(app);final WebAuth auth=WebAuth.restore(p.getString("user",""),p.getString("verifier",""));
        String config=addresses.toString()+port+p.getString("verifier","");if(config.equals(configuration)){MAIN.removeCallbacks(NETWORK);MAIN.postDelayed(NETWORK,5000);return;}
        stop();configuration=config;final long epoch=generation;status="Starting";
        CONTROL.execute(new Runnable(){public void run(){
            final List<WebServer> made=new ArrayList<WebServer>();final StringBuilder message=new StringBuilder();final boolean[] refused={false};
            for(final InetAddress a:addresses) {
                // A port just let go by the listener before this one is not always free the same
                // instant: binding straight after a restart failed now and then, and the server was
                // gone until somebody noticed (seen in the device lane). Asked again, briefly.
                IOException last=null;
                for(int attempt=0;attempt<8&&epoch==generation;attempt++) {
                    try {
                        final WebServer[] holder=new WebServer[1];
                        WebServer s=new WebServer(a,port,auth,new WebServer.Handler(){public WebServer.Response handle(WebHttp.Request r,InputStream in)throws Exception{return route(r,in,holder[0]);}},
                                new WebServer.Peers(){public boolean allows(InetAddress peer){return localPeer(a,peer);}},app.getCacheDir());
                        holder[0]=s;made.add(s);if(!a.isLoopbackAddress())message.append("http://").append(s.authority()).append("/\n");
                        last=null;break;
                    }catch(IOException rejected){
                        last=rejected;
                        if(auth==null)break;
                        try{Thread.sleep(250);}catch(InterruptedException stopped){Thread.currentThread().interrupt();break;}
                    }
                }
                if(last!=null){refused[0]=true;message.append("Cannot listen on a local interface; check port and credentials\n");}
            }
            MAIN.post(new Runnable(){public void run(){if(epoch!=generation||!wanted()){for(WebServer s:made)s.close();return;}
                synchronized(SERVERS){SERVERS.addAll(made);}status=made.isEmpty()?"Not listening":message.length()==0?"Listening on loopback only":message.toString();
                // A port that would not bind is asked for again at the next look (five seconds):
                // two restarts close together — an account change and the network watch — could
                // leave the second binding the port the first had not yet let go, and "the same
                // configuration" then meant nobody ever tried again (seen once in the device lane).
                // Only when nothing bound at all: a restart closes every session, so one interface
                // that will not bind must not restart the ones that did, every five seconds.
                if(refused[0]&&made.isEmpty())configuration="";}});
        }});
        MAIN.removeCallbacks(NETWORK);MAIN.postDelayed(NETWORK,5000);
    }
    private static final Runnable NETWORK=new Runnable(){public void run(){if(wanted())reconcile();}};
    private interface Task<T>{T run()throws Exception;}
    private static <T>T ui(final Task<T> task)throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())return task.run();
        FutureTask<T> f=new FutureTask<T>(new Callable<T>(){public T call()throws Exception{return task.run();}});MAIN.post(f);
        try{return f.get(90,TimeUnit.SECONDS);}catch(ExecutionException e){Throwable t=e.getCause();if(t instanceof Exception)throw (Exception)t;throw new IOException("Action failed");}
        catch(InterruptedException e){f.cancel(false);throw e;}catch(TimeoutException e){f.cancel(false);throw e;}
    }
    private static Map<String,String> current(){Map<String,String> v=new TreeMap<String,String>();for(Map.Entry<String,Object> e:Settings.everything(app).entrySet())v.put(e.getKey(),String.valueOf(e.getValue()));
        v.put("clock_hour12",String.valueOf(Settings.hour12(app)));v.put("image_quality",String.valueOf(Settings.imageQuality(app)));v.put("wake_on",String.valueOf(Settings.wakeOn(app)));return v;}
    private static WebServer.Response json(JSONObject j)throws IOException{return WebServer.Response.data("application/json; charset=utf-8",j.toString().getBytes("UTF-8"));}
    private static byte[] asset(int id)throws IOException{InputStream in=app.getResources().openRawResource(id);try{return WebHttp.bounded(in,WebHttp.TEXT_LIMIT);}finally{in.close();}}
    private static WebServer.Response route(final WebHttp.Request r,InputStream in,final WebServer server)throws Exception {
        if(server==null)return WebServer.Response.text(409,"Server starting; reload");
        String[] target=r.target.split("\\?",2);String path=target[0];
        Map<String,String> query=target.length>1?WebHttp.form(target[1].getBytes("UTF-8")):new HashMap<String,String>();
        if(r.method.equals("GET")) {
            if(path.equals("/"))return WebServer.Response.data("text/html; charset=utf-8",asset(R.raw.web_admin_html));
            if(path.equals("/app.js"))return WebServer.Response.data("application/javascript; charset=utf-8",asset(R.raw.web_admin_js));
            if(path.equals("/crypto.js"))return WebServer.Response.data("application/javascript; charset=utf-8",asset(R.raw.web_admin_crypto));
            if(path.equals("/app.css"))return WebServer.Response.data("text/css; charset=utf-8",asset(R.raw.web_admin_css));
            if(path.equals("/state"))return ui(new Task<WebServer.Response>(){public WebServer.Response run()throws Exception{if(!r.active())throw new IOException("Request expired or server stopped");return json(snapshot(server.csrf()));}});
            if(path.equals("/export"))return WebImports.export(app,query);
            if(path.equals("/media"))return media(query);
            return WebServer.Response.text(400,"Unknown page");
        }
        if(path.equals("/upload")) {
            if(!r.header("content-type").equals("application/octet-stream"))throw new IOException("Upload a file");
            final File work=WebImports.work(app);final File upload=new File(work,"upload");
            try {
                OutputStream out=new FileOutputStream(upload);try{WebHttp.copy(in,out,r.length);}finally{out.close();}
                final String revision=r.header("x-settings-revision");final String kind=query.get("kind"),name=query.get("name");
                final SettingsPackage.Preview preview=WebImports.preview(app,work,upload,kind,name);
                final Set<String> sections=new HashSet<String>(Arrays.asList(SettingsIni.SECTIONS));
                if(query.containsKey("sections")){sections.clear();sections.addAll(Arrays.asList(query.get("sections").split(",")));}
                final boolean[] files={ !"false".equals(query.get("fonts")),!"false".equals(query.get("pictures")),!"false".equals(query.get("sounds"))};
                // Asked on the UI loop, where the revision is kept; answered at once unless there is work.
                WebServer.Response early=ui(new Task<WebServer.Response>(){public WebServer.Response run()throws Exception{if(!r.active())throw new IOException("Request expired or server stopped");
                    if(!String.valueOf(REVISION.get()).equals(revision))return WebServer.Response.text(409,"Settings changed; reload before importing");
                    if(!"apply".equals(query.get("mode")))return WebServer.Response.text(200,WebImports.describe(preview));
                    if(preview.isEmpty())return WebServer.Response.text(200,"No recognized settings or files; ignored");
                    return null;
                }});
                if(early!=null)return early;
                // The copying is done here, on this request's own worker: megabytes of files moved
                // on the UI loop froze the clock and could raise "not responding" (review of
                // 2026-10-06). Imports are one at a time (WebImports.apply is synchronized).
                final SettingsPackage.Result result=WebImports.apply(app,preview,sections,files);
                return ui(new Task<WebServer.Response>(){public WebServer.Response run()throws Exception{
                    REVISION.incrementAndGet();preparePictures();refresh();
                    return WebServer.Response.text(200,"Imported "+result.settingsApplied+" settings; ignored "+result.dropped+" unavailable references");
                }});
            }finally{WebImports.remove(work);}
        }
        if(!r.header("content-type").startsWith("application/x-www-form-urlencoded"))throw new IOException("Form required");
        final Map<String,String> form=WebHttp.form(WebHttp.readBody(in,r.length,WebHttp.TEXT_LIMIT));
        if(path.equals("/settings"))return ui(new Task<WebServer.Response>(){public WebServer.Response run()throws Exception{if(!r.active())throw new IOException("Request expired or server stopped");
            String revision=form.remove("revision");if(!String.valueOf(REVISION.get()).equals(revision))return WebServer.Response.text(409,"Settings changed; reload before saving");
            String sources=form.remove("layout_sources");
            Map<String,String> valid=WebSettings.validate(form,false);Map<String,String> merged=current();merged.putAll(valid);WebSettings.dependencies(merged);capabilities(valid);
            SharedPreferences.Editor edit=Settings.edit(app);
            for(Map.Entry<String,String> e:valid.entrySet()){WebSettings.Field f=WebSettings.field(e.getKey());if(f.kind=='b')edit.putBoolean(e.getKey(),e.getValue().equals("true"));else if(f.kind=='i')edit.putInt(e.getKey(),Integer.parseInt(e.getValue()));else edit.putString(e.getKey(),e.getValue());}
            if(valid.containsKey("sun_method")){
                int method=Integer.parseInt(valid.get("sun_method"));if(method!=SunMethods.CUSTOM){SunRules rules=SunMethods.of(method).rules;
                    edit.putInt(Settings.KEY_SUN_DAWN_TENTHS,rules.dawnTenths).putInt(Settings.KEY_SUN_DUSK_TENTHS,rules.duskTenths).putInt(Settings.KEY_SUN_DUSK_MINUTES,rules.duskMinutesAfterEvening).putInt(Settings.KEY_SUN_EVENING_TENTHS,rules.eveningTenths).putBoolean(Settings.KEY_SUN_NIGHT_TO_DAWN,rules.nightEndsAtDawn).putInt(Settings.KEY_SUN_SHADOW,rules.shadowMultiple).putInt(Settings.KEY_SUN_HIGH_RULE,rules.highRule);}
                edit.putBoolean(Settings.KEY_SUN_METHOD_CHOSEN,method!=0);
            }
            if(valid.containsKey("layout_slides"))edit.putString(Settings.KEY_LAYOUT_SLIDES,LayoutSlides.parse(valid.get("layout_slides")).anchoredAt(System.currentTimeMillis()).text());
            if(valid.containsKey("layouts")) {
                try {WebImports.begin(app);prepareLayouts(LayoutBook.parse(valid.get("layouts")),sources);
                    if(!edit.commit())throw new IOException("Cannot save settings");WebImports.finish(app);
                } catch(Exception failure){WebImports.recover(app);throw new Refused("The layout or its pictures could not be saved.");}
                LayoutSkins.sweep(app,Settings.layouts(app));
            }else if(!edit.commit())throw new IOException("Cannot save settings");
            if(valid.containsKey("wake_on"))WakeBells.setSwitch(app,valid.get("wake_on").equals("true"));else WakeBells.reconcile(app);
            if(valid.containsKey("pool_background")||valid.containsKey("pool_text")||valid.containsKey("image_quality"))preparePictures();
            refresh();return WebServer.Response.text(200,"Saved "+valid.size()+" settings; ignored "+(form.size()-valid.size())+" fields");
        }});
        if(path.equals("/account")) {
            final int port=Integer.parseInt(form.get("port"));final String user=form.get("user"),masked=form.get("maskedVerifier");final boolean on="true".equals(form.get("enabled"));
            if(form.containsKey("password")||form.containsKey("verifier")||!("true".equals(form.get("enabled"))||"false".equals(form.get("enabled"))))throw new IOException("Use a signed account envelope");
            if(port<1024||port>65535||user==null||!user.matches("[A-Za-z0-9_.@-]{1,64}"))throw new IOException("Invalid account or port");
            if(masked==null&&!account(app).getString("user","").equals(user))throw new IOException("New password required");
            final WebAuth auth=masked==null?null:WebAuth.restore(user,form.get("salt")+":"+WebAuth.base64(server.accountVerifier(r,user,form.get("salt"),form.get("nonce"),masked)));
            if(masked!=null&&auth==null)throw new IOException("Invalid account verifier");
            return ui(new Task<WebServer.Response>(){public WebServer.Response run()throws Exception{if(!r.active())throw new IOException("Request expired or server stopped");
                if(!String.valueOf(REVISION.get()).equals(form.get("revision")))return WebServer.Response.text(409,"Reload first");
                SharedPreferences.Editor edit=account(app).edit().putString("user",user).putInt("port",port).putBoolean("enabled",on);if(auth!=null)edit.putString("verifier",auth.encoded());
                if(!edit.commit())throw new IOException("Cannot save account");REVISION.incrementAndGet();
                generation++;server.invalidateSessions();synchronized(SERVERS){for(WebServer active:SERVERS)active.invalidateSessions();}
                MAIN.postDelayed(new Runnable(){public void run(){configuration="";reconcile();}},1000);
                return WebServer.Response.text(200,"Administrator saved. Reconnect using the new account/port.");
            }});
        }
        if(path.equals("/action"))return ui(new Task<WebServer.Response>(){public WebServer.Response run()throws Exception{if(!r.active())throw new IOException("Request expired or server stopped");
            if(!String.valueOf(REVISION.get()).equals(form.get("revision")))return WebServer.Response.text(409,"Reload first");
            action(form);REVISION.incrementAndGet();refresh();return WebServer.Response.text(200,"Done on the clock device");
        }});
        return WebServer.Response.text(400,"Unknown action");
    }
    private static void refresh(){for(Object host:new ArrayList<Object>(HOSTS)){if(host instanceof WebActivity)((WebActivity)host).webSettingsChanged();else if(host instanceof ClockDreamService)((ClockDreamService)host).webSettingsChanged();}}
    private static void preparePictures(){CONTROL.execute(new Runnable(){public void run(){PreparedImages.prepareAll(app);MAIN.post(new Runnable(){public void run(){refresh();}});}});}
    private static void requireSound(String name)throws IOException{if(!name.isEmpty()&&Settings.sounds(app).file(name)==null)throw new IOException("Sound is unavailable");}
    private static void capabilities(Map<String,String> values)throws IOException {
        if(values.containsKey("tts_language")||values.containsKey("tts_engine")) {
            String engine=values.containsKey("tts_engine")?values.get("tts_engine"):Settings.ttsEngine(app);if(engine.isEmpty())engine=defaultEngine;
            String language=values.containsKey("tts_language")?values.get("tts_language"):Settings.ttsLanguage(app);
            if(!language.isEmpty()){boolean found=false;for(VoiceOptions.Option voice:VOICES)found|=voice.engine.equals(engine)&&voice.tag.equals(language);
                if(!voicesReady||!found)throw new IOException("Voice is unavailable on the selected engine");}
        }
        if(values.containsKey("bells"))for(Bell bell:Bells.parse(values.get("bells")).list())requireSound(bell.sound);
        if(values.containsKey("sound_clips"))for(SoundClip clip:SoundClips.parse(values.get("sound_clips")).list())requireSound(clip.name);
        if(values.containsKey("timer_presets"))for(TimerPreset preset:TimerPresets.parse(values.get("timer_presets"))){requireSound(preset.startSound);requireSound(preset.finishSound);for(TimerInterval interval:preset.intervals){requireSound(interval.startSound);requireSound(interval.preAlarmSound);}}

        for(Map.Entry<String,String> e:values.entrySet()) {
            String k=e.getKey(),v=e.getValue();if(k.equals("font")||k.startsWith("font_")){if(!v.isEmpty()&&Settings.fonts(app).file(v)==null)throw new IOException("Font is unavailable");}
            if(k.equals("tts_engine")&&!v.isEmpty()){boolean found=false;for(VoiceChoices.Engine a:VoiceChoices.engines(app))found|=a.pkg.equals(v);if(!found||!VoiceChoices.enginesChoosable())throw new IOException("Engine is unavailable");}
            if(k.equals("tts_language")&&!v.isEmpty()){boolean found=false;for(VoiceOptions.Option a:VOICES)found|=a.tag.equals(v)&&a.engine.equals(values.containsKey("tts_engine")?values.get("tts_engine"):Settings.ttsEngine(app).isEmpty()?defaultEngine:Settings.ttsEngine(app));if(!voicesReady||!found)throw new IOException("Voice is unavailable; reload after discovery");}
            if(k.equals("image_quality")&&(Integer.parseInt(v)>ImageQuality.highestOn(Build.VERSION.SDK_INT)||Integer.parseInt(v)>Settings.imageQuality(app)))throw new IOException("Run the image trial on the device before raising quality");
            if(k.equals("pool_background")||k.equals("pool_text")||k.equals("background_order"))for(String n:v.split("\n"))if(!n.isEmpty()&&Settings.images(app).file(n)==null)throw new IOException("Picture is unavailable");
        }
    }
    private static void prepareLayouts(LayoutBook book,String provenance)throws Exception {
        JSONArray sources=provenance==null?new JSONArray():new JSONArray(provenance);
        if(sources.length()>100)throw new IOException("Too many layout sources");
        LayoutBook old=Settings.layouts(app);
        for(boolean way:new boolean[]{false,true})for(int i=1;i<book.size(way);i++) {
            LayoutPreset preset=book.get(way,i);String sourceName=preset.name;boolean sourceWay=way;
            boolean existing=false;for(int j=1;j<old.size(way);j++)if(old.get(way,j).name.equals(preset.name))existing=true;
            if(!existing&&LayoutName.complaint(preset.name)!=null)throw new IOException("Reserved layout name");
            for(int j=0;j<sources.length();j++){JSONObject row=sources.getJSONObject(j);
                if(row.getString("name").equals(preset.name)&&row.getBoolean("landscape")==way){sourceName=row.optString("source","");sourceWay=row.optBoolean("sourceLandscape",way);break;}}
            LayoutPreset source=null;for(int j=1;j<old.size(sourceWay);j++)if(old.get(sourceWay,j).name.equals(sourceName))source=old.get(sourceWay,j);
            for(int role:new int[]{LayoutPreset.PICTURE_BACKGROUND,LayoutPreset.PICTURE_TEXT})for(String name:preset.pictures(role)){
                if(LayoutSkins.file(app,preset,name)!=null)continue;
                File original=source==null?null:LayoutSkins.file(app,source,name);
                if(original==null)original=Settings.images(app).file(name);
                if(original==null||!LayoutSkins.bring(app,preset,name,original))throw new IOException("Theme picture is unavailable");
            }
        }
    }
    private static JSONObject snapshot(String csrf)throws Exception {
        JSONObject out=new JSONObject();out.put("revision",REVISION.get());out.put("csrf",csrf);out.put("values",new JSONObject(current()));
        JSONArray fields=new JSONArray();for(WebSettings.Field f:WebSettings.fields()){if(!f.editable)continue;JSONObject row=new JSONObject();row.put("key",f.key);row.put("page",f.page);row.put("kind",String.valueOf(f.kind));row.put("min",f.min);row.put("max",f.max);if(f.choices!=null)row.put("choices",new JSONArray(Arrays.asList(f.choices)));fields.put(row);}out.put("fields",fields);
        JSONObject media=new JSONObject();media.put("fonts",names(Settings.fonts(app)));media.put("pictures",names(Settings.images(app)));media.put("sounds",names(Settings.sounds(app)));out.put("media",media);
        JSONArray calendars=new JSONArray();for(int c=0;c<Calendars.COUNT;c++)calendars.put(Calendars.name(c));out.put("calendars",calendars);
        JSONArray methods=new JSONArray();for(SunMethods.Method m:SunMethods.all())methods.put(m.name);out.put("methods",methods);
        JSONArray engines=new JSONArray();for(VoiceChoices.Engine e:VoiceChoices.engines(app)){JSONObject j=new JSONObject();j.put("value",e.pkg);j.put("label",e.label);engines.put(j);}out.put("engines",engines);
        JSONArray voices=new JSONArray();for(VoiceOptions.Option v:VOICES){JSONObject j=new JSONObject();j.put("value",v.tag);j.put("label",VoiceChoices.languageLabel(v.tag)+" · "+v.engineLabel);j.put("engine",v.engine);voices.put(j);}out.put("voices",voices);out.put("voicesReady",voicesReady);out.put("defaultEngine",defaultEngine);
        out.put("qualityMax",ImageQuality.highestOn(Build.VERSION.SDK_INT));out.put("user",account(app).getString("user",""));out.put("port",port(app));out.put("enabled",enabled(app));out.put("status",status);
        int[] screen=FullScreen.size(app);out.put("width",screen[0]);out.put("height",screen[1]);out.put("boxFields",new JSONArray(Arrays.asList(WebSettings.BOX_FIELDS)));
        JSONArray table=new JSONArray();SunClock sun=Settings.sunClock(app);long day=Bells.stampOf(System.currentTimeMillis(),Settings.offsetMinutes(app,System.currentTimeMillis()))/1440L;
        for(int i=0;i<7;i++){JSONObject row=new JSONObject();row.put("day",day+i-2440588L);JSONArray times=new JSONArray();for(int event=1;event<=8;event++)times.put(sun.localMinute((int)(day+i),event));row.put("times",times);table.put(row);}out.put("sunTable",table);
        return out;
    }
    private static JSONArray names(FontLibrary lib){JSONArray a=new JSONArray();for(FontLibrary.Entry e:lib.list())a.put(e.name);return a;}
    private static FontLibrary library(String kind)throws IOException{if("fonts".equals(kind))return Settings.fonts(app);if("pictures".equals(kind))return Settings.images(app);if("sounds".equals(kind))return Settings.sounds(app);throw new IOException("Unknown library");}
    private static WebServer.Response media(Map<String,String> q)throws Exception{
        String kind=q.get("kind"),name=q.get("name");if(name==null||SafeName.complaint(name)!=null)throw new IOException("Invalid name");File f=library(kind).file(name);if(f==null)throw new IOException("Missing file");
        if("pictures".equals(kind))return WebServer.Response.file("image/"+(name.toLowerCase(Locale.US).endsWith(".svg")?"unsupported":name.toLowerCase(Locale.US).endsWith(".png")?"png":"jpeg"),f,null);
        return WebServer.Response.file("application/octet-stream",f,"reteclock-media.bin");
    }
    private static void action(Map<String,String> f)throws Exception {
        String op=f.get("op");if("clear-log".equals(op)){TimerLog.deleteAll(app);return;}
        if("stop-test".equals(op)){if(testSound!=null)testSound.stopNow();if(testVoice!=null)testVoice.release();return;}
        if("test-speech".equals(op)){if(testVoice!=null)testVoice.release();testVoice=new TimerVoice(app);testVoice.say(Settings.spokenTimeNow(app),SystemClock.elapsedRealtime(),1f);return;}
        if("trial-images".equals(op)){int step=(int)WebSettings.number(f.containsKey("step")?f.get("step"):String.valueOf(Settings.imageQuality(app)),0,ImageQuality.highestOn(Build.VERSION.SDK_INT));Intent i=new Intent(app,ImageTrialActivity.class).putExtra(ImageTrialActivity.EXTRA_STEP,step).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);app.startActivity(i);return;}
        if("test-level".equals(op)){int mode=(int)WebSettings.number(f.get("mode"),0,2),kind=(int)WebSettings.number(f.get("soundKind"),0,4);SoundLevels levels=Settings.soundLevels(app);float gain=levels.gain(mode,kind);boolean buzz=levels.buzzes(mode,kind)&&!Settings.onTelevision(app);
            if(kind==SoundLevels.WAKE_BELLS)new TimerSounds(app).playAlarm(Tones.CHIME,gain,buzz);
            else if(kind==SoundLevels.SPOKEN_TIME||kind==SoundLevels.TIMER_MESSAGES){if(testVoice==null)testVoice=new TimerVoice(app);testVoice.say(Settings.spokenTimeNow(app),SystemClock.elapsedRealtime(),gain);}
            else new TimerSounds(app).play(kind==SoundLevels.BELLS?Tones.CHIME:Tones.END,gain,buzz);return;}
        if("test-sound".equals(op)){String name=f.get("name");if(name==null||SafeName.complaint(name)!=null||Settings.sounds(app).file(name)==null)throw new IOException("Missing sound");if(testSound==null)testSound=new SoundPlayer();testSound.play(Settings.sounds(app).file(name),Settings.soundClips(app).of(name));return;}
        FontLibrary lib=library(f.get("kind"));String name=f.get("name");if(name==null||SafeName.complaint(name)!=null||lib.file(name)==null)throw new IOException("Missing media");
        if("delete".equals(op)){if(!lib.delete(name))throw new IOException("Cannot delete media");
            if("pictures".equals(f.get("kind"))){ImageRoles.Lists roles=Settings.roles(app);List<String> a=new ArrayList<String>(roles.background),b=new ArrayList<String>(roles.text);a.remove(name);b.remove(name);Settings.saveRoles(app,new ImageRoles.Lists(a,b));List<String> order=Settings.backgroundCustomOrder(app);order.remove(name);Settings.setBackgroundCustomOrder(app,order);preparePictures();}
            if("sounds".equals(f.get("kind")))Settings.setSoundClips(app,Settings.soundClips(app).without(name));}
        else if("rename".equals(op)){String next=f.get("newName");if(next==null||SafeName.complaint(next)!=null)throw new IOException("Invalid name");String landed=lib.rename(name,next);if(landed==null)throw new IOException("Name exists or rename failed");
            Map<String,String> moved=new HashMap<String,String>();moved.put(name,landed);
            if("sounds".equals(f.get("kind"))){Settings.setBells(app,Settings.bells(app).renamed(moved));Settings.setSoundClips(app,Settings.soundClips(app).renamed(moved));List<TimerPreset> presets=new ArrayList<TimerPreset>();for(TimerPreset p:Settings.timerPresets(app))presets.add(p.soundsRenamed(moved));Settings.setTimerPresets(app,presets);}
            if("pictures".equals(f.get("kind"))){Settings.saveRoles(app,new ImageRoles.Lists(replace(Settings.roles(app).background,name,landed),replace(Settings.roles(app).text,name,landed)));Settings.setBackgroundCustomOrder(app,replace(Settings.backgroundCustomOrder(app),name,landed));preparePictures();}
            if("fonts".equals(f.get("kind")))for(String role:Settings.FONT_ROLES)if(Settings.fontNameFor(app,role).equals(name))Settings.edit(app).putString("font_"+role,landed).commit();
        }else throw new IOException("Unknown action");WakeBells.reconcile(app);
    }
    private static List<String> replace(List<String> list,String from,String to){List<String> out=new ArrayList<String>();for(String s:list)out.add(s.equals(from)?to:s);return out;}
}
