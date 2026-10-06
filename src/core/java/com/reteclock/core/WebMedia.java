package com.reteclock.core;

import java.io.*;

/** Bound animation metadata before a platform decoder sees compressed frames, regardless of extension. */
public final class WebMedia {
    private static final long PIXELS=8L*1024*1024, BYTES=4L*1024*1024;
    private WebMedia() {}
    public static void checkAnimation(File file)throws IOException {
        RandomAccessFile in=new RandomAccessFile(file,"r");
        try {
            if(in.length()<6)return;
            byte[] signature=new byte[6];in.readFully(signature);
            String magic=new String(signature,"US-ASCII");
            if(magic.startsWith("GIF")){if(!magic.equals("GIF87a")&&!magic.equals("GIF89a"))throw new Refused("Invalid GIF header");gif(in);}
            else if(magic.startsWith("RIFF")){in.seek(0);webp(in);}
        }finally{in.close();}
    }
    private static void gif(RandomAccessFile in)throws IOException {
        if(in.length()>BYTES)throw new Refused("Animation exceeds byte budget");
        int width=u16(in),height=u16(in),packed=in.readUnsignedByte();in.skipBytes(2);
        if(width<1||height<1||(long)width*height>1000000)throw new Refused("Animation dimensions exceed budget");
        if((packed&128)!=0)skip(in,3L*(1<<((packed&7)+1)));
        int frames=0;long pixels=0;
        while(in.getFilePointer()<in.length()) {
            int block=in.readUnsignedByte();
            if(block==0x3b){if(frames==0||in.getFilePointer()!=in.length())throw new Refused("Invalid GIF trailer");return;}
            if(block==0x21){in.readUnsignedByte();blocks(in);continue;}
            if(block!=0x2c)throw new Refused("Invalid GIF block");
            int left=u16(in),top=u16(in),w=u16(in),h=u16(in);packed=in.readUnsignedByte();
            if(w<1||h<1||left+w>width||top+h>height||++frames>512||(pixels+=(long)width*height)>PIXELS)
                throw new Refused("Animation exceeds frame budget");
            if((packed&128)!=0)skip(in,3L*(1<<((packed&7)+1)));
            int code=in.readUnsignedByte();if(code<2||code>8)throw new Refused("Invalid GIF code size");blocks(in);
        }
        throw new Refused("Truncated GIF");
    }
    private static void blocks(RandomAccessFile in)throws IOException {
        int size;while((size=in.readUnsignedByte())!=0)skip(in,size);
    }
    private static void webp(RandomAccessFile in)throws IOException {
        in.skipBytes(4);long size=u32(in);byte[] tag=new byte[4];in.readFully(tag);
        if(!new String(tag,"US-ASCII").equals("WEBP")||size+8!=in.length())throw new Refused("Invalid WebP container");
        int frames=0,chunks=0;long pixels=0,canvas=0;
        while(in.getFilePointer()<in.length()) {
            in.readFully(tag);String kind=new String(tag,"US-ASCII");long length=u32(in),start=in.getFilePointer(),end=start+length+(length&1);
            if(++chunks>8192||end>in.length())throw new Refused("Invalid WebP chunk");
            if(kind.equals("VP8X")) {
                if(length!=10)throw new Refused("Invalid WebP canvas");in.skipBytes(4);canvas=(u24(in)+1)*(u24(in)+1);
            }
            if(kind.equals("ANMF")) {
                if(length<16||in.length()>BYTES)throw new Refused("Animation exceeds byte budget");
                u24(in);u24(in);long width=u24(in)+1,height=u24(in)+1;
                if(canvas<1||canvas>1000000||++frames>512||width*height>canvas||(pixels+=canvas)>PIXELS)throw new Refused("Animation exceeds frame budget");
            }
            in.seek(end);
        }
    }
    private static void skip(RandomAccessFile in,long count)throws IOException {long to=in.getFilePointer()+count;if(to>in.length())throw new EOFException("Truncated animation");in.seek(to);}
    private static int u16(RandomAccessFile in)throws IOException {return in.readUnsignedByte()|(in.readUnsignedByte()<<8);}
    private static long u24(RandomAccessFile in)throws IOException {return u16(in)|((long)in.readUnsignedByte()<<16);}
    private static long u32(RandomAccessFile in)throws IOException {return (long)u16(in)|((long)u16(in)<<16);}
}
