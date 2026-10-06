package com.reteclock.core;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;

/** A deliberately small HTTP/1 request parser. No ambiguous framing or unbounded reads. */
public final class WebHttp {
    public static final int TEXT_LIMIT = 256 * 1024;
    public static final long UPLOAD_LIMIT = 64L * 1024 * 1024;
    /** Kept free beyond what an operation itself needs, so the clock is never left with none. */
    public static final long SPARE_BYTES = 8L * 1024 * 1024;
    private WebHttp() {}
    /**
     * Whether this directory's volume has room for so many bytes. A volume that will not say how
     * large it is — nothing known at all — is not refused for it; a full one is.
     */
    public static boolean roomFor(File directory, long bytes) {
        long usable = directory.getUsableSpace();
        return (usable == 0 && directory.getTotalSpace() == 0) || usable >= bytes;
    }
    interface Live { boolean get(); }
    public static final class Request {
        Live live;
        public String session;
        public boolean active() { return live==null||live.get(); }
        public final String method, target;
        public final Map<String,String> headers;
        public final long length;
        Request(String method, String target, Map<String,String> headers, long length) {
            this.method=method; this.target=target; this.headers=headers; this.length=length;
        }
        public String header(String key) { String v=headers.get(key); return v==null ? "" : v; }
    }
    public static Request readHead(InputStream in) throws IOException {
        int[] budget={16384};
        String line=line(in, 4096, budget);
        String[] first=line.split(" ", -1);
        if (first.length!=3 || !(first[2].equals("HTTP/1.1") || first[2].equals("HTTP/1.0"))
                || !(first[0].equals("GET") || first[0].equals("POST"))
                || !first[1].startsWith("/") || first[1].startsWith("//")
                || first[1].length()>2048 || first[1].indexOf('#')>=0 || first[1].indexOf('\\')>=0)
            throw new IOException("Invalid request");
        Map<String,String> headers=new LinkedHashMap<String,String>();
        for (int count=0;;count++) {
            line=line(in,4096,budget);
            if (line.length()==0) break;
            if (count>=32) throw new IOException("Too many headers");
            int colon=line.indexOf(':');
            if (colon<1 || !line.substring(0,colon).matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+"))
                throw new IOException("Invalid header");
            String key=line.substring(0,colon).toLowerCase(Locale.US);
            if (headers.containsKey(key)) throw new IOException("Duplicate header");
            headers.put(key,line.substring(colon+1).trim());
        }
        if (headers.containsKey("transfer-encoding")) throw new IOException("Transfer encoding unsupported");
        if (!headers.containsKey("host")) throw new IOException("Host required");
        long length=0;
        String size=headers.get("content-length");
        if (size!=null) {
            if (!size.matches("[0-9]{1,18}")) throw new IOException("Invalid length");
            try { length=Long.parseLong(size); } catch (NumberFormatException e) { throw new IOException("Invalid length"); }
        } else if (first[0].equals("POST")) throw new IOException("Length required");
        if (length>UPLOAD_LIMIT || (first[0].equals("GET") && length!=0)) throw new IOException("Body too large");
        return new Request(first[0],first[1],headers,length);
    }
    private static String line(InputStream in, int max, int[] budget) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for (;;) {
            int c=in.read(); if (c<0) throw new EOFException("Incomplete headers");
            if (--budget[0]<0) throw new IOException("Headers too large");
            if (c=='\r') {
                if (in.read()!='\n' || --budget[0]<0) throw new IOException("CRLF required");
                return out.toString("US-ASCII");
            }
            if (c<32 || c>126) throw new IOException("Invalid header byte");
            if (out.size()>=max) throw new IOException("Header too large");
            out.write(c);
        }
    }
    public static byte[] readBody(InputStream in, long length, int limit) throws IOException {
        if (length<0 || length>limit) throw new IOException("Body too large");
        ByteArrayOutputStream out=new ByteArrayOutputStream((int)Math.min(length,8192));
        copy(in,out,length); return out.toByteArray();
    }
    public static void copy(InputStream in, OutputStream out, long length) throws IOException {
        byte[] buf=new byte[8192];
        while (length>0) {
            int n=in.read(buf,0,(int)Math.min(length,buf.length));
            if (n<0) throw new EOFException("Incomplete body");
            if (n==0) continue;
            out.write(buf,0,n); length-=n;
        }
    }
    /** Reads through EOF, rejecting limit+1 rather than returning a truncated document. */
    public static byte[] bounded(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n;
        while ((n=in.read(b,0,Math.min(b.length,limit-out.size()+1)))!=-1) {
            if (n==0) continue;
            if (out.size()+n>limit) throw new IOException("File too large");
            out.write(b,0,n);
        }
        return out.toByteArray();
    }
    public static String utf8(byte[] bytes) throws IOException {
        try {
            return Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) { throw new IOException("Invalid UTF-8"); }
    }
    public static Map<String,String> form(byte[] body) throws IOException {
        if (body.length>TEXT_LIMIT) throw new IOException("Form too large");
        String raw=utf8(body); Map<String,String> out=new LinkedHashMap<String,String>();
        if (raw.isEmpty()) return out;
        for (String pair:raw.split("&",-1)) {
            int equals=pair.indexOf('='); if (equals<1) throw new IOException("Invalid form");
            String key=decode(pair.substring(0,equals)), value=decode(pair.substring(equals+1));
            if (key.length()>128 || out.containsKey(key) || out.size()>=1024) throw new IOException("Duplicate or excessive fields");
            out.put(key,value);
        }
        return out;
    }
    private static String decode(String raw) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] bytes=raw.getBytes("UTF-8");
        for (int i=0;i<bytes.length;i++) {
            int c=bytes[i]&255;
            if (c=='%') {
                if (i+2>=bytes.length) throw new IOException("Invalid escape");
                int a=Character.digit((char)bytes[++i],16), b=Character.digit((char)bytes[++i],16);
                if (a<0 || b<0) throw new IOException("Invalid escape"); c=(a<<4)|b;
            } else if (c=='+') c=' ';
            out.write(c);
        }
        return utf8(out.toByteArray());
    }
}
