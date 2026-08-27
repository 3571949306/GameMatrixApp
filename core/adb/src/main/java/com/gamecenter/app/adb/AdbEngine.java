package com.gamecenter.app.adb;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.view.Surface;
import com.gamecenter.app.adb.protocol.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Service-owned state. No Activity, View, Surface, module class loader or UI callback is retained. */
public final class AdbEngine implements Closeable {

    public interface Events { void changed(); }
    public interface Work { String run(Session session, ResourceScope scope) throws Exception; }

    public interface Observer { void onChanged(); }

    public static final class AppInfo {
        public final String packageName;
        public final String label;
        public final boolean isSystem;
        public final boolean enabled;
        AppInfo(String packageName, String label, boolean isSystem, boolean enabled) {
            this.packageName = packageName; this.label = label; this.isSystem = isSystem; this.enabled = enabled;
        }
    }

    public static final class FileInfo {
        public final String name, path;
        public final boolean isDirectory;
        public final long size;
        FileInfo(String name, String path, boolean isDirectory, long size) {
            this.name = name; this.path = path; this.isDirectory = isDirectory; this.size = size;
        }
    }

    public static final class DeviceInfo {
        public final String model, brand, androidVersion, sdk, buildId, serial;
        public final int dpi, resolutionWidth, resolutionHeight;
        DeviceInfo(String model, String brand, String androidVersion, String sdk, String buildId,
                   String serial, int dpi, int resolutionWidth, int resolutionHeight) {
            this.model = model; this.brand = brand; this.androidVersion = androidVersion;
            this.sdk = sdk; this.buildId = buildId; this.serial = serial;
            this.dpi = dpi; this.resolutionWidth = resolutionWidth; this.resolutionHeight = resolutionHeight;
        }
    }

    public static final class Session implements Closeable {
        public final String id, title;
        final AdbTransport transport;
        final FastbootClient fastboot;
        final String usbName;
        final AtomicBoolean busy = new AtomicBoolean();
        volatile boolean closed, dangerous;
        public volatile String operation = "", log = "", directory = "/sdcard", packageFilter = "";
        public volatile List<String> applications = Collections.emptyList();
        public volatile List<AdbSync.Entry> files = Collections.emptyList();
        public final Set<String> selectedFiles = new LinkedHashSet<>();
        volatile ResourceScope task;
        private ScrcpySession scrcpy;

        Session(String id, String title, AdbTransport transport, FastbootClient fastboot, String usbName) {
            this.id = id; this.title = title; this.transport = transport; this.fastboot = fastboot; this.usbName = usbName;
        }
        public boolean available() { return !closed && (transport == null || transport.isOpen()); }
        public synchronized void append(String value) {
            String combined = log + value + "\n";
            log = combined.length() > 64000 ? "[较早输出已省略]\n" + combined.substring(combined.length() - 60000) : combined;
        }
        @Override public void close() {
            if (closed) return; closed = true;
            ResourceScope scope = task; if (scope != null) scope.close();
            if (scrcpy != null) { scrcpy.close(); scrcpy = null; }
            try { if (transport != null) transport.close(); } catch (IOException ignored) { }
            try { if (fastboot != null) fastboot.close(); } catch (IOException ignored) { }
            applications = Collections.emptyList(); files = Collections.emptyList(); selectedFiles.clear();
        }
    }

    private final Context context;
    private final Events events;
    private final List<Observer> observers = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Set<ResourceScope> connecting = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 20, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(24), task -> new Thread(task, "gm-adb-io"), new ThreadPoolExecutor.AbortPolicy());
    private final ScheduledThreadPoolExecutor deadlines = new ScheduledThreadPoolExecutor(1, task -> new Thread(task, "gm-adb-timer"));
    private final AtomicInteger jobs = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile AdbIdentity identity;
    public volatile String selectedId, notice = "仅连接你拥有或获授权管理的设备。所有操作均需手动触发。";

    public AdbEngine(Context context, Events events) {
        this.context = context.getApplicationContext(); this.events = events;
        workers.allowCoreThreadTimeOut(true); deadlines.setRemoveOnCancelPolicy(true);
    }

    public void observe(Runnable r) { if (r != null) observers.add(new Observer() { @Override public void onChanged() { r.run(); } }); }
    public int activeJobs() { return jobs.get(); }
    public boolean hasDangerousJob() { for (Session s : sessions.values()) if (s.busy.get() && s.dangerous) return true; return false; }
    public boolean isClosed() { return closed.get(); }
    public List<Session> sessions() { List<Session> out = new ArrayList<>(sessions.values()); out.sort(Comparator.comparing(s -> s.title)); return out; }
    public Session selected() { return selectedId == null ? null : sessions.get(selectedId); }
    public Session get(String id) { return sessions.get(id); }
    public void select(String id) { if (sessions.containsKey(id)) { selectedId = id; changed(); } }
    private synchronized AdbIdentity identity() throws Exception { if (identity == null) identity = AdbIdentity.load(context); return identity; }
    public void message(String text) { notice = text; changed(); }

    private void changed() {
        events.changed();
        for (Observer o : observers) { try { o.onChanged(); } catch (Exception ignored) {} }
    }

    // ===== Connection =====

    public void connectTcp(String host, int port, boolean tls) {
        String address = host.trim(); String id = (tls ? "tls:" : "tcp:") + address + ":" + port;
        connectionTask("连接 " + address + ":" + port, scope -> {
            TcpAdbLink link = scope.own(new TcpAdbLink(address, port, identity(), false));
            link.connect();
            AdbTransport connection = scope.own(AdbWireConnection.connect(link, identity(), tls));
            Session session = new Session(id, address + ":" + port + (tls ? " · TLS" : " · TCP"), connection, null, null);
            publish(session, scope); scope.release(connection); scope.release(link);
            context.getSharedPreferences("mod_adb__connection", Context.MODE_PRIVATE).edit()
                    .putString("last_host", address).putInt("last_port", port).putBoolean("last_tls", tls).apply();
            return "连接成功：" + session.title;
        });
    }

    public void pair(String host, int port, String code) {
        connectionTask("无线配对", scope -> {
            TcpAdbLink link = scope.own(new TcpAdbLink(host.trim(), port, identity(), true));
            AdbPairing.pair(link, identity(), code, scope);
            return "配对成功。请使用无线调试页面的连接端口（不是配对端口）建立 TLS 连接。";
        });
    }

    public void connectUsb(UsbDevice device, boolean fastboot) {
        connectionTask("连接 USB " + device.getDeviceName(), scope -> {
            UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            UsbAdbLink link = scope.own(new UsbAdbLink(manager, device, fastboot ? 3 : 1));
            String id = "usb:" + device.getDeviceName();
            String title = (device.getProductName() == null ? device.getDeviceName() : device.getProductName()) + (fastboot ? " · Fastboot" : " · USB ADB");
            AdbTransport transport = fastboot ? null : scope.own(AdbWireConnection.connect(link, identity(), false));
            FastbootClient client = fastboot ? scope.own(new FastbootClient(link.fastbootLink())) : null;
            publish(new Session(id, title, transport, client, device.getDeviceName()), scope);
            if (transport != null) scope.release(transport); if (client != null) scope.release(client); scope.release(link);
            return "已连接：" + title;
        });
    }

    private void publish(Session session, ResourceScope scope) throws IOException {
        synchronized (sessions) {
            scope.check();
            if (closed.get()) throw new IOException("工作台已退出");
            Session previous = sessions.get(session.id);
            if (previous != null && previous.available()) { session.close(); throw new IOException("该设备已有连接，请先断开旧会话"); }
            if (previous != null) previous.close();
            sessions.put(session.id, session); selectedId = session.id;
        }
    }

    interface ConnectionWork { String run(ResourceScope scope) throws Exception; }
    private void connectionTask(String name, ConnectionWork task) {
        if (closed.get()) return;
        ResourceScope scope = new ResourceScope(); connecting.add(scope); jobs.incrementAndGet(); message(name + "…");
        ScheduledFuture<?> limit = deadlines.schedule(scope::close, 35, TimeUnit.SECONDS);
        try {
            workers.execute(() -> {
                try { scope.check(); notice = task.run(scope); }
                catch (Exception | LinkageError error) { notice = name + "失败：" + explain(error); }
                finally { limit.cancel(false); scope.close(); connecting.remove(scope); jobs.decrementAndGet(); changed(); }
            });
        } catch (RejectedExecutionException error) {
            limit.cancel(false); scope.close(); connecting.remove(scope); jobs.decrementAndGet(); message("任务队列已满，请稍后再试");
        }
    }

    public void cancelConnections() { for (ResourceScope scope : connecting) scope.close(); }

    public void disconnect(String id) {
        Session session = sessions.get(id);
        if (session == null) return;
        if (session.dangerous && session.busy.get()) { message("刷写或提交操作进行中，不能主动断开设备"); return; }
        sessions.remove(id, session); session.close();
        if (id.equals(selectedId)) selectedId = sessions.isEmpty() ? null : sessions.keySet().iterator().next();
        changed();
    }

    public void detached(String deviceName) {
        for (Session session : sessions.values()) if (deviceName.equals(session.usbName)) {
            session.close(); session.append("USB 已拔出。正在执行的操作结果可能未知，请检查目标设备。");
        }
        changed();
    }

    public void cancel(String id) {
        Session session = sessions.get(id);
        if (session == null || session.task == null) return;
        if (session.dangerous) { message("不能中断当前高风险操作；发生断线时请检查设备状态"); return; }
        session.task.close(); session.append("已请求取消；已提交到设备的更改不会自动撤销。"); changed();
    }

    // ===== Generic work runner =====

    public void run(String id, String label, boolean dangerous, long timeoutSeconds, Work work) {
        Session session = sessions.get(id);
        if (session == null || !session.available() || closed.get()) { message("设备未连接，请重新连接"); return; }
        if (!session.busy.compareAndSet(false, true)) { message("该设备有任务正在执行，请等待或取消"); return; }
        ResourceScope scope = new ResourceScope(); session.task = scope; session.operation = label; session.dangerous = dangerous;
        jobs.incrementAndGet(); session.append("▶ " + label + "  [" + session.title + "]"); changed();
        ScheduledFuture<?> limit = deadlines.schedule(scope::close, timeoutSeconds, TimeUnit.SECONDS);
        try {
            workers.execute(() -> {
                try { scope.check(); String result = work.run(session, scope); scope.check(); session.append(result); }
                catch (Exception | LinkageError error) { session.append("失败/未确认：" + explain(error)); }
                finally {
                    limit.cancel(false); scope.close(); session.task = null; session.dangerous = false; session.operation = "";
                    session.busy.set(false); jobs.decrementAndGet(); changed();
                }
            });
        } catch (RejectedExecutionException error) {
            limit.cancel(false); scope.close(); session.task = null; session.dangerous = false; session.busy.set(false); jobs.decrementAndGet(); message("任务队列已满");
        }
    }

    AdbTransport scoped(Session session, ResourceScope scope) throws IOException {
        if (session.transport == null) throw new IOException("此操作需要 ADB 连接，不适用于 Fastboot");
        return new AdbTransport() {
            @Override public Channel open(String service) throws IOException { scope.check(); return scope.own(session.transport.open(service)); }
            @Override public boolean isOpen() { return !scope.isClosed() && session.transport.isOpen(); }
            @Override public void close() { scope.close(); }
        };
    }

    // ===== Shell / Commands =====

    public void shell(String id, String command) {
        run(id, "执行命令", false, 120, (session, scope) -> {
            AdbShell.Result result = AdbShell.exec(session.transport, scope, command);
            return result.output + "\n[退出码 " + result.exitCode + "]";
        });
    }

    public void command(String id, String label, String command) {
        run(id, label, false, 120, (session, scope) -> {
            AdbShell.Result result = AdbShell.exec(session.transport, scope, command); result.requireSuccess(); return result.output.isEmpty() ? "已完成" : result.output;
        });
    }

    // ===== App management =====

    public void listApps(String id, String filter) {
        run(id, "读取应用列表", false, 45, (session, scope) -> {
            if (!filter.equals("-3") && !filter.equals("-s") && !filter.isEmpty()) throw new IOException("应用筛选条件不合法");
            AdbShell.Result result = AdbShell.exec(session.transport, scope, "pm list packages " + filter); result.requireSuccess();
            List<String> packages = new ArrayList<>();
            for (String line : result.output.split("\\r?\\n")) if (line.startsWith("package:")) {
                String name = line.substring(8).trim(); AdbCommands.packageName(name); packages.add(name);
            }
            Collections.sort(packages); session.applications = Collections.unmodifiableList(packages); session.packageFilter = filter;
            return "已读取 " + packages.size() + " 个包；可按包名搜索。";
        });
    }

    public List<AppInfo> listApps(String id) {
        Session session = sessions.get(id);
        if (session == null) return Collections.emptyList();
        List<AppInfo> result = new ArrayList<>();
        boolean isSystem = "-s".equals(session.packageFilter);
        boolean isUser = "-3".equals(session.packageFilter);
        try {
            // Try to get application labels via shell command for better UX
            if (session.transport != null && session.available()) {
                AdbShell.Result labelResult = AdbShell.exec(session.transport, new ResourceScope(),
                        "cmd package list packages -f");
                if (labelResult.exitCode == 0) {
                    java.util.Map<String, String> labels = new java.util.HashMap<>();
                    for (String line : labelResult.output.split("\\r?\\n")) {
                        if (line.startsWith("package:")) {
                            String rest = line.substring(8);
                            int eqIdx = rest.indexOf('=');
                            if (eqIdx > 0) {
                                String pkg = rest.substring(0, eqIdx);
                                String path = rest.substring(eqIdx + 1);
                                // Use package name as label; actual label requires package manager query
                                labels.put(pkg, pkg);
                            }
                        }
                    }
                    for (String pkg : session.applications) {
                        String label = labels.getOrDefault(pkg, pkg);
                        result.add(new AppInfo(pkg, label, isSystem, true));
                    }
                    return result;
                }
            }
        } catch (Exception ignored) {
            // Fall back to simple package names
        }
        for (String pkg : session.applications) {
            result.add(new AppInfo(pkg, pkg, isSystem, true));
        }
        return result;
    }

    public void launchApp(String id, String packageName) {
        command(id, "启动 " + packageName, "monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1");
    }

    public void forceStopApp(String id, String packageName) {
        command(id, "强制停止 " + packageName, "am force-stop " + packageName);
    }

    public void setAppEnabled(String id, String packageName, boolean enabled) {
        command(id, (enabled ? "启用 " : "停用 ") + packageName,
                "pm " + (enabled ? "enable" : "disable") + " " + packageName);
    }

    public void clearAppData(String id, String packageName) {
        command(id, "清除 " + packageName + " 数据", "pm clear " + packageName);
    }

    public void uninstallApp(String id, String packageName) {
        run(id, "卸载 " + packageName, true, 120, (session, scope) -> {
            AdbShell.Result result = AdbShell.exec(session.transport, scope, "pm uninstall --user 0 " + packageName);
            result.requireSuccess();
            return "已卸载 " + packageName;
        });
    }

    // ===== File management =====

    public void listFiles(String id, String path) {
        final String directory = AdbCommands.path(path);
        run(id, "浏览 " + directory, false, 45, (session, scope) -> {
            List<AdbSync.Entry> entries = new AdbSync(scoped(session, scope)).list(directory);
            entries.sort(Comparator.comparing((AdbSync.Entry entry) -> !entry.isDirectory()).thenComparing(entry -> entry.name));
            if (!directory.equals(session.directory)) session.selectedFiles.clear();
            session.directory = directory; session.files = Collections.unmodifiableList(entries); return "目录包含 " + entries.size() + " 项";
        });
    }

    public List<FileInfo> listFileEntries(String id) {
        Session session = sessions.get(id);
        if (session == null) return Collections.emptyList();
        List<FileInfo> result = new ArrayList<>();
        String baseDir = session.directory;
        for (AdbSync.Entry e : session.files) {
            String fullPath = baseDir.endsWith("/") ? baseDir + e.name : baseDir + "/" + e.name;
            result.add(new FileInfo(e.name, fullPath, e.isDirectory(), e.size));
        }
        return result;
    }

    public void createDirectory(String id, String path) {
        run(id, "创建目录", false, 45, (session, scope) -> {
            AdbShell.Result result = AdbShell.exec(session.transport, scope, "mkdir -p " + AdbCommands.path(path));
            result.requireSuccess();
            return "目录已创建";
        });
    }

    public void upload(String id, String directory, List<Uri> uris) {
        List<Uri> chosen = new ArrayList<>(uris);
        run(id, "上传 " + chosen.size() + " 个文件", false, 3600, (session, scope) -> {
            AdbSync sync = new AdbSync(scoped(session, scope)); int completed = 0;
            for (Uri uri : chosen) {
                scope.check(); String remote = AdbCommands.child(directory, displayName(uri));
                try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new IOException("无法读取选择的文件"); scope.own(in);
                    sync.push(remote, in, -1, bytes -> progress(session, "上传 " + remote, bytes)); scope.release(in);
                }
                session.append("已上传 " + remote); completed++;
            }
            return "上传完成：" + completed + " 个文件";
        });
    }

    public void download(String id, String remote, Uri output) {
        run(id, "下载 " + remote, false, 3600, (session, scope) -> {
            try (OutputStream out = context.getContentResolver().openOutputStream(output, "wt")) {
                if (out == null) throw new IOException("无法打开保存位置"); scope.own(out);
                new AdbSync(scoped(session, scope)).pull(remote, out, bytes -> progress(session, "下载", bytes)); scope.release(out);
            }
            return "下载完成（失败或取消时目标文件可能不完整）";
        });
    }

    public String screenshot(String id, Uri output) {
        Session session = sessions.get(id);
        if (session == null || !session.available() || closed.get()) {
            message("设备未连接，请重新连接");
            return null;
        }
        if (!session.busy.compareAndSet(false, true)) {
            message("该设备有任务正在执行，请等待或取消");
            return null;
        }
        ResourceScope scope = new ResourceScope(); session.task = scope; session.operation = "保存屏幕截图";
        jobs.incrementAndGet(); session.append("▶ 保存屏幕截图 [" + session.title + "]");
        ScheduledFuture<?> limit = deadlines.schedule(scope::close, 45, TimeUnit.SECONDS);
        final String[] savedPath = new String[1];
        try {
            workers.execute(() -> {
                try {
                    scope.check();
                    try (AdbTransport.Channel channel = scope.own(session.transport.open("exec:screencap -p"));
                         OutputStream out = context.getContentResolver().openOutputStream(output, "wt")) {
                        if (out == null) throw new IOException("无法打开保存位置");
                        scope.own(out);
                        byte[] signature = new byte[8];
                        new DataInputStream(channel.input()).readFully(signature);
                        if (!Arrays.equals(signature, new byte[]{(byte)137,80,78,71,13,10,26,10}))
                            throw new IOException("设备未返回 PNG 截图");
                        out.write(signature);
                        copy(channel.input(), out, scope, 64L * 1024 * 1024);
                        scope.release(out);
                    }
                    // Get the file path for display
                    String path = getFilePathFromUri(output);
                    savedPath[0] = path;
                    scope.check();
                    session.append("截图已保存：" + path);
                } catch (Exception | LinkageError error) {
                    session.append("截图失败：" + explain(error));
                } finally {
                    limit.cancel(false); scope.close(); session.task = null;
                    session.busy.set(false); jobs.decrementAndGet(); changed();
                }
            });
        } catch (RejectedExecutionException error) {
            limit.cancel(false); scope.close(); session.task = null;
            session.busy.set(false); jobs.decrementAndGet();
            message("任务队列已满");
        }
        return null;
    }

    private String getFilePathFromUri(Uri uri) {
        if (uri == null) return "未知位置";
        String path = uri.getPath();
        if (path != null) return path;
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (Exception ignored) {}
        return uri.toString();
    }

    // ===== Install =====

    public void installUris(String id, List<Uri> uris) {
        List<Uri> chosen = new ArrayList<>(uris);
        run(id, "安装 APK / split APK", true, 3600, (session, scope) -> {
            List<File> staged = new ArrayList<>();
            try {
                for (Uri uri : chosen) staged.add(stage(uri, scope));
                return installFiles(session, scope, staged);
            } finally { for (File file : staged) if (!file.delete()) file.deleteOnExit(); }
        });
    }

    public void installLocal(String id, String packageName) {
        run(id, "从本机安装 " + packageName, true, 3600, (session, scope) -> {
            ApplicationInfo app = context.getPackageManager().getApplicationInfo(packageName, 0);
            List<File> sources = new ArrayList<>(); sources.add(new File(app.sourceDir));
            if (app.splitSourceDirs != null) for (String split : app.splitSourceDirs) sources.add(new File(split));
            return installFiles(session, scope, sources);
        });
    }

    private String installFiles(Session session, ResourceScope scope, List<File> files) throws Exception {
        List<AdbPackageInstaller.Source> sources = new ArrayList<>();
        for (File file : files) sources.add(new AdbPackageInstaller.Source() {
            @Override public String name() { return file.getName(); }
            @Override public long size() { return file.length(); }
            @Override public InputStream open() throws IOException { return new FileInputStream(file); }
        });
        return AdbPackageInstaller.install(session.transport, scope, sources, bytes -> progress(session, "安装上传", bytes));
    }

    // ===== Device info =====

    public DeviceInfo getDeviceInfo(String id) {
        Session session = sessions.get(id);
        if (session == null) return null;
        // Synchronously query device info
        try {
            AdbShell.Result brandResult = AdbShell.exec(session.transport, new ResourceScope(), "getprop ro.product.brand");
            AdbShell.Result modelResult = AdbShell.exec(session.transport, new ResourceScope(), "getprop ro.product.model");
            AdbShell.Result verResult = AdbShell.exec(session.transport, new ResourceScope(), "getprop ro.build.version.release");
            AdbShell.Result sdkResult = AdbShell.exec(session.transport, new ResourceScope(), "getprop ro.build.version.sdk");
            AdbShell.Result buildResult = AdbShell.exec(session.transport, new ResourceScope(), "getprop ro.build.display.id");
            AdbShell.Result serialResult = AdbShell.exec(session.transport, new ResourceScope(), "getprop ro.serialno");
            AdbShell.Result dpiResult = AdbShell.exec(session.transport, new ResourceScope(), "getprop ro.sf.lcd_density");
            AdbShell.Result resResult = AdbShell.exec(session.transport, new ResourceScope(), "wm size");

            String brand = brandResult.output.trim();
            String model = modelResult.output.trim();
            String androidVer = verResult.output.trim();
            String sdkVer = sdkResult.output.trim();
            String buildId = buildResult.output.trim();
            String serial = serialResult.output.trim();
            int dpi = 320;
            try { dpi = Integer.parseInt(dpiResult.output.trim()); } catch (NumberFormatException ignored) {}

            int w = 0, h = 0;
            String resOut = resResult.output.trim();
            if (resOut.contains("x")) {
                String[] parts = resOut.split("x");
                try { w = Integer.parseInt(parts[0].trim()); h = Integer.parseInt(parts[1].trim()); } catch (NumberFormatException ignored) {}
            }

            return new DeviceInfo(model, brand, androidVer, sdkVer, buildId, serial, dpi, w, h);
        } catch (Exception e) {
            return new DeviceInfo("unknown", "unknown", "unknown", "0", "unknown", id, 320, 0, 0);
        }
    }

    // ===== Screen control / Scrcpy =====

    public ScrcpySession startScrcpy(String id, int maxSize, int bitRate) {
        Session session = sessions.get(id);
        if (session == null) return null;
        if (session.scrcpy != null) { session.scrcpy.close(); session.scrcpy = null; }
        ScrcpySession.Listener listener = new ScrcpySession.Listener() {
            @Override public void onSize(int width, int height) { }
            @Override public void onError(String message) { session.append("投屏错误：" + message); changed(); }
        };
        android.graphics.SurfaceTexture dummyTexture = new android.graphics.SurfaceTexture(0);
        Surface dummySurface = new Surface(dummyTexture);
        session.scrcpy = new ScrcpySession(context, session.transport, dummySurface, maxSize, bitRate, listener);
        // Do not auto-start; caller will set real Surface and call start()
        return session.scrcpy;
    }

    public void stopScrcpy(String id) {
        Session session = sessions.get(id);
        if (session != null && session.scrcpy != null) { session.scrcpy.close(); session.scrcpy = null; }
    }

    public ScrcpySession getScrcpy(String id) {
        Session session = sessions.get(id);
        return session != null ? session.scrcpy : null;
    }

    // ===== System settings =====

    public void setDpi(String id, int dpi) {
        command(id, "设置 DPI", "wm density " + dpi + " reset");
    }

    public void setResolution(String id, int width, int height) {
        command(id, "设置分辨率", "wm size " + width + "x" + height);
    }

    public void setAnimationScale(String id, float scale) {
        command(id, "设置动画缩放", "settings put global window_animation_scale " + scale);
    }

    public void reboot(String id, String mode) {
        run(id, "重启到 " + mode, true, 180, (session, scope) -> {
            AdbShell.Result result = AdbShell.exec(session.transport, scope, "reboot " + mode);
            return "设备正在重启";
        });
    }

    // ===== Fastboot =====

    public void fastbootCommand(String id, String command) {
        run(id, "Fastboot " + command, !command.startsWith("getvar:"), 180, (session, scope) -> {
            if (session.fastboot == null) throw new IOException("需要 Fastboot USB 连接");
            scope.own(session.fastboot);
            String result = session.fastboot.command(command); scope.release(session.fastboot); return result;
        });
    }

    public String fastbootCommand(String command) {
        Session selected = selected();
        if (selected == null || selected.fastboot == null) return "未连接 Fastboot 设备";
        try {
            return selected.fastboot.command(command);
        } catch (IOException e) {
            return "执行失败：" + explain(e);
        }
    }

    public String getFastbootInfo() {
        Session selected = selected();
        if (selected == null || selected.fastboot == null) return "未检测到 Fastboot 设备";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("设备：").append(selected.title).append("\n");
            sb.append("序列号：").append(selected.id).append("\n");
            try { sb.append("产品：").append(selected.fastboot.command("getvar product")).append("\n"); } catch (Exception ignored) {}
            try { sb.append("型号：").append(selected.fastboot.command("getvar product:model")).append("\n"); } catch (Exception ignored) {}
            return sb.toString();
        } catch (Exception e) {
            return "读取失败：" + explain(e);
        }
    }

    public void flashPartition(String partition, String imagePath) {
        Session selected = selected();
        if (selected == null) { message("未连接设备"); return; }
        try {
            Uri uri = Uri.fromFile(new File(imagePath));
            flash(selected.id, partition, uri, false);
        } catch (Exception e) {
            message("刷写失败：" + explain(e));
        }
    }

    public void bootTempImage(String imagePath) {
        Session selected = selected();
        if (selected == null) { message("未连接设备"); return; }
        try {
            Uri uri = Uri.fromFile(new File(imagePath));
            flash(selected.id, "boot", uri, true);
        } catch (Exception e) {
            message("启动失败：" + explain(e));
        }
    }

    public void erasePartition(String partition) {
        Session selected = selected();
        if (selected == null || selected.fastboot == null) { message("需要 Fastboot 连接"); return; }
        run(selected.id, "擦除分区 " + partition, true, 300, (session, scope) -> {
            scope.own(session.fastboot);
            String result = session.fastboot.command("erase:" + partition);
            scope.release(session.fastboot);
            return result;
        });
    }

    void flash(String id, String partition, Uri image, boolean bootOnly) {
        run(id, bootOnly ? "临时启动镜像" : "刷写分区 " + partition, true, 3600, (session, scope) -> {
            if (session.fastboot == null) throw new IOException("需要 Fastboot USB 连接");
            File file = stage(image, scope);
            try (InputStream in = new FileInputStream(file)) {
                scope.own(in); scope.own(session.fastboot);
                if (bootOnly) session.fastboot.boot(in, file.length(), bytes -> progress(session, "传输镜像", bytes));
                else session.fastboot.flash(partition, in, file.length(), bytes -> progress(session, "传输镜像", bytes));
                scope.release(session.fastboot); scope.release(in);
                return "设备报告操作成功。请核对设备启动状态。";
            } finally { if (!file.delete()) file.deleteOnExit(); }
        });
    }

    // ===== Transfer helpers =====

    File stage(Uri uri, ResourceScope scope) throws IOException {
        File directory = new File(context.getCacheDir(), "adb-transfer");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法建立传输缓存");
        File file = File.createTempFile("upload-", ".bin", directory); boolean complete = false;
        try (InputStream in = context.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(file)) {
            if (in == null) throw new IOException("无法读取选择文件"); scope.own(in); scope.own(out);
            copy(in, out, scope, 4L * 1024 * 1024 * 1024 - 1); scope.release(in); scope.release(out); complete = true; return file;
        } finally { if (!complete) file.delete(); }
    }

    String displayName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) { String name = cursor.getString(0); if (name != null && !name.isEmpty()) return name; }
        } catch (RuntimeException ignored) { }
        return "file-" + UUID.randomUUID();
    }

    private void progress(Session session, String operation, long bytes) { session.operation = operation + " · " + (bytes / 1024) + " KiB"; changed(); }

    static long copy(InputStream in, OutputStream out, ResourceScope scope, long maximum) throws IOException {
        byte[] buffer = new byte[65536]; long total = 0;
        for (int n; (n = in.read(buffer)) != -1;) {
            scope.check(); if (n == 0) throw new IOException("文件流停滞");
            total += n; if (total > maximum) throw new IOException("文件超过当前传输大小上限"); out.write(buffer, 0, n);
        }
        return total;
    }

    static String explain(Throwable error) {
        String message = error.getMessage(); if (message == null || message.isEmpty()) message = error.getClass().getSimpleName();
        Throwable cause = error.getCause(); if (cause != null && cause.getMessage() != null) message += " / " + cause.getMessage();
        return message.length() > 1200 ? message.substring(0, 1200) : message;
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        cancelConnections(); for (Session session : sessions.values()) session.close(); sessions.clear();
        workers.shutdownNow(); deadlines.shutdownNow();
    }
}
