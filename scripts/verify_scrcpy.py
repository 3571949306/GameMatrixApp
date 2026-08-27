#!/usr/bin/env python3
"""Verify pinned scrcpy asset, wire parsing and session lifetime using Android boundary fakes.

These tests execute the production session, but do not test a real hardware MediaCodec.
No Gradle tasks or protected release assets are touched.
"""
from pathlib import Path
import hashlib
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "core/adb/src/main/java/com/gamecenter/app/adb"
ASSETS = ROOT / "core/adb/src/main/assets"
SHA256 = "8588238c9a5a00aa542906b6ec7e6d5541d9ffb9b5d0f6e1bc0e365e2303079e"
STUBS = {
    "android/content/Context.java": """
package android.content;
public class Context {
 public Context getApplicationContext(){return this;}
 public android.content.res.AssetManager getAssets(){return new android.content.res.AssetManager();}
}
""",
    "android/content/res/AssetManager.java": """
package android.content.res;
public class AssetManager {
 public java.io.InputStream open(String name) throws java.io.IOException {
  return new java.io.FileInputStream(new java.io.File(System.getProperty("assets"),name));
 }
}
""",
    "android/view/Surface.java": """
package android.view;
public class Surface {
 public volatile boolean valid=true; public int releases;
 public boolean isValid(){return valid;}
 public void release(){valid=false;releases++;}
}
""",
    "android/media/MediaFormat.java": """
package android.media;
public class MediaFormat {
 public static final String KEY_WIDTH="width",KEY_HEIGHT="height",KEY_MAX_INPUT_SIZE="max-input-size";
 private final java.util.Map<String,Integer> values=new java.util.HashMap<>();
 public static MediaFormat createVideoFormat(String mime,int w,int h){
  MediaFormat f=new MediaFormat();f.setInteger(KEY_WIDTH,w);f.setInteger(KEY_HEIGHT,h);return f;
 }
 public void setInteger(String k,int value){values.put(k,value);}
 public int getInteger(String k){return values.get(k);}
 public boolean containsKey(String k){return values.containsKey(k);}
}
""",
    "android/media/MediaCodec.java": """
package android.media;
public class MediaCodec {
 public static final int BUFFER_FLAG_CODEC_CONFIG=2,INFO_OUTPUT_FORMAT_CHANGED=-2;
 public static final java.util.concurrent.atomic.AtomicInteger ACTIVE=new java.util.concurrent.atomic.AtomicInteger();
 public static class BufferInfo {}
 private MediaFormat format; private boolean formatPending; private boolean released;
 public static MediaCodec createDecoderByType(String mime) throws java.io.IOException {ACTIVE.incrementAndGet();return new MediaCodec();}
 public void configure(MediaFormat f,android.view.Surface s,Object crypto,int flags){format=f;formatPending=true;}
 public void start(){}
 public void stop(){}
 public void release(){if(!released){released=true;ACTIVE.decrementAndGet();}}
 public int dequeueInputBuffer(long timeout){return 0;}
 public java.nio.ByteBuffer getInputBuffer(int index){return java.nio.ByteBuffer.allocate(65536);}
 public void queueInputBuffer(int i,int off,int len,long pts,int flags){}
 public int dequeueOutputBuffer(BufferInfo info,long timeout){if(formatPending){formatPending=false;return INFO_OUTPUT_FORMAT_CHANGED;}return -1;}
 public MediaFormat getOutputFormat(){return format;}
 public void releaseOutputBuffer(int index,boolean render){}
}
""",
}


def main():
    asset = ASSETS / "adb/scrcpy-server-v3.3.4"
    if hashlib.sha256(asset.read_bytes()).hexdigest() != SHA256:
        raise SystemExit("scrcpy server asset checksum mismatch")
    license_text = (ASSETS / "licenses/scrcpy.txt").read_text(encoding="utf-8")
    if SHA256 not in license_text or "Apache License" not in license_text:
        raise SystemExit("scrcpy attribution is missing")
    java, javac = shutil.which("java"), shutil.which("javac")
    if not java or not javac:
        raise SystemExit("JDK required")
    output_root = ROOT / "build/agent-verification"
    output_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="scrcpy-", dir=output_root) as directory:
        output = Path(directory)
        stubs = []
        for name, content in STUBS.items():
            path = output / "stubs" / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            stubs.append(path)
        sources = [SOURCE / name for name in ("ScrcpyProtocol.java", "H264Dimensions.java", "ScrcpySession.java")]
        sources += sorted((SOURCE / "protocol").glob("*.java"))
        sources += [ROOT / "core/adb/tests/ScrcpyRegressionTest.java"]
        classes = output / "classes"
        subprocess.run([javac, "-encoding", "UTF-8", "--release", "17", "-d", str(classes),
                        *map(str, stubs + sources)], check=True, timeout=60)
        subprocess.run([java, "-Xmx128m", "-Dfile.encoding=UTF-8", "-Dassets=" + str(ASSETS),
                        "-cp", str(classes), "com.gamecenter.app.adb.ScrcpyRegressionTest"], check=True, timeout=40)


if __name__ == "__main__":
    main()
