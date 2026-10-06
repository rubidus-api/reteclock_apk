package com.reteclock.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.MessageDigest;

/** Bounded local HTTP listener; socket lifetime follows its owner, not a background daemon. */
public final class WebServer {
    public interface Handler { Response handle(WebHttp.Request request,InputStream body)throws Exception; }
    public interface Peers { boolean allows(InetAddress address); }
    public static final class Response {
        public final int status; public final String type; public final byte[] bytes; public final File file;
        public String download; public Runnable cleanup;
        private Response(int status,String type,byte[] bytes,File file){this.status=status;this.type=type;this.bytes=bytes;this.file=file;}
        public static Response text(int status,String text)throws IOException{return new Response(status,"text/plain; charset=utf-8",text.getBytes("UTF-8"),null);}
        public static Response data(String type,byte[] bytes){return new Response(200,type,bytes,null);}
        public static Response file(String type,File file,String download){Response r=new Response(200,type,null,file);r.download=download;return r;}
    }
    private final ServerSocket listener;
    private final Handler handler; private final Peers peers;
    private final WebSessions sessions;private final File staging;
    private final String token=WebAuth.random();
    private final Set<Socket> sockets=Collections.synchronizedSet(new HashSet<Socket>());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(2));
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor();
    private volatile boolean closed;
    public WebServer(InetAddress address,int port,WebAuth auth,Handler handler,Peers peers)throws IOException {
        this(address,port,auth,handler,peers,null);
    }
    public WebServer(InetAddress address,int port,WebAuth auth,Handler handler,Peers peers,File staging)throws IOException {
        if(auth==null)throw new IOException("Set administrator credentials first");
        this.handler=handler;this.peers=peers;this.staging=staging;
        sessions=new WebSessions(auth,new WebSessions.Clock(){public long now(){return System.nanoTime()/1000000L;}});
        listener=new ServerSocket();listener.setReuseAddress(true);listener.bind(new InetSocketAddress(address,port),2);
        Thread accept=new Thread(new Runnable(){public void run(){accept();}},"reteclock-web");accept.setDaemon(true);accept.start();
    }
    public int port(){return listener.getLocalPort();}
    public String authority(){String a=listener.getInetAddress().getHostAddress().replace("%","%25");return (a.indexOf(':')>=0?"["+a+"]":a)+":"+port();}
    public String csrf(){return token;}
    public void invalidateSessions(){sessions.revoke();}
    private boolean authorityAllowed(String value) {
        if(value.equals(authority()))return true;
        if(listener.getInetAddress().getAddress().length!=16||!value.endsWith(":"+port())
                ||!value.matches("\\[[0-9A-Fa-f:.]+(%[A-Za-z0-9_.-]+)?\\]:[0-9]+"))return false;
        String literal=value.substring(1,value.indexOf(']'));int zone=literal.indexOf('%');if(zone>=0)literal=literal.substring(0,zone);
        try{return Arrays.equals(listener.getInetAddress().getAddress(),InetAddress.getByName(literal).getAddress());}
        catch(Exception invalid){return false;}
    }
    private void accept() {
        while(!closed) {
            final Socket socket;
            try{socket=listener.accept();socket.setSoTimeout(10000);sockets.add(socket);}catch(IOException stopped){return;}
            if(closed){drop(socket);return;}
            try{workers.execute(new Runnable(){public void run(){serve(socket);}});}catch(RejectedExecutionException busy){drop(socket);}
        }
    }
    public byte[] accountVerifier(WebHttp.Request r,String user,String salt,String nonce,String masked)throws IOException {
        return sessions.unmask(r.session,user,salt,nonce,WebAuth.unbase64(masked));
    }
    private Response login(WebHttp.Request r,InputStream in,String peer)throws Exception {
        if(r.length>2048||!r.header("content-type").equals("application/x-www-form-urlencoded"))return Response.text(400,"Invalid login request");
        byte[] body=new byte[(int)r.length];int used=0,n;while(used<body.length){n=in.read(body,used,body.length-used);if(n<0)throw new EOFException();used+=n;}
        Map<String,String> values=WebHttp.form(body);
        Set<String> allowed=new HashSet<String>(Arrays.asList(r.target.equals("/auth/challenge")?new String[]{"user"}:new String[]{"id","nonce","proof"}));
        if(!allowed.equals(values.keySet()))return Response.text(400,"Use challenge proof authentication");
        if(r.target.equals("/auth/challenge")) {
            String user=values.get("user");if(user==null||!user.matches("[A-Za-z0-9_.@-]{1,64}"))return Response.text(400,"Invalid administrator ID");
            WebSessions.Challenge c=sessions.challenge(user,r.header("host"),peer);
            // Said as what it is: after an account change this listener is about to be replaced,
            // which is not somebody having guessed too often.
            if(c==null)return sessions.revoked()?Response.text(503,"The administrator settings changed; the server is restarting. Sign in again in a moment"):Response.text(429,"Too many attempts; try again in half a minute");
            return Response.data("application/json; charset=utf-8",("{\"id\":\""+c.id+"\",\"nonce\":\""+c.nonce+"\",\"salt\":\""+c.salt+"\",\"iterations\":"+WebAuth.ITERATIONS+"}").getBytes("UTF-8"));
        }
        WebSessions.Session session=sessions.login(values.get("id"),values.get("nonce"),values.get("proof"),peer);
        if(session==null)return Response.text(401,"Login failed or challenge expired");
        return Response.data("application/json; charset=utf-8",("{\"id\":\""+session.id+"\"}").getBytes("UTF-8"));
    }
    /**
     * A request that needs a session: the head is proved first, and only then is the body read.
     *
     * The final proof covers the body's digest, so it cannot be checked until the body is here; the
     * head proof ({@link WebSessions#headText}) is what stands between an onlooker who has seen a
     * session ID and this device's storage. Room for the body is asked for before it is taken.
     */
    private Response protectedRequest(WebHttp.Request r,InputStream in)throws Exception {
        String id=r.header("x-auth-session"),count=r.header("x-auth-counter");
        long counter=count.matches("[1-9][0-9]{0,15}")?Long.parseLong(count):0;
        if(!r.header("authorization").isEmpty()||!sessions.exists(id,counter)||r.header("x-auth-proof").length()!=44
                ||!sessions.signed(id,counter,WebSessions.headText(r,id,counter),r.header("x-auth-head")))
            return Response.text(401,"Login required");
        if(r.method.equals("POST")&&!WebAuth.same(token.getBytes("UTF-8"),r.header("x-csrf-token").getBytes("UTF-8")))
            return Response.text(403,"Reload the administration page");
        if(!r.target.split("\\?",2)[0].equals("/upload")&&r.length>WebHttp.TEXT_LIMIT)throw new IOException("Request too large");
        File staged=null;InputStream verified=null;
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            if(r.length>0) {
                File room=staging!=null?staging:new File(System.getProperty("java.io.tmpdir","."));
                if(!WebHttp.roomFor(room,r.length*2+WebHttp.SPARE_BYTES))return Response.text(507,"Not enough free space on the clock for this upload");
                staged=File.createTempFile("web-request-",".tmp",staging);OutputStream out=new FileOutputStream(staged);
                try{byte[] bytes=new byte[8192];long remaining=r.length;while(remaining>0){int n=in.read(bytes,0,(int)Math.min(bytes.length,remaining));if(n<0)throw new EOFException();if(n==0)continue;digest.update(bytes,0,n);out.write(bytes,0,n);remaining-=n;}}finally{out.close();}
            }
            if(!sessions.accept(id,counter,WebSessions.requestText(r,id,counter,WebAuth.hex(digest.digest())),r.header("x-auth-proof")))
                return Response.text(401,"Request proof rejected; sign in again");
            r.session=id;if(!r.active())throw new IOException("Server stopped");
            if(r.target.equals("/auth/logout")&&r.method.equals("POST")){sessions.logout(id);return Response.text(200,"Signed out");}
            verified=staged==null?new ByteArrayInputStream(new byte[0]):new FileInputStream(staged);
            return handler.handle(r,verified);
        } finally {
            if(verified!=null)try{verified.close();}catch(IOException ignored){}
            if(staged!=null)staged.delete();
        }
    }
    private void serve(final Socket socket) {
        ScheduledFuture<?> deadline;
        try{deadline=deadlines.schedule(new Runnable(){public void run(){drop(socket);}},120,TimeUnit.SECONDS);}
        catch(RejectedExecutionException stopped){drop(socket);return;}
        Response response=null;
        try {
            // Asked before a byte is read: a peer that is not of this network is answered and
            // closed, not parsed (review of 2026-10-06).
            if(!peers.allows(socket.getInetAddress())) {
                response=Response.text(403,"Local origin required");
            } else {
                InputStream in=new BufferedInputStream(socket.getInputStream());WebHttp.Request r=WebHttp.readHead(in);
                final WebHttp.Request owned=r;
                r.live=new WebHttp.Live(){public boolean get(){return !closed&&!socket.isClosed()&&(owned.session==null||sessions.alive(owned.session));}};
                String host=r.header("host"),origin=r.header("origin"),peer=socket.getInetAddress().getHostAddress();
                if(!authorityAllowed(host)||(!origin.isEmpty()&&(!origin.startsWith("http://")||!authorityAllowed(origin.substring(7))))) response=Response.text(403,"Local origin required");
                else if(r.method.equals("GET")&&(r.target.equals("/")||r.target.equals("/app.js")||r.target.equals("/app.css")||r.target.equals("/crypto.js")))response=handler.handle(r,in);
                else if(r.method.equals("POST")&&(r.target.equals("/auth/challenge")||r.target.equals("/auth/login")))response=login(r,in,peer);
                else response=protectedRequest(r,in);
            }
        } catch(Refused refused) {
            // Words written for the person, by the code that refused: which line, key or entry.
            try{response=Response.text(400,"Refused, and nothing was changed. "+refused.getMessage());}catch(IOException ignored){}
        } catch(IllegalArgumentException bad) {
            try{response=Response.text(400,bad.getMessage()==null?"Invalid value":bad.getMessage());}catch(IOException ignored){}
        } catch(Exception bad) {
            try{response=Response.text(400,"Request or file rejected");}catch(IOException ignored){}
        }
        try {if(response!=null)write(socket.getOutputStream(),response);}catch(IOException disconnected){}
        finally{if(response!=null&&response.cleanup!=null)response.cleanup.run();deadline.cancel(false);drop(socket);}
    }
    private static void write(OutputStream out,Response r)throws IOException {
        long size=r.file==null?r.bytes.length:r.file.length();
        StringBuilder h=new StringBuilder("HTTP/1.1 ").append(r.status).append(' ').append(r.status==200?"OK":r.status==401?"Unauthorized":r.status==403?"Forbidden":r.status==409?"Conflict":r.status==429?"Too Many Requests":r.status==503?"Service Unavailable":r.status==507?"Insufficient Storage":"Bad Request")
            .append("\r\nContent-Type: ").append(r.type).append("\r\nContent-Length: ").append(size)
            .append("\r\nConnection: close\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\nReferrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'\r\n");
        
        if(r.download!=null&&r.download.matches("[A-Za-z0-9_.-]+"))h.append("Content-Disposition: attachment; filename=\"").append(r.download).append("\"\r\n");
        out.write(h.append("\r\n").toString().getBytes("US-ASCII"));
        if(r.file!=null){InputStream in=new FileInputStream(r.file);try{WebHttp.copy(in,out,size);}finally{in.close();}}
        else out.write(r.bytes);out.flush();
    }
    private void drop(Socket socket){sockets.remove(socket);try{socket.close();}catch(IOException ignored){}}
    public void close(){closed=true;invalidateSessions();try{listener.close();}catch(IOException ignored){}synchronized(sockets){for(Socket s:new ArrayList<Socket>(sockets))drop(s);}workers.shutdownNow();deadlines.shutdownNow();}
}
