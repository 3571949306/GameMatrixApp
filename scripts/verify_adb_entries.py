#!/usr/bin/env python3
"""Execute the production ADB entry adapter and tool preference logic without Gradle.

Small Android boundary fakes intentionally omit all new host R fields: a dynamic tool APK
must tolerate an old host, and the tested code must not link its ADB contract or resources.
This does not replace packaged Android UI/manifest validation.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "module-store/feature/tools/tools"
SOURCE = TOOLS / "src/main/java/com/gamecenter/app/tools"
STUBS = {
    "android/content/ComponentName.java": """
package android.content;
public class ComponentName {
 public final String pkg, name;
 public ComponentName(String pkg, String name) { this.pkg=pkg; this.name=name; }
}
""",
    "android/content/Intent.java": """
package android.content;
public class Intent {
 public static final int FLAG_ACTIVITY_NEW_TASK=0x10000000;
 public ComponentName component; public int flags;
 public final java.util.Map<String,String> extras=new java.util.HashMap<>();
 public Intent setClassName(String pkg,String name){component=new ComponentName(pkg,name);return this;}
 public Intent putExtra(String key,String value){extras.put(key,value);return this;}
 public Intent addFlags(int value){flags|=value;return this;}
}
""",
    "android/content/ActivityNotFoundException.java": """
package android.content;
public class ActivityNotFoundException extends RuntimeException {}
""",
    "android/content/pm/ApplicationInfo.java": """
package android.content.pm;
public class ApplicationInfo { public boolean enabled=true; }
""",
    "android/content/pm/ActivityInfo.java": """
package android.content.pm;
public class ActivityInfo {
 public boolean enabled=true, exported=false;
 public ApplicationInfo applicationInfo=new ApplicationInfo();
}
""",
    "android/content/pm/PackageManager.java": """
package android.content.pm;
public class PackageManager {
 public static class NameNotFoundException extends Exception {}
 public boolean available=true;
 public ActivityInfo info=new ActivityInfo();
 public ActivityInfo getActivityInfo(android.content.ComponentName c,int flags)
  throws NameNotFoundException {
  if(!available) throw new NameNotFoundException(); return info;
 }
}
""",
    "android/content/res/Resources.java": """
package android.content.res;
public class Resources {
 public int lookups=0; public boolean available=true;
 public int getIdentifier(String name,String type,String pkg) {
  lookups++;
  if(!available) return 0;
  switch(type+":"+name) {
   case "layout:item_tool_adb_workbench":return 1001;
   case "string:adb_entry_title":return 1002;
   case "string:adb_entry_description":return 1003;
   case "id:adb_entry_open":return 1004;
   case "string:adb_entry_unavailable":return 1005;
   default:return 0;
  }
 }
}
""",
    "android/content/SharedPreferences.java": """
package android.content;
public class SharedPreferences {
 private final java.util.Map<String,Object> values=new java.util.HashMap<>();
 public String getString(String k,String fallback){return (String)values.getOrDefault(k,fallback);}
 public int getInt(String k,int fallback){return (Integer)values.getOrDefault(k,fallback);}
 public boolean getBoolean(String k,boolean fallback){return (Boolean)values.getOrDefault(k,fallback);}
 @SuppressWarnings("unchecked")
 public java.util.Set<String> getStringSet(String k,java.util.Set<String> fallback) {
  java.util.Set<String> value=(java.util.Set<String>)values.get(k);
  return value==null?fallback:java.util.Collections.unmodifiableSet(value);
 }
 public Editor edit(){return new Editor();}
 public class Editor {
  private final java.util.Map<String,Object> pending=new java.util.HashMap<>();
  public Editor putString(String k,String v){pending.put(k,v);return this;}
  public Editor putInt(String k,int v){pending.put(k,v);return this;}
  public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
  public Editor putStringSet(String k,java.util.Set<String> v){pending.put(k,new java.util.HashSet<>(v));return this;}
  public void apply(){values.putAll(pending);}
 }
}
""",
    "android/content/Context.java": """
package android.content;
public class Context {
 public final android.content.pm.PackageManager pm=new android.content.pm.PackageManager();
 public final android.content.res.Resources resources=new android.content.res.Resources();
 public final SharedPreferences prefs=new SharedPreferences();
 public Intent started; public int starts=0; public boolean denyStart=false;
 public String getPackageName(){return "com.example.host.debug";}
 public Context getApplicationContext(){return this;}
 public android.content.pm.PackageManager getPackageManager(){return pm;}
 public android.content.res.Resources getResources(){return resources;}
 public String getString(int id){return "text-"+id;}
 public void startActivity(Intent intent){
  if(denyStart)throw new SecurityException("disabled during launch"); started=intent;starts++;
 }
}
""",
    "android/app/Activity.java": """
package android.app;
public class Activity extends android.content.Context {}
""",
    "android/view/View.java": """
package android.view;
public class View {
 public interface OnClickListener { void onClick(View v); }
 private final android.content.Context context; private OnClickListener listener;
 public View(android.content.Context c){context=c;}
 public android.content.Context getContext(){return context;}
 public View findViewById(int id){return id==1004?this:null;}
 public void setOnClickListener(OnClickListener l){listener=l;}
 public void performClick(){if(listener!=null)listener.onClick(this);}
}
""",
    "android/widget/Toast.java": """
package android.widget;
public class Toast {
 public static final int LENGTH_SHORT=0;
 public static Toast makeText(android.content.Context c,CharSequence s,int duration){return new Toast();}
 public void show(){}
}
""",
    "com/gamecenter/app/BuildConfig.java": """
package com.gamecenter.app;
public class BuildConfig { public static final boolean ENABLE_TOOLS_ENHANCEMENT=true; }
""",
    "com/gamecenter/app/core/common/ModuleScopedPreferences.java": """
package com.gamecenter.app.core.common;
public class ModuleScopedPreferences {
 public static void migrateFrom(android.content.Context c,String module,String name){}
 public static android.content.SharedPreferences get(android.content.Context c,String module,String name){return c.prefs;}
}
""",
}


def main():
    java, javac = shutil.which("java"), shutil.which("javac")
    if not java or not javac:
        raise SystemExit("JDK required (java and javac on PATH)")
    # The fake R contains only the pre-existing fields referenced by ToolSectionStore.
    old_resources = re.findall(r"R\.(\w+)\.(\w+)", (SOURCE / "ToolSectionStore.java").read_text(encoding="utf-8"))
    resource_classes = {}
    for index, (kind, name) in enumerate(sorted(set(old_resources)), 1):
        if name.startswith("adb_") or name == "item_tool_adb_workbench":
            raise AssertionError("New resources must be resolved after a host capability check")
        resource_classes.setdefault(kind, []).append(f"public static final int {name}={index};")
    STUBS["com/gamecenter/app/R.java"] = "package com.gamecenter.app; public class R {" + "".join(
        f"public static class {kind} {{ {''.join(fields)} }}" for kind, fields in resource_classes.items()
    ) + "}"
    output_root = ROOT / "build/agent-verification"
    output_root.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="adb-entries-", dir=output_root) as directory:
        output = Path(directory)
        stubs = []
        for relative, content in STUBS.items():
            path = output / "stubs" / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
            stubs.append(path)
        sources = [SOURCE / name for name in (
            "ToolSection.java", "ToolSectionStore.java", "ToolBinder.java", "AdbWorkbenchToolBinder.java"
        )]
        sources += [
            ROOT / "core/common/src/main/java/com/gamecenter/app/core/common/AdbWorkbenchLauncher.java",
            TOOLS / "tests/AdbEntryRegressionTest.java",
        ]
        classes = output / "classes"
        subprocess.run([javac, "-encoding", "UTF-8", "-d", str(classes), *map(str, stubs + sources)], check=True)
        subprocess.run([java, "-cp", str(classes), "com.gamecenter.app.tools.AdbEntryRegressionTest"], check=True)


if __name__ == "__main__":
    main()
