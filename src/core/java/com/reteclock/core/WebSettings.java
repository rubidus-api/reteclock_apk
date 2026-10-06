package com.reteclock.core;

import java.io.*;
import java.util.*;
import com.reteclock.core.layout.*;

/** Explicit web/import capabilities. A stored preference is not automatically editable. */
public final class WebSettings {
    public static final String[] ROLES={"hour","minute","meridiem","second","weekday","month_day","year","quote","calendar_title","calendar_weekday","calendar_day"};
    public static final String[] BOX_FIELDS={"hour_minute","hour","minute","meridiem","second","weekday","month_day","year","weekday_date","small_line","quote","calendar_title","calendar_weekday","calendar_day","calendar","timer","gap"};
    public static final class Field {
        public final String key,page;
        public final char kind;
        public long min=Integer.MIN_VALUE,max=Integer.MAX_VALUE;
        public String[] choices;
        public boolean editable=true;
        Field(String key,char kind,String page) { this.key=key;this.kind=kind;this.page=page; }
    }
    private static final Map<String,Field> FIELDS=new LinkedHashMap<String,Field>();
    static {
        for (String[] d:SettingsIni.definitions()) add(d[0],d[1].charAt(0),d[2]);
        add("wake_on",'b',"experiments"); add("tts_engine",'s',"clock"); add("image_quality",'i',"pictures");
        for(String k:new String[]{"sun_method_chosen","sun_twilight","calendar_week_monday","timer_alert","font","foreground"})FIELDS.get(k).editable=false;
        for(String role:ROLES) {
            add("font_"+role,'s',"fonts");
            for(String p:new String[]{"text_bold_","text_italic_","text_underline_","text_outline_"}) add(p+role,'b',"fonts");
        }
        for(int c=0;c<Calendars.COUNT;c++) {
            add("calendar_system_names_"+c,'i',"timedate"); range("calendar_system_names_"+c,0,Calendars.styleCount(c)-1);
            add("calendar_system_weekdays_"+c,'i',"timedate"); range("calendar_system_weekdays_"+c,0,Calendars.weekdayStyleCount(c)-1);
            for(String p:new String[]{"names_months_","names_weekdays_","spoken_months_","spoken_weekdays_"}) add(p+c,'s',p.startsWith("spoken")?"clock":"timedate");
        }
        for(int m=0;m<SoundLevels.MODES;m++) for(int k=0;k<SoundLevels.COUNT;k++) {
            add(SoundLevels.levelKey(m,k),'i',"sounds"); range(SoundLevels.levelKey(m,k),0,100);
            add(SoundLevels.muteKey(m,k),'b',"sounds");
            if(SoundLevels.canBuzz(k)) add(SoundLevels.buzzKey(m,k),'b',"sounds");
        }
        range("date_style",0,2); range("screen_turn",0,3); range("spoken_time_style",0,1);
        range("time_percent_wide",20,90); range("time_percent_tall",20,90);
        range("clock_noon_style",0,3); range("clock_midnight_style",0,4); range("clock_padding",0,127);
        range("background_fit",0,5); range("background_order_mode",0,5); range("image_quality",0,2);
        choose("background_still_seconds",5,10,30,60,300,1800,3600,21600,86400);
        range("sleep_brightness",-1,100); range("sleep_days",0,127); range("sleep_start",0,1439); range("sleep_end",0,1439);
        choose("sun_twilight",6,12,15,17,18,19); choose("sun_shadow",1,2); range("sun_high_rule",0,4);
        range("sun_dawn_tenths",0,300); range("sun_dusk_tenths",0,300); range("sun_evening_tenths",0,300); range("sun_dusk_minutes",0,240); range("sun_method",0,SunMethods.all().length-1);
        range("timer_chosen",0,99); range("timer_alert",0,2); range("timer_log_ceiling_mb",2,100); range("timer_log_floor_mb",1,99);
        range("calendar_header",0,2); range("calendar_system",0,Calendars.COUNT-1); range("calendar_week_start",0,6); range("calendar_hijri_offset",-2,2);
        range("time_source",0,1); range("time_utc_offset",-840,840); range("time_dst_preset",0,4);
    }
    private WebSettings() {}
    private static void add(String k,char kind,String page) { FIELDS.put(k,new Field(k,kind,page)); }
    private static void range(String k,long min,long max) { Field f=FIELDS.get(k);f.min=min;f.max=max; }
    private static void choose(String k,int... n) { Field f=FIELDS.get(k);f.choices=new String[n.length];for(int i=0;i<n.length;i++)f.choices[i]=String.valueOf(n[i]); }
    public static Field field(String key) { return FIELDS.get(key); }
    public static Collection<Field> fields() { return Collections.unmodifiableCollection(FIELDS.values()); }
    public static Map<String,String> validate(Map<String,String> input, boolean importing) {
        Map<String,String> out=new LinkedHashMap<String,String>();
        for(Map.Entry<String,String> e:input.entrySet()) {
            Field f=field(e.getKey()); if(f==null || (!importing&&!f.editable) || (importing&&(f.key.equals("wake_on")||f.key.equals("tts_engine")||f.key.equals("image_quality")))) continue;
            String value=e.getValue();
            if(importing&&value!=null) {
                if(f.kind=='b'){String alias=value.toLowerCase(Locale.US);
                    if(Arrays.asList("true","yes","on","1").contains(alias))value="true";
                    else if(Arrays.asList("false","no","off","0").contains(alias))value="false";
                }else if(f.kind=='i')try{String digits=value.trim();
                    // Old Dalvik's Integer.parseInt rejects a leading plus.
                    if(digits.startsWith("+")){if(digits.length()<2||digits.charAt(1)<'0'||digits.charAt(1)>'9')throw new NumberFormatException();digits=digits.substring(1);}
                    value=String.valueOf(Integer.parseInt(digits));
                }catch(NumberFormatException bad){throw new IllegalArgumentException("The value of "+f.key+" is not a whole number: "+Refused.quote(value));}
            }
            try { check(f,value); } catch (Exception bad) { throw new IllegalArgumentException("The value of "+f.key+" is not allowed: "+Refused.quote(value)+allowed(f)); }
            out.put(f.key,value);
        }
        return out;
    }
    /** What a field does accept, for the sentence that says a value was refused. */
    private static String allowed(Field f) {
        if(f.kind=='b')return " (use true or false)";
        if(f.kind!='i')return "";
        if(f.choices!=null)return " (use one of "+Arrays.asList(f.choices).toString().replace("[","").replace("]","")+")";
        if(f.key.equals("sleep_brightness"))return " (use -1, or 1 to 100)";
        if(f.key.startsWith("sound_level_"))return " (use 0 to 100 in steps of 10)";
        if(f.min>Integer.MIN_VALUE||f.max<Integer.MAX_VALUE)return " (use "+f.min+" to "+f.max+")";
        return "";
    }
    public static void dependencies(Map<String,String> v) {
        if(v.containsKey("timer_log_floor_mb")&&v.containsKey("timer_log_ceiling_mb")
                && number(v.get("timer_log_floor_mb"),1,99)>=number(v.get("timer_log_ceiling_mb"),2,100))
            throw new IllegalArgumentException("Log floor must be below ceiling");
        if(v.containsKey("timer_chosen")&&v.containsKey("timer_presets")) {
            int size=TimerPresets.parse(v.get("timer_presets")).size();
            if(size>0 && number(v.get("timer_chosen"),0,99)>=size) throw new IllegalArgumentException("Choose an existing timer preset");
        }
        if(v.containsKey("layouts")&&v.containsKey("layout_slides")) {
            LayoutBook book=LayoutBook.parse(v.get("layouts"));
            for(String line:v.get("layout_slides").split("\n")) if(line.startsWith("P|")||line.startsWith("L|")) {
                String[] p=line.split("\\|",-1);
                boolean exists=false; for(int i=0;i<book.size(line.startsWith("L"));i++) if(book.get(line.startsWith("L"),i).name.equals(p[3])) exists=true;
                if(!exists) throw new IllegalArgumentException("Slide names an unavailable layout");
            }
        }
    }
    private static void check(Field f,String v) throws Exception {
        if(v==null || v.getBytes("UTF-8").length>WebHttp.TEXT_LIMIT || v.indexOf('\0')>=0) throw new Exception();
        if(f.kind=='b') { if(!v.equals("true")&&!v.equals("false")) throw new Exception(); return; }
        if(f.kind=='i') {
            long n=number(v,f.min,f.max);
            if(f.key.equals("sleep_brightness")&&n==0)throw new Exception();
            if(f.choices!=null&&!Arrays.asList(f.choices).contains(v)) throw new Exception();
            if(f.key.startsWith("sound_level_") && n%10!=0) throw new Exception(); return;
        }
        String k=f.key;
        if(k.equals("bells")) bells(v);
        else if(k.equals("timer_presets")) timers(v);
        else if(k.equals("layouts")) layouts(v,true);
        else if(k.equals("layout_slides")) slides(v);
        else if(k.equals("sound_clips")) clips(v);
        else if(k.equals("sun_latitude")||k.equals("sun_longitude")) {
            if(!v.isEmpty()) { double d=Double.parseDouble(v);double max=k.equals("sun_latitude")?90:180;
                if(Double.isNaN(d)||Double.isInfinite(d)||Math.abs(d)>max) throw new Exception(); }
        } else if(k.equals("time_dst_custom")) {
            if(!v.isEmpty()) { String[] a=v.split(",",-1); if(a.length!=9)throw new Exception();
                for(int i=0;i<2;i++){number(a[i*4],1,12);number(a[i*4+1],0,6);number(a[i*4+2],0,4);number(a[i*4+3],0,1439);}
                long n=number(a[8],30,120);if(n!=30&&n!=60&&n!=120)throw new Exception(); }
        } else if(k.equals("date_order")) {
            if(!v.isEmpty()&&!DateOrder.parse(v).text().equals(v)) throw new Exception();
        } else if(k.equals("timer_keys_start_pause")||k.equals("timer_keys_stop")) {
            if(!v.isEmpty()) for(String n:v.split(",",-1)) number(n,1,1000);
        } else if(k.equals("font")||k.startsWith("font_")||k.equals("foreground")) file(v);
        else if(k.startsWith("pool_")||k.equals("background_order")) { if(!v.isEmpty()) for(String n:v.split("\n",-1))file(n); }
        else if(k.equals("tts_engine")) { if(v.length()>200||(!v.isEmpty()&&!v.matches("[A-Za-z0-9_.]+")))throw new Exception(); }
        else if(k.equals("tts_language")) { if(!v.isEmpty()&&VoiceLocale.parse(v)==null)throw new Exception(); }
        else if(k.equals("markers")) { if(v.split("\t",-1).length>4||v.length()>1024||v.indexOf('\n')>=0||v.indexOf('\r')>=0)throw new Exception(); }
        else if(k.startsWith("names_")||k.startsWith("spoken_months_")||k.startsWith("spoken_weekdays_")) {
            if(v.length()>4096||v.split("\t",-1).length>(k.contains("weekdays")?7:13)||v.indexOf('\n')>=0)throw new Exception();
        } else if(v.length()>4096)throw new Exception();
    }
    public static long number(String v,long min,long max) {
        if(!v.matches("-?[0-9]{1,18}"))throw new IllegalArgumentException();
        long n=Long.parseLong(v); if(n<min||n>max)throw new IllegalArgumentException();return n;
    }
    private static void file(String v) { if(!v.isEmpty()&&SafeName.complaint(v)!=null)throw new IllegalArgumentException(); }
    private static void bit(String v) { number(v,0,1); }
    private static void bells(String v) {
        if(v.isEmpty())return; String[] lines=v.split("\n",-1);if(lines.length>100)throw new IllegalArgumentException();
        for(String line:lines) {
            List<String> a=TimerPreset.split(line,'|');if(a.size()<4||a.size()>10||a.size()==9)throw new IllegalArgumentException();
            bit(a.get(0));number(a.get(1),0,127);number(a.get(2),0,1439);file(TimerPreset.unescape(a.get(3)));
            if(a.size()>4&&a.get(4).length()>256)throw new IllegalArgumentException();
            if(a.size()>5)number(a.get(5),1,10);if(a.size()>6)number(a.get(6),0,30);
            if(a.size()>7)bit(a.get(7));if(a.size()>9){number(a.get(8),0,8);number(a.get(9),-180,180);}
        }
    }
    private static void timers(String v) {
        if(v.isEmpty())return;String[] lines=v.split("\n",-1);if(lines.length>100)throw new IllegalArgumentException();
        for(String line:lines) {
            List<String> p=TimerPreset.split(line,'\t');if(p.size()<2||p.size()>101)throw new IllegalArgumentException();
            List<String> h=TimerPreset.split(p.get(0),'|');if(h.size()>4||h.get(0).length()>256)throw new IllegalArgumentException();
            if(h.size()>1)bit(h.get(1));for(int i=2;i<h.size();i++)file(TimerPreset.unescape(h.get(i)));
            for(int i=1;i<p.size();i++) {
                List<String>a=TimerPreset.split(p.get(i),'|');if(a.size()<6||a.size()>8)throw new IllegalArgumentException();
                long ms=number(a.get(1),1000,TimeInput.msOf(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE));number(a.get(2),Integer.MIN_VALUE,Integer.MAX_VALUE);number(a.get(3),Integer.MIN_VALUE,Integer.MAX_VALUE);
                if(a.get(0).length()>256||a.get(4).length()>1024)throw new IllegalArgumentException();
                number(a.get(5),0,Math.min(3600,ms/1000));for(int j=6;j<a.size();j++)file(TimerPreset.unescape(a.get(j)));
            }
        }
    }
    public static void layouts(String v,boolean book) {
        if(v.isEmpty())return;if(v.split("\n",-1).length>4096)throw new IllegalArgumentException();int boxes=0,presets=0;
        for(String line:v.split("\n")) {
            if(line.isEmpty()||line.equals("--"))continue;
            int cut=line.indexOf('=');if(cut<1)throw new IllegalArgumentException();
            String key=line.substring(0,cut),value=line.substring(cut+1);
            if(key.equals("chosen")) {String[] n=value.split(",",-1);if(n.length<1||n.length>2)throw new IllegalArgumentException();for(String s:n)number(s,0,100);}
            else if(key.equals("name")){if(++presets>100||value.length()>100||!LayoutName.isValid(value))throw new IllegalArgumentException();}
            else if(key.equals("way")){if(!value.equals("portrait")&&!value.equals("landscape"))throw new IllegalArgumentException();}
            else if(key.equals("picture")){String[]p=value.split("\\|",-1);if(p.length!=2||(!p[0].equals("text")&&!p[0].equals("background")))throw new IllegalArgumentException();file(p[1]);}
            else if(key.equals("box")||key.equals("portrait")||key.equals("landscape")) {
                if(++boxes>1500)throw new IllegalArgumentException();String[] p=value.split("\\|",-1);if(p.length<6||p.length>10||!Arrays.asList(BOX_FIELDS).contains(p[0]))throw new IllegalArgumentException();
                number(p[1],0,8);for(int i=2;i<6;i++) {if(p[i].contains(".")){double d=Double.parseDouble(p[i]);if(Double.isNaN(d)||Double.isInfinite(d)||Math.abs(d)>1000)throw new IllegalArgumentException();}else number(p[i],i<4?-1000:-1,1000);}
                if(p.length>6)number(p[6],0,8);if(p.length>7)bit(p[7]);if(p.length>8)bit(p[8]);if(p.length>9)number(p[9],-1,3);
            }
        }
        if(book) {
            LayoutBook b=LayoutBook.parse(v);
            String first=v.split("\n",2)[0];if(first.startsWith("chosen=")) {
                String[] n=first.substring(7).split(",");for(int i=0;i<n.length;i++)if(number(n[i],0,100)>=b.size(i==1))throw new IllegalArgumentException();
            }
        } else if(LayoutPreset.parse(v)==null)throw new IllegalArgumentException();
    }
    private static void slides(String v) {
        if(v.isEmpty())return;int count=0;
        for(String l:v.split("\n")) {
            if(l.startsWith("on=")){String[]a=l.substring(3).split(",",-1);if(a.length!=2)throw new IllegalArgumentException();bit(a[0]);bit(a[1]);}
            else if(l.startsWith("anchor="))number(l.substring(7),0,Long.MAX_VALUE);
            else {String[]a=l.split("\\|",-1);if(a.length!=4||!(a[0].equals("P")||a[0].equals("L"))||++count>100)throw new IllegalArgumentException();number(a[1],10,86400);bit(a[2]);if(a[3].length()>100)throw new IllegalArgumentException();}
        }
    }
    private static void clips(String v) {
        if(v.isEmpty())return;if(v.split("\n").length>500)throw new IllegalArgumentException();
        for(String line:v.split("\n")){List<String>a=TimerPreset.split(line,'|');if(a.size()!=4)throw new IllegalArgumentException();file(TimerPreset.unescape(a.get(0)));long start=number(a.get(1),0,36000),end=number(a.get(2),0,36000);if(end!=0&&end<=start)throw new IllegalArgumentException();bit(a.get(3));}
    }
    /**
     * Inspect recognized input before a forgiving legacy parser could drop a bad value.
     *
     * Strict on purpose, and all or nothing: a line that is not a setting, a setting given twice or
     * a value its field does not take refuses the whole file — and says which line it was, so a
     * file edited by hand can be put right (owner's decision, 2026-10-06).
     */
    public static Map<String,String> readIni(String text) throws IOException {
        if(text.getBytes("UTF-8").length>WebHttp.TEXT_LIMIT)throw new Refused("The settings file is larger than 256 KB.");
        if(SettingsIni.isOldFormat(text)) {
            Map<String,String> old=new LinkedHashMap<String,String>();
            for(SettingsText.Entry e:SettingsText.read(text)){if(old.containsKey(e.key))throw new Refused("The setting "+e.key+" is given twice.");old.put(e.key,e.value);}
            try{return validate(old,true);}catch(IllegalArgumentException e){throw new Refused(e.getMessage()+".");}
        }
        Map<String,String> raw=new LinkedHashMap<String,String>();
        Map<String,Integer> where=new HashMap<String,Integer>();
        int number=0;
        for(String line:text.split("\n",-1)) {
            number++;
            line=line.trim();if(line.isEmpty()||line.startsWith("#")||line.startsWith(";")||line.startsWith("["))continue;
            int a=line.indexOf('='), b=line.indexOf(':');int cut=a<0?b:b<0?a:Math.min(a,b);
            if(cut<1)throw new Refused("Line "+number+" is not a setting (it has no “key = value”): "+Refused.quote(line)+". Begin a note with #.");
            String key=line.substring(0,cut).trim().toLowerCase(Locale.US);
            if(field(key)==null)continue;
            if(raw.containsKey(key))throw new Refused("Line "+number+" gives "+key+" a second time; line "+where.get(key)+" already set it.");
            raw.put(key,SettingsIni.decodeValue(line.substring(cut+1).trim()));
            where.put(key,Integer.valueOf(number));
        }
        try{return validate(raw,true);}
        catch(IllegalArgumentException e){
            String message=e.getMessage();
            for(Map.Entry<String,Integer> at:where.entrySet())if(message.startsWith("The value of "+at.getKey()+" ")){message="Line "+at.getValue()+": "+message;break;}
            throw new Refused(message+".");
        }
    }
}
