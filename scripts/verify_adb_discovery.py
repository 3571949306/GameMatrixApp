#!/usr/bin/env python3
"""Guard the opt-in LAN ADB discovery boundary without a Gradle/device dependency."""

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    discovery = (ROOT / "core/adb/src/main/java/com/gamecenter/app/adb/AdbMdnsDiscovery.java").read_text(encoding="utf-8")
    section = (ROOT / "core/adb/src/main/java/com/gamecenter/app/adb/ui/DeviceConnectSection.java").read_text(encoding="utf-8")
    layout = (ROOT / "core/adb/src/main/res/layout/fragment_device_connect.xml").read_text(encoding="utf-8")

    assert 'SERVICE_TYPE = "_adb-tls-connect._tcp."' in discovery
    assert "NsdManager.PROTOCOL_DNS_SD" in discovery
    assert "SCAN_WINDOW_MS = 12_000L" in discovery
    assert "Socket" not in discovery, "mDNS discovery must not become a port scanner"
    assert "stopLocked(false)" in discovery and "listener = null" in discovery
    assert "adb_mdns_scan" in layout and "adb_mdns_device_list" in layout
    assert "new AdbMdnsDiscovery" in section
    assert "selectDiscoveredEndpoint" in section
    assert "if (discovery != null) discovery.close();" in section
    print("ADB mDNS discovery boundary: PASS")


if __name__ == "__main__":
    main()
