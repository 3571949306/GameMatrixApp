package com.gamecenter.app.adb;

import android.hardware.usb.*;
import com.gamecenter.app.adb.protocol.AdbWireConnection;
import com.gamecenter.app.adb.protocol.FastbootClient;
import java.io.*;

/** USB ownership stays in :adb; no forced interface claim or implicit permission request. */
final class UsbAdbLink implements AdbWireConnection.Link {
    private final UsbDeviceConnection connection;
    private final UsbInterface usbInterface;
    private final UsbEndpoint in, out;
    private volatile int timeout;
    private volatile boolean closed;
    static UsbInterface find(UsbDevice device, int protocol) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface face = device.getInterface(i);
            if (face.getInterfaceClass() == 255 && face.getInterfaceSubclass() == 66 && face.getInterfaceProtocol() == protocol) return face;
        }
        return null;
    }
    UsbAdbLink(UsbManager manager, UsbDevice device, int protocol) throws IOException {
        if (!manager.hasPermission(device)) throw new IOException("需要系统 USB 授权");
        usbInterface = find(device, protocol);
        if (usbInterface == null) throw new IOException("未找到 ADB/Fastboot USB 接口");
        UsbEndpoint input = null, output = null;
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint point = usbInterface.getEndpoint(i);
            if (point.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                if (point.getDirection() == UsbConstants.USB_DIR_IN) input = point; else output = point;
            }
        }
        if (input == null || output == null) throw new IOException("USB bulk 端点缺失");
        in = input; out = output; connection = manager.openDevice(device);
        if (connection == null) throw new IOException("无法打开 USB 设备");
        if (!connection.claimInterface(usbInterface, false)) { connection.close(); throw new IOException("USB 接口正在被其他应用使用"); }
    }
    private final InputStream input = new InputStream() {
        private final byte[] buffer = new byte[16384]; private int position, length;
        @Override public int read() throws IOException { byte[] one = new byte[1]; return read(one, 0, 1) < 0 ? -1 : one[0] & 255; }
        @Override public int read(byte[] bytes, int offset, int count) throws IOException {
            if (count == 0) return 0;
            if (closed) throw new IOException("USB 已关闭");
            if (position == length) {
                length = connection.bulkTransfer(in, buffer, buffer.length, timeout); position = 0;
                if (length <= 0) throw new IOException("USB 读取失败或超时");
            }
            int n = Math.min(count, length - position); System.arraycopy(buffer, position, bytes, offset, n); position += n; return n;
        }
    };
    private final OutputStream output = new OutputStream() {
        @Override public void write(int value) throws IOException { write(new byte[]{(byte) value}); }
        @Override public synchronized void write(byte[] bytes, int offset, int count) throws IOException { writeUsb(bytes, offset, count); }
    };
    private void writeUsb(byte[] bytes, int offset, int count) throws IOException {
        while (count > 0) {
            if (closed) throw new IOException("USB 已关闭");
            int n = connection.bulkTransfer(out, bytes, offset, Math.min(count, 16384), 15000);
            if (n <= 0) throw new IOException("USB 写入失败或超时");
            offset += n; count -= n;
        }
    }
    FastbootClient.Link fastbootLink() {
        return new FastbootClient.Link() {
            @Override public int read(byte[] bytes) throws IOException {
                if (closed) throw new IOException("USB 已关闭");
                int n = connection.bulkTransfer(in, bytes, bytes.length, 30000);
                if (n <= 0) throw new IOException("Fastboot 无回复，操作结果待确认；请勿自动重试"); return n;
            }
            @Override public void write(byte[] bytes, int offset, int count) throws IOException { writeUsb(bytes, offset, count); }
            @Override public void close() throws IOException { UsbAdbLink.this.close(); }
        };
    }
    @Override public InputStream input() { return input; }
    @Override public OutputStream output() { return output; }
    @Override public void upgradeTls() throws IOException { throw new IOException("USB ADB 不支持 STLS"); }
    @Override public void setReadTimeout(int millis) { timeout = millis; }
    @Override public synchronized void close() { if (!closed) { closed = true; connection.releaseInterface(usbInterface); connection.close(); } }
}
