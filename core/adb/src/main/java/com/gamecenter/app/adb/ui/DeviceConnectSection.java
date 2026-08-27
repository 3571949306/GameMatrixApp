package com.gamecenter.app.adb.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.app.Activity;
import android.widget.ListView;
import android.widget.TextView;

import com.gamecenter.app.adb.AdbEngine;
import com.gamecenter.app.adb.AdbMdnsDiscovery;
import com.gamecenter.app.adb.AdbSessionService;
import com.gamecenter.app.adb.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Device connection section: TCP/IP, wireless pairing, USB.
 * Manages connection state and persists connection records.
 */
public final class DeviceConnectSection extends BaseSection {

    private static final String PREFS = "mod_adb__connection";
    private static final String KEY_HOST = "last_host";
    private static final String KEY_PORT = "last_port";
    private static final String KEY_TLS = "last_tls";
    private static final String KEY_PAIR_HOST = "pair_host";
    private static final String KEY_PAIR_PORT = "pair_port";
    private static final String KEY_PAIR_CODE = "pair_code";

    private EditText tcpHost, tcpPort, pairHost, pairPort, pairCode;
    private CheckBox tcpTls;
    private LinearLayout tcpForm, pairForm, usbForm;
    private ListView usbDeviceList, connectionRecords;
    private ArrayAdapter<String> usbAdapter, recordsAdapter;
    private ListView mdnsDeviceList;
    private TextView mdnsScan, mdnsStatus;
    private ArrayAdapter<String> mdnsAdapter;
    private final List<AdbMdnsDiscovery.Endpoint> mdnsEndpoints = new ArrayList<>();
    private AdbMdnsDiscovery discovery;
    private TextView connectedDevice, usbStatus;
    private int activeForm = 0;

    @Override
    public View createView(Activity activity) {
        activityRef = new java.lang.ref.WeakReference<>(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.fragment_device_connect, null);

        tcpHost = view.findViewById(R.id.adb_tcp_host);
        tcpPort = view.findViewById(R.id.adb_tcp_port);
        tcpTls = view.findViewById(R.id.adb_tcp_tls);
        pairHost = view.findViewById(R.id.adb_pair_host);
        pairPort = view.findViewById(R.id.adb_pair_port);
        pairCode = view.findViewById(R.id.adb_pair_code);
        tcpForm = view.findViewById(R.id.adb_tcp_form);
        pairForm = view.findViewById(R.id.adb_pair_form);
        usbForm = view.findViewById(R.id.adb_usb_form);
        connectedDevice = view.findViewById(R.id.adb_connected_device);
        usbStatus = view.findViewById(R.id.adb_usb_status);
        usbDeviceList = view.findViewById(R.id.adb_usb_device_list);
        connectionRecords = view.findViewById(R.id.adb_connection_records);
        mdnsDeviceList = view.findViewById(R.id.adb_mdns_device_list);
        mdnsScan = view.findViewById(R.id.adb_mdns_scan);
        mdnsStatus = view.findViewById(R.id.adb_mdns_status);
        discovery = new AdbMdnsDiscovery(activity.getApplicationContext());

        setupFormTabs(view);
        setupConnectButtons(view);
        loadRecords();
        scanUsbDevices();

        return view;
    }

    private void setupFormTabs(View root) {
        View tcp = root.findViewById(R.id.adb_connect_tcp);
        View pair = root.findViewById(R.id.adb_connect_pair);
        View usb = root.findViewById(R.id.adb_connect_usb);

        tcp.setOnClickListener(v -> selectForm(0));
        pair.setOnClickListener(v -> selectForm(1));
        usb.setOnClickListener(v -> selectForm(2));

        selectForm(0);
    }

    private void selectForm(int index) {
        activeForm = index;
        tcpForm.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pairForm.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        usbForm.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        TextView tcp = (TextView) ((View) tcpForm.getParent()).findViewById(R.id.adb_connect_tcp);
        TextView pair = (TextView) ((View) tcpForm.getParent()).findViewById(R.id.adb_connect_pair);
        TextView usb = (TextView) ((View) tcpForm.getParent()).findViewById(R.id.adb_connect_usb);

        updateTabAppearance(tcp, index == 0);
        updateTabAppearance(pair, index == 1);
        updateTabAppearance(usb, index == 2);
    }

    private void updateTabAppearance(TextView tab, boolean selected) {
        if (selected) {
            tab.setBackgroundResource(R.drawable.adb_tab_bg_selected);
            tab.setTextColor(activity().getColor(R.color.adb_tab_selected));
            tab.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            tab.setBackground(null);
            tab.setTextColor(activity().getColor(R.color.adb_text_secondary));
            tab.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
    }

    private void setupConnectButtons(View root) {
        Activity act = activity();
        if (act == null) return;
        TextView tcpConnect = root.findViewById(R.id.adb_tcp_connect);
        if (tcpConnect != null) {
            tcpConnect.setOnClickListener(v -> connectTcp());
        }
        TextView pairConnect = root.findViewById(R.id.adb_pair_connect);
        if (pairConnect != null) {
            pairConnect.setOnClickListener(v -> pairDevice());
        }
        TextView usbScan = root.findViewById(R.id.adb_usb_scan);
        if (usbScan != null) {
            usbScan.setOnClickListener(v -> scanUsbDevices());
        }
        mdnsScan.setOnClickListener(v -> scanWirelessAdb());
        mdnsAdapter = new ArrayAdapter<String>(act, android.R.layout.simple_list_item_1, new ArrayList<>());
        mdnsDeviceList.setAdapter(mdnsAdapter);
        mdnsDeviceList.setOnItemClickListener((parent, view, position, id) -> selectDiscoveredEndpoint(position));
    }

    private void scanWirelessAdb() {
        Activity act = activity();
        if (act == null || discovery == null) return;
        if (discovery.isScanning()) {
            discovery.stop();
            return;
        }
        mdnsEndpoints.clear();
        mdnsAdapter.clear();
        mdnsDeviceList.setVisibility(View.GONE);
        mdnsStatus.setVisibility(View.VISIBLE);
        mdnsStatus.setText(R.string.adb_device_discovery_scanning);
        mdnsScan.setText(R.string.adb_device_discovery_stop);
        discovery.start(new AdbMdnsDiscovery.Listener() {
            @Override public void onStarted() { }

            @Override public void onEndpoint(AdbMdnsDiscovery.Endpoint endpoint) {
                Activity current = activity();
                if (current == null) return;
                mdnsEndpoints.add(endpoint);
                mdnsAdapter.add(endpoint.name + " · " + endpoint.host + ':' + endpoint.port + " [TLS]");
                mdnsAdapter.notifyDataSetChanged();
                mdnsDeviceList.setVisibility(View.VISIBLE);
                mdnsStatus.setText(current.getString(R.string.adb_device_discovery_found, mdnsEndpoints.size()));
            }

            @Override public void onFinished() {
                Activity current = activity();
                if (current == null) return;
                mdnsScan.setText(R.string.adb_device_discovery_start);
                if (mdnsEndpoints.isEmpty()) mdnsStatus.setText(R.string.adb_device_discovery_empty);
                else mdnsStatus.setText(current.getString(R.string.adb_device_discovery_found, mdnsEndpoints.size()));
            }

            @Override public void onError(String message) {
                Activity current = activity();
                if (current == null) return;
                mdnsScan.setText(R.string.adb_device_discovery_start);
                mdnsStatus.setText(current.getString(R.string.adb_device_connect_failure, message));
            }
        });
    }

    private void selectDiscoveredEndpoint(int position) {
        Activity act = activity();
        if (act == null || position < 0 || position >= mdnsEndpoints.size()) return;
        AdbMdnsDiscovery.Endpoint endpoint = mdnsEndpoints.get(position);
        tcpHost.setText(endpoint.host);
        tcpPort.setText(String.valueOf(endpoint.port));
        tcpTls.setChecked(true);
        selectForm(0);
        showBottomMessage(act.getString(R.string.adb_device_discovery_selected, endpoint.host, endpoint.port));
    }

    private void connectTcp() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        String host = tcpHost.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(tcpPort.getText().toString().trim());
        } catch (NumberFormatException e) {
            showBottomMessage(act.getString(R.string.adb_device_connect_failure, "端口号无效"));
            return;
        }
        if (host.isEmpty()) {
            showBottomMessage(act.getString(R.string.adb_device_connect_failure, "主机地址不能为空"));
            return;
        }
        boolean tls = tcpTls.isChecked();
        engine().connectTcp(host, port, tls);
        showBottomMessage(act.getString(R.string.adb_device_connecting));

        SharedPreferences prefs = act.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_HOST, host).putInt(KEY_PORT, port).putBoolean(KEY_TLS, tls).apply();
    }

    private void pairDevice() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        String host = pairHost.getText().toString().trim();
        String portStr = pairPort.getText().toString().trim();
        String code = pairCode.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            showBottomMessage(act.getString(R.string.adb_device_connect_failure, "端口号无效"));
            return;
        }
        if (host.isEmpty() || code.isEmpty()) {
            showBottomMessage(act.getString(R.string.adb_device_connect_failure, "请填写完整的配对信息"));
            return;
        }
        engine().pair(host, port, code);
        showBottomMessage(act.getString(R.string.adb_device_connecting));

        SharedPreferences prefs = act.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_PAIR_HOST, host)
                .putInt(KEY_PAIR_PORT, port)
                .putString(KEY_PAIR_CODE, code).apply();
    }

    private void scanUsbDevices() {
        Activity act = activity();
        if (act == null) return;

        UsbManager manager = (UsbManager) act.getSystemService(Context.USB_SERVICE);
        if (manager == null) {
            usbStatus.setText(R.string.adb_device_no_usb);
            usbStatus.setTextColor(act.getColor(R.color.adb_text_secondary));
            return;
        }

        Map<String, UsbDevice> devices = manager.getDeviceList();
        List<String> deviceLabels = new ArrayList<>();
        for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
            UsbDevice device = entry.getValue();
            String name = device.getProductName() != null ? device.getProductName() : device.getDeviceName();
            deviceLabels.add(name + " (" + device.getDeviceName() + ")");
        }

        if (deviceLabels.isEmpty()) {
            usbStatus.setText(R.string.adb_device_no_usb);
            usbStatus.setTextColor(act.getColor(R.color.adb_text_secondary));
            usbStatus.setText(R.string.adb_device_no_usb);
        } else {
            usbStatus.setText(act.getString(R.string.adb_device_authorized) + "：" + deviceLabels.size() + " 个设备");
            usbStatus.setTextColor(act.getColor(R.color.adb_connected));
        }

        usbAdapter = new ArrayAdapter<String>(activity(), android.R.layout.simple_list_item_1, deviceLabels);
        usbDeviceList.setAdapter(usbAdapter);
        usbDeviceList.setOnItemClickListener((parent, view, position, id) -> {
            if (engine() == null) return;
            for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
                if (position == 0 && entry.getKey().equals(deviceLabels.get(0).split("\\(")[0].trim())) {
                    engine().connectUsb(entry.getValue(), false);
                    showBottomMessage(act.getString(R.string.adb_device_connecting));
                    return;
                }
            }
            showBottomMessage(act.getString(R.string.adb_device_connect_failure, "请选择设备"));
        });
    }

    private void loadRecords() {
        Activity act = activity();
        if (act == null) return;
        SharedPreferences prefs = act.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String host = prefs.getString(KEY_HOST, "");
        int port = prefs.getInt(KEY_PORT, 5555);
        boolean tls = prefs.getBoolean(KEY_TLS, false);
        if (!host.isEmpty()) {
            tcpHost.setText(host);
            tcpPort.setText(String.valueOf(port));
            tcpTls.setChecked(tls);
        }
        String pairHostVal = prefs.getString(KEY_PAIR_HOST, "");
        if (!pairHostVal.isEmpty()) {
            pairHost.setText(pairHostVal);
            pairPort.setText(String.valueOf(prefs.getInt(KEY_PAIR_PORT, 0)));
            pairCode.setText(prefs.getString(KEY_PAIR_CODE, ""));
        }

        List<String> records = new ArrayList<>();
        String savedHost = prefs.getString(KEY_HOST, "");
        if (!savedHost.isEmpty()) {
            records.add(savedHost + ":" + prefs.getInt(KEY_PORT, 5555) + (prefs.getBoolean(KEY_TLS, false) ? " [TLS]" : ""));
        }
        if (!pairHostVal.isEmpty()) {
            records.add(pairHostVal + ":" + prefs.getInt(KEY_PAIR_PORT, 0) + " [配对]");
        }
        recordsAdapter = new ArrayAdapter<String>(activity(), android.R.layout.simple_list_item_1, records);
        connectionRecords.setAdapter(recordsAdapter);
    }

    @Override
    protected void onEngineBound(AdbEngine engine) {
        Activity act = activity();
        if (act == null) return;
        AdbEngine.Session selected = engine.selected();
        if (selected != null) {
            connectedDevice.setText(selected.title);
            connectedDevice.setTextColor(act.getColor(R.color.adb_connected));
        } else {
            connectedDevice.setText(R.string.adb_topbar_no_device);
            connectedDevice.setTextColor(act.getColor(R.color.adb_text_secondary));
        }
    }

    @Override
    protected void onEngineUnbound() {
        if (discovery != null) discovery.close();
    }

    @Override
    public void onDestroy() {
        if (discovery != null) { discovery.close(); discovery = null; }
        mdnsEndpoints.clear();
        activityRef = null;
    }
}
