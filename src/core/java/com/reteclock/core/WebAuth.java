package com.reteclock.core;

import java.io.*;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Salted PBKDF2-HMAC-SHA256 verifier, implemented with APIs present below KitKat. */
public final class WebAuth {
    private static final String DIGITS="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    public static final int ITERATIONS=20000;
    private final String user;
    private final byte[] salt, verifier;
    private WebAuth(String user, byte[] salt, byte[] verifier) { this.user=user; this.salt=salt; this.verifier=verifier; }
    public static void checkAccount(String user, String password) {
        if (user==null || !user.matches("[A-Za-z0-9_.@-]{1,64}")) throw new IllegalArgumentException("Use 1-64 letters, digits, dot, dash, underscore or @ for the administrator");
        try {
            if (password==null || password.getBytes("UTF-8").length<8 || password.getBytes("UTF-8").length>128
                    || password.matches("(?s).*[\\x00-\\x1f\\x7f].*")) throw new IllegalArgumentException("Password must be 8-128 UTF-8 bytes, without control characters");
        } catch (UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
    public static WebAuth create(String user, String password) {
        checkAccount(user,password); byte[] salt=new byte[16]; new SecureRandom().nextBytes(salt);
        return new WebAuth(user,salt,derive(password,salt));
    }
    public String encoded() { return base64(salt)+":"+base64(verifier); }
    public static WebAuth restore(String user, String encoded) {
        try {
            String[] a=encoded.split(":",-1); if (a.length!=2) return null;
            byte[] s=unbase64(a[0]), v=unbase64(a[1]);
            if (s.length!=16 || v.length!=32 || !user.matches("[A-Za-z0-9_.@-]{1,64}")) return null;
            return new WebAuth(user,s,v);
        } catch (Exception e) { return null; }
    }
    public static boolean same(byte[] a, byte[] b) {
        if (a==null || b==null || a.length!=b.length) return false;
        int diff=0; for(int i=0;i<a.length;i++) diff|=a[i]^b[i]; return diff==0;
    }
    public String salt() { return base64(salt); }
    boolean userEquals(String name) { return user.equals(name); }
    byte[] sign(String text) { try{return hmac(verifier,text.getBytes("UTF-8"));}catch(UnsupportedEncodingException e){throw new AssertionError(e);} }
    public static byte[] deriveKey(String password, byte[] salt) { return derive(password,salt); }
    public static byte[] hmac(byte[] key,byte[] value) {
        try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(value);}catch(Exception e){throw new IllegalStateException("HMAC unavailable");}
    }
    public static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append("0123456789abcdef".charAt((b&255)>>4)).append("0123456789abcdef".charAt(b&15));return s.toString();}
    public static byte[] mask(byte[] key,String user,String salt,String nonce,byte[] value) {
        if(value.length!=32)throw new IllegalArgumentException("Invalid verifier size");
        try{byte[] pad=hmac(key,("reteclock-account-v1\n"+user+"\n"+salt+"\n"+nonce).getBytes("UTF-8")),out=value.clone();for(int i=0;i<32;i++)out[i]^=pad[i];return out;}catch(UnsupportedEncodingException e){throw new AssertionError(e);}
    }
    private static byte[] derive(String password, byte[] salt) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(password.getBytes("UTF-8"),"HmacSHA256"));
            byte[] block=new byte[salt.length+4]; System.arraycopy(salt,0,block,0,salt.length); block[block.length-1]=1;
            byte[] u=mac.doFinal(block), result=u.clone();
            for (int n=1;n<ITERATIONS;n++) { u=mac.doFinal(u); for(int i=0;i<result.length;i++) result[i]^=u[i]; }
            return result;
        } catch (Exception e) { throw new IllegalStateException("Password verifier unavailable"); }
    }
    public static String random() { byte[] b=new byte[24]; new SecureRandom().nextBytes(b); return base64(b); }
    public static String base64(byte[] in) {
        StringBuilder out=new StringBuilder();
        for(int i=0;i<in.length;i+=3) {
            int a=in[i]&255, b=i+1<in.length?in[i+1]&255:0, c=i+2<in.length?in[i+2]&255:0;
            out.append(DIGITS.charAt(a>>2)).append(DIGITS.charAt(((a&3)<<4)|(b>>4)))
                .append(i+1<in.length?DIGITS.charAt(((b&15)<<2)|(c>>6)):'=')
                .append(i+2<in.length?DIGITS.charAt(c&63):'=');
        }
        return out.toString();
    }
    public static byte[] unbase64(String text) throws IOException {
        if (text.length()%4!=0 || !text.matches("[A-Za-z0-9+/]*={0,2}")) throw new IOException("Invalid Base64");
        ByteArrayOutputStream out=new ByteArrayOutputStream(); int bits=0,n=0;
        for(int i=0;i<text.length();i++) {
            char c=text.charAt(i); if (c=='=') break;
            int v=DIGITS.indexOf(c); if(v<0) throw new IOException("Invalid Base64");
            bits=(bits<<6)|v; n+=6; if(n>=8) { n-=8; out.write((bits>>n)&255); }
        }
        byte[] bytes=out.toByteArray(); if(!base64(bytes).equals(text)) throw new IOException("Noncanonical Base64");
        return bytes;
    }
}
