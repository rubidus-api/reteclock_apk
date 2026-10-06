package com.reteclock.core;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Validate the central directory and stream every entry under one expansion budget. */
public final class WebArchive {
    public static final long EXPANDED_LIMIT=128L*1024*1024, FILE_LIMIT=32L*1024*1024;
    public static final class Item {
        public final String path; public final File file; public final long bytes;
        Item(String path,File file,long bytes){this.path=path;this.file=file;this.bytes=bytes;}
    }
    private WebArchive() {}
    public static String safePath(String path) throws IOException {
        if(path==null||path.isEmpty()||path.getBytes("UTF-8").length>240||path.startsWith("/")
                ||path.indexOf('\\')>=0||path.indexOf(':')>=0||path.matches("(?s).*[\\x00-\\x1f\\x7f].*"))throw new Refused("Unsafe archive path");
        String clean=path.endsWith("/")?path.substring(0,path.length()-1):path;
        String[] p=clean.split("/",-1);if(p.length>8)throw new Refused("Archive path too deep");
        for(String s:p)if(s.isEmpty()||s.equals(".")||s.equals(".."))throw new Refused("Unsafe archive path");
        return clean;
    }
    public static List<Item> unpack(File archive,File directory,long limit)throws IOException {
        if(archive.length()>WebHttp.UPLOAD_LIMIT)throw new Refused("Archive too large");
        checkDirectory(archive);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new Refused("Cannot stage upload");
        ZipFile zip=new ZipFile(archive);List<Item> items=new ArrayList<Item>();Set<String> names=new HashSet<String>();long total=0;int count=0;
        try {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()) {
                ZipEntry e=entries.nextElement();if(++count>500)throw new Refused("Too many archive entries");
                String path=safePath(e.getName());if(!names.add(path))throw new Refused("Duplicate archive destination");
                if(e.isDirectory()) {if(e.getSize()!=0)throw new Refused("Directory contains data");continue;}
                if(e.getSize()<0||e.getSize()>FILE_LIMIT||e.getSize()>limit-total)throw new Refused("Archive expansion too large");
                // Asked entry by entry, of the size the entry claims and is then held to.
                if(!WebHttp.roomFor(directory,e.getSize()+WebHttp.SPARE_BYTES))throw new Refused("Not enough free space on the clock to unpack this package");
                File file=new File(directory,path);File parent=file.getParentFile();
                if(!parent.isDirectory()&&!parent.mkdirs())throw new Refused("Cannot stage entry");
                InputStream in=zip.getInputStream(e);OutputStream out=new FileOutputStream(file);CRC32 crc=new CRC32();long size=0;byte[] b=new byte[8192];
                try {int n;while((n=in.read(b))!=-1){if(n==0)continue;size+=n;total+=n;
                    if(size>FILE_LIMIT||total>limit)throw new Refused("Archive expansion too large");crc.update(b,0,n);out.write(b,0,n);}}
                finally {try{in.close();}finally{out.close();}}
                if(size!=e.getSize()||crc.getValue()!=e.getCrc())throw new Refused("Corrupt archive entry");
                items.add(new Item(path,file,size));
            }
        } finally {zip.close();}
        return items;
    }
    /** Reject encrypted entries, links, split/ZIP64 archives and inconsistent central framing. */
    private static void checkDirectory(File file)throws IOException {
        RandomAccessFile in=new RandomAccessFile(file,"r");
        try {
            long end=file.length(),eocd=-1;if(end<22)throw new Refused("Truncated ZIP");
            for(long p=end-22;p>=Math.max(0,end-65557);p--){in.seek(p);if(u32(in)==0x06054b50L){
                in.seek(p+20);int comment=u16(in);if(p+22+comment==end){eocd=p;break;}}}
            if(eocd<0)throw new Refused("Missing ZIP directory");in.seek(eocd+4);
            if(u16(in)!=0||u16(in)!=0)throw new Refused("Split ZIP unsupported");
            int disk=u16(in),count=u16(in);long size=u32(in),offset=u32(in);
            if(count!=disk||count>500||offset+size!=eocd)throw new Refused("Invalid ZIP directory");
            in.seek(offset);
            for(int i=0;i<count;i++) {
                long start=in.getFilePointer();if(u32(in)!=0x02014b50L)throw new Refused("Invalid ZIP entry");
                u16(in);u16(in);int flags=u16(in),method=u16(in);
                if((flags&1)!=0||(flags&64)!=0||(method!=0&&method!=8))throw new Refused("Unsupported ZIP entry");
                in.skipBytes(4);long crc=u32(in),compressed=u32(in),expanded=u32(in);int name=u16(in),extra=u16(in),comment=u16(in);int diskStart=u16(in);
                u16(in);long attributes=u32(in),local=u32(in);
                int kind=(int)(attributes>>>16)&0170000;
                if((kind!=0&&kind!=0100000&&kind!=0040000)||diskStart!=0||expanded==0xffffffffL||local>=offset||name<1||name>960)
                    throw new Refused("Unsafe ZIP entry");
                long next=start+46+name+extra+comment;if(next>eocd)throw new Refused("Invalid ZIP entry length");
                byte[] centralName=new byte[name];in.readFully(centralName);
                in.seek(local);if(u32(in)!=0x04034b50L)throw new Refused("Invalid local ZIP entry");
                u16(in);int localFlags=u16(in),localMethod=u16(in);in.skipBytes(4);
                long localCrc=u32(in),localCompressed=u32(in),localExpanded=u32(in);int localName=u16(in),localExtra=u16(in);
                if(localFlags!=flags||localMethod!=method||localName!=name
                        ||local+30+localName+localExtra+compressed>offset
                        ||((flags&8)==0&&(localCrc!=crc||localCompressed!=compressed||localExpanded!=expanded)))
                    throw new Refused("Inconsistent ZIP headers");
                byte[] recordedName=new byte[localName];in.readFully(recordedName);
                if(!Arrays.equals(centralName,recordedName))throw new Refused("Inconsistent ZIP names");
                in.seek(next);
            }
            if(in.getFilePointer()!=eocd)throw new Refused("Invalid ZIP directory length");
        } finally {in.close();}
    }
    private static int u16(RandomAccessFile in)throws IOException {int a=in.readUnsignedByte(),b=in.readUnsignedByte();return a|(b<<8);}
    private static long u32(RandomAccessFile in)throws IOException {return (long)u16(in)|((long)u16(in)<<16);}
}
