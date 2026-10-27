package com.gamecenter.app.network.p2p.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * P2P 连接候选地址模型 (Candidate)。
 * <p>
 * 封装本端或对端的网络端点（IP、端口、候选类型、优先级、NAT 属性）。
 * 支持 JSON 序列化与反序列化，便于通过信令服务器进行 SDP/Candidate 交换。
 */
public class Candidate implements Comparable<Candidate> {

    private final CandidateType type;
    private final String ip;
    private final int port;
    private final int priority;
    private final NatType natType;
    @Nullable
    private final String foundation; // 相同基础标识（如相同网卡或相同 STUN 服务器）

    public Candidate(@NonNull CandidateType type, @NonNull String ip, int port, int priority, @NonNull NatType natType) {
        this(type, ip, port, priority, natType, null);
    }

    public Candidate(@NonNull CandidateType type, @NonNull String ip, int port, int priority, @NonNull NatType natType, @Nullable String foundation) {
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.ip = Objects.requireNonNull(ip, "ip must not be null").trim();
        this.port = port;
        this.priority = priority;
        this.natType = Objects.requireNonNull(natType, "natType must not be null");
        this.foundation = foundation;
    }

    @NonNull
    public CandidateType getType() {
        return type;
    }

    @NonNull
    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public int getPriority() {
        return priority;
    }

    @NonNull
    public NatType getNatType() {
        return natType;
    }

    @Nullable
    public String getFoundation() {
        return foundation;
    }

    public boolean isIpv6() {
        return ip.contains(":") && !ip.contains(".");
    }

    /**
     * 转换为紧凑可传输的 JSON 对象。
     */
    @NonNull
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", type.name());
            json.put("ip", ip);
            json.put("port", port);
            json.put("priority", priority);
            json.put("natType", natType.name());
            if (foundation != null) {
                json.put("foundation", foundation);
            }
        } catch (JSONException ignored) {
        }
        return json;
    }

    /**
     * 从 JSON 对象解析 Candidate。
     */
    @Nullable
    public static Candidate fromJson(@NonNull JSONObject json) {
        try {
            CandidateType type = CandidateType.valueOf(json.getString("type"));
            String ip = json.getString("ip");
            int port = json.getInt("port");
            int priority = json.optInt("priority", type.getDefaultPriority());
            NatType natType = NatType.valueOf(json.optString("natType", NatType.UNKNOWN.name()));
            String foundation = json.optString("foundation", null);
            return new Candidate(type, ip, port, priority, natType, foundation);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public int compareTo(Candidate o) {
        // 优先级高的排在前面（降序）
        return Integer.compare(o.priority, this.priority);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Candidate)) return false;
        Candidate candidate = (Candidate) o;
        return port == candidate.port &&
                type == candidate.type &&
                ip.equals(candidate.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, ip, port);
    }

    @NonNull
    @Override
    public String toString() {
        return "Candidate{" +
                "type=" + type +
                ", addr=" + (isIpv6() ? "[" + ip + "]" : ip) + ":" + port +
                ", priority=" + priority +
                ", nat=" + natType +
                '}';
    }
}
