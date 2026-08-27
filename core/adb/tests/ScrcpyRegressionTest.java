package com.gamecenter.app.adb;

import android.content.Context;
import android.media.MediaCodec;
import android.view.Surface;
import com.gamecenter.app.adb.protocol.AdbTransport;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScrcpyRegressionTest {
    public static void main(String[] args) throws Exception {
        packetBoundsAndHeader();
        controlEncoding();
        textChunksAndInboundBounds();
        rotationDimensions();
        sessionRunsAndClosesOnlyOwnedResources();
        closeDuringUpload();
        closeDuringVideoHandshake();
        overloadedControlsStopSession();
        System.out.println("scrcpy: 8 regression groups passed (Android/MediaCodec boundaries faked)");
    }

    private static void packetBoundsAndHeader() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(0); out.writeInt(ScrcpyProtocol.H264); out.writeInt(1280); out.writeInt(720);
        out.writeLong((1L << 62) | 17); out.writeInt(3); out.write(new byte[] {1,2,3});
        InputStream input = new ByteArrayInputStream(bytes.toByteArray());
        ScrcpyProtocol.readDummy(input);
        ScrcpyProtocol.Size size = ScrcpyProtocol.readVideoHeader(input);
        check(size.width == 1280 && size.height == 720, "metadata dimensions");
        ScrcpyProtocol.Packet packet = ScrcpyProtocol.readPacket(input);
        check(packet.keyFrame && !packet.config && packet.presentationTimeUs == 17, "PTS and flags");
        check(packet.data.length == 3 && packet.data[2] == 3, "packet payload");
        expectFailure(() -> ScrcpyProtocol.readDummy(new ByteArrayInputStream(new byte[] {1})));
        expectFailure(() -> ScrcpyProtocol.readPacket(new ByteArrayInputStream(new byte[11])));
        for (int length : new int[] {-1, 0, ScrcpyProtocol.MAX_PACKET + 1}) {
            byte[] header = ByteBuffer.allocate(12).putLong(0).putInt(length).array();
            expectFailure(() -> ScrcpyProtocol.readPacket(new ByteArrayInputStream(header)));
        }
        byte[] invalidConfig = ByteBuffer.allocate(12).putLong(Long.MIN_VALUE).putInt(65537).array();
        expectFailure(() -> ScrcpyProtocol.readPacket(new ByteArrayInputStream(invalidConfig)));
        byte[] invalidSize = ByteBuffer.allocate(12).putInt(ScrcpyProtocol.H264).putInt(-1).putInt(20).array();
        expectFailure(() -> ScrcpyProtocol.readVideoHeader(new ByteArrayInputStream(invalidSize)));
    }

    private static void controlEncoding() throws Exception {
        ByteBuffer touch = ByteBuffer.wrap(ScrcpyProtocol.touch(0, 1f, -2f, new ScrcpyProtocol.Size(720,1280)));
        check(touch.remaining() == 32 && touch.get() == 2 && touch.get() == 0 && touch.getLong() == 0, "touch prefix");
        check(touch.getInt() == 719 && touch.getInt() == 0, "touch coordinates clamped inside bounds");
        check(touch.getShort() == 720 && touch.getShort() == 1280 && Short.toUnsignedInt(touch.getShort()) == 65535, "touch size and pressure");
        check(touch.getInt() == 0 && touch.getInt() == 0, "finger has no mouse buttons");
        ByteBuffer up = ByteBuffer.wrap(ScrcpyProtocol.touch(1, .5f, .5f, new ScrcpyProtocol.Size(100,200)));
        check(up.getShort(22) == 0, "release pressure is zero");
        expectFailure(() -> ScrcpyProtocol.touch(2, Float.NaN, 0, new ScrcpyProtocol.Size(100,100)));
        byte[] key = ScrcpyProtocol.key(4);
        check(key.length == 28 && key[0] == 0 && key[1] == 0 && key[14] == 0 && key[15] == 1, "key down/up atomic queue item");
        check(ByteBuffer.wrap(key).getInt(2) == 4 && ByteBuffer.wrap(key).getInt(16) == 4, "key codes");
        check(ScrcpyProtocol.screenPower(true)[1] == 1 && ScrcpyProtocol.screenPower(false)[1] == 0, "3.3.4 power is boolean, not legacy mode 2");
    }

    private static void textChunksAndInboundBounds() throws Exception {
        String text = "x".repeat(299) + "棋😀".repeat(250);
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(ScrcpyProtocol.text(text)));
        StringBuilder result = new StringBuilder();
        while (in.available() > 0) {
            check(in.readByte() == 1, "text message type");
            int count = in.readInt();
            check(count > 0 && count <= 300, "bounded UTF-8 chunk");
            byte[] chunk = new byte[count]; in.readFully(chunk);
            result.append(new String(chunk, StandardCharsets.UTF_8));
        }
        check(result.toString().equals(text), "UTF-8 chunks preserve code points");
        expectFailure(() -> ScrcpyProtocol.text("x".repeat(16385)));
        byte[] clipboard = ByteBuffer.allocate(5).put((byte)0).putInt(Integer.MAX_VALUE).array();
        expectFailure(() -> ScrcpyProtocol.discardDeviceMessage(new ByteArrayInputStream(clipboard)));
        expectFailure(() -> ScrcpyProtocol.discardDeviceMessage(new ByteArrayInputStream(new byte[] {99})));
    }

    private static void rotationDimensions() throws Exception {
        ScrcpyProtocol.Size portrait = H264Dimensions.fromConfig(sps(720,1280));
        ScrcpyProtocol.Size landscape = H264Dimensions.fromConfig(sps(1280,720));
        check(portrait.width == 720 && portrait.height == 1280, "portrait SPS");
        check(landscape.width == 1280 && landscape.height == 720, "rotation SPS");
        ScrcpyProtocol.Size cropped = H264Dimensions.fromConfig(sps(1080,1920));
        check(cropped.width == 1080 && cropped.height == 1920, "SPS crop units");
        check(H264Dimensions.fromConfig(new byte[] {0,0,0,1,0x68,1}) == null, "PPS only leaves size unchanged");
        expectFailure(() -> H264Dimensions.fromConfig(new byte[] {0,0,0,1,0x67,0}));
    }

    private static void sessionRunsAndClosesOnlyOwnedResources() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.rejectFirstVideo = true;
        Surface surface = new Surface();
        Observer observer = new Observer();
        ScrcpySession session = new ScrcpySession(new Context(), transport, surface, 1280, 4_000_000, observer);
        session.start(); session.start();
        await(observer.firstSize, "decoder first size");
        transport.video.feed(packet(Long.MIN_VALUE, sps(480,640)));
        await(observer.rotated, "rotation decoder reconfiguration");
        session.touch(0,.5f,.5f); session.touch(1,1f,1f);
        await(transport.controlWritten, "touch control sent");
        session.close(); session.close();
        await(transport.cleaned, "owned remote file cleanup");
        check(transport.closedCount.get() == 0 && transport.isOpen(), "shared transport remains open");
        check(surface.releases == 0 && surface.isValid(), "borrowed Surface remains owned by UI");
        check(MediaCodec.ACTIVE.get() == 0, "decoders released after rotation and close");
        check(transport.owned.stream().allMatch(channel -> channel.closed), "all session channels closed");
        check(transport.launches.get() == 1, "start is idempotent");
        check(transport.launchCommand.contains("tunnel_forward=true") && transport.launchCommand.contains("audio=false"), "pinned protocol options");
        check(transport.launchCommand.contains(transport.cleanedPath), "cleanup only targets own launched path");
        check(observer.errors.get() == 0, "normal close emits no error");
    }

    private static void closeDuringUpload() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.blockUpload = true;
        Observer observer = new Observer();
        ScrcpySession session = new ScrcpySession(new Context(), transport, new Surface(), 1280, 4_000_000, observer);
        session.start();
        await(transport.sync.waiting, "upload response is blocking");
        session.close();
        await(transport.cleaned, "cancelled upload cleanup");
        check(transport.sync.closed && transport.isOpen(), "upload cancellation closes channel, not transport");
        check(transport.launches.get() == 0 && observer.errors.get() == 0, "cancelled upload cannot launch server or notify old UI");
    }

    private static void closeDuringVideoHandshake() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.blockHandshake = true;
        Observer observer = new Observer();
        ScrcpySession session = new ScrcpySession(new Context(), transport, new Surface(), 1280, 4_000_000, observer);
        session.start();
        await(transport.video.waiting, "dummy-byte read blocks");
        session.close();
        await(transport.cleaned, "handshake cancellation cleanup");
        check(transport.video.closed && transport.isOpen(), "blocked handshake can be cancelled independently");
        check(MediaCodec.ACTIVE.get() == 0, "no decoder allocated before handshake");
    }

    private static void overloadedControlsStopSession() throws Exception {
        FakeTransport transport = new FakeTransport();
        transport.blockControl = true;
        Observer observer = new Observer();
        ScrcpySession session = new ScrcpySession(new Context(), transport, new Surface(), 1280, 4_000_000, observer);
        session.start(); await(observer.firstSize, "size before controls");
        session.key(4); await(transport.controlWaiting, "control send is blocking");
        for (int index = 0; index < 70; index++) session.key(4);
        await(observer.error, "bounded queue rejects overload by stopping mirror");
        await(transport.cleaned, "overload cleanup");
        check(observer.errors.get() == 1 && transport.isOpen(), "overload terminates mirror exactly once without disconnecting ADB");
    }

    private static byte[] packet(long flags, byte[] payload) {
        return ByteBuffer.allocate(12 + payload.length).putLong(flags).putInt(payload.length).put(payload).array();
    }

    /** Minimal baseline 4:2:0 SPS fixture, including right/bottom cropping and RBSP escaping. */
    private static byte[] sps(int width, int height) throws Exception {
        Bits bits = new Bits();
        bits.fixed(66,8);bits.fixed(0,8);bits.fixed(31,8);bits.ue(0);
        bits.ue(0);bits.ue(0);bits.ue(0);bits.ue(1);bits.fixed(0,1);
        int mbWidth=(width+15)/16,mbHeight=(height+15)/16;
        bits.ue(mbWidth-1);bits.ue(mbHeight-1);bits.fixed(1,1);bits.fixed(1,1);
        bits.fixed(1,1);bits.ue(0);bits.ue((mbWidth*16-width)/2);bits.ue(0);bits.ue((mbHeight*16-height)/2);
        bits.fixed(0,1);bits.fixed(1,1);
        ByteArrayOutputStream out = new ByteArrayOutputStream();out.write(new byte[] {0,0,0,1,0x67});
        int zeros=0;
        for(byte value:bits.bytes()){
            int unsigned=value&255;
            if(zeros>=2 && unsigned<=3){out.write(3);zeros=0;}
            out.write(unsigned);zeros=unsigned==0?zeros+1:0;
        }
        return out.toByteArray();
    }
    private static class Bits {
        final StringBuilder bits=new StringBuilder();
        void fixed(int value,int n){for(int i=n-1;i>=0;i--)bits.append((value>>i)&1);}
        void ue(int value){String encoded=Integer.toBinaryString(value+1);bits.append("0".repeat(encoded.length()-1));bits.append(encoded);}
        byte[] bytes(){while(bits.length()%8!=0)bits.append('0');byte[] result=new byte[bits.length()/8];for(int i=0;i<result.length;i++)result[i]=(byte)Integer.parseInt(bits.substring(i*8,i*8+8),2);return result;}
    }

    private static class Observer implements ScrcpySession.Listener {
        final CountDownLatch firstSize=new CountDownLatch(1),rotated=new CountDownLatch(1),error=new CountDownLatch(1);
        final AtomicInteger errors=new AtomicInteger();
        public void onSize(int width,int height){firstSize.countDown();if(width==480&&height==640)rotated.countDown();}
        public void onError(String message){errors.incrementAndGet();error.countDown();}
    }

    private static class BlockingInput extends InputStream {
        final ArrayDeque<Byte> bytes=new ArrayDeque<>();
        final CountDownLatch waiting=new CountDownLatch(1);
        volatile boolean closed;
        synchronized void feed(byte[] data){for(byte value:data)bytes.add(value);notifyAll();}
        public synchronized int read() throws IOException {
            while(bytes.isEmpty()&&!closed){waiting.countDown();try{wait();}catch(InterruptedException e){throw new java.io.InterruptedIOException();}}
            return bytes.isEmpty()?-1:Byte.toUnsignedInt(bytes.remove());
        }
        public synchronized int read(byte[] out,int off,int len)throws IOException{
            if(len==0)return 0;int first=read();if(first<0)return -1;out[off]=(byte)first;int count=1;
            while(count<len&&!bytes.isEmpty())out[off+count++]=bytes.remove();return count;
        }
        public synchronized void close(){closed=true;notifyAll();}
    }

    private static class FakeTransport implements AdbTransport {
        final List<FakeChannel> owned=java.util.Collections.synchronizedList(new ArrayList<>());
        final BlockingInput sync=new BlockingInput(),video=new BlockingInput(),control=new BlockingInput();
        final CountDownLatch cleaned=new CountDownLatch(1),controlWritten=new CountDownLatch(1),controlWaiting=new CountDownLatch(1);
        final AtomicInteger closedCount=new AtomicInteger(),launches=new AtomicInteger();
        boolean blockUpload,blockHandshake,blockControl,rejectFirstVideo;
        int sockets;
        volatile String launchCommand="",cleanedPath="";
        public boolean isOpen(){return closedCount.get()==0;}
        public void close(){closedCount.incrementAndGet();}
        public synchronized Channel open(String service)throws IOException{
            if(service.equals("sync:")){
                if(!blockUpload)sync.feed(new byte[]{'O','K','A','Y',0,0,0,0});
                return own(sync,new ByteArrayOutputStream());
            }
            if(service.startsWith("shell:CLASSPATH=")){
                launchCommand=service;launches.incrementAndGet();return own(new BlockingInput(),new ByteArrayOutputStream());
            }
            if(service.startsWith("localabstract:")){
                if(rejectFirstVideo){rejectFirstVideo=false;throw new IOException("server not listening yet");}
                if(sockets++==0){
                    if(!blockHandshake){video.feed(ByteBuffer.allocate(13).put((byte)0).putInt(ScrcpyProtocol.H264).putInt(640).putInt(480).array());}
                    return own(video,new ByteArrayOutputStream());
                }
                check(!video.bytes.contains((byte)0) || video.bytes.size()==12,"control connects after dummy byte consumed");
                return own(control,new OutputStream(){
                    public void write(int value)throws IOException{write(new byte[]{(byte)value});}
                    public void write(byte[] data,int offset,int count)throws IOException{
                        if(blockControl){controlWaiting.countDown();synchronized(control){while(!control.closed)try{control.wait();}catch(InterruptedException e){throw new java.io.InterruptedIOException();}}}
                        controlWritten.countDown();
                    }
                });
            }
            if(service.startsWith("shell:rm -f /data/local/tmp/gm-scrcpy-")){
                cleanedPath=service.substring("shell:rm -f ".length());
                return new Channel(){
                    public InputStream input(){return new ByteArrayInputStream(new byte[0]);}
                    public OutputStream output(){return new ByteArrayOutputStream();}
                    public void close(){cleaned.countDown();}
                };
            }
            throw new IOException("Unexpected service "+service);
        }
        private Channel own(BlockingInput in,OutputStream out){FakeChannel channel=new FakeChannel(in,out);owned.add(channel);return channel;}
    }
    private static class FakeChannel implements AdbTransport.Channel {
        final BlockingInput in;final OutputStream out;volatile boolean closed;
        FakeChannel(BlockingInput in,OutputStream out){this.in=in;this.out=out;}
        public InputStream input(){return in;}
        public OutputStream output(){return out;}
        public void close(){closed=true;in.close();}
    }
    private interface Action {void run()throws Exception;}
    private static void expectFailure(Action action)throws Exception{try{action.run();}catch(IOException|IllegalArgumentException expected){return;}throw new AssertionError("Malformed input was accepted");}
    private static void await(CountDownLatch latch,String message)throws Exception{check(latch.await(5,TimeUnit.SECONDS),message);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
