package com.gamecenter.app.network;

import org.json.JSONObject;

import java.util.Queue;

/**
 * WebSocket 客户端辅助工具类。
 *
 * <p>打个比方：这个类就像WebSocket连接的"工具箱"，里面放着两件常用工具——
 * 一把"钥匙提取器"（从URL中提取认证令牌）和一个"信箱管理器"（管理待发送消息队列）。
 * 工具箱本身不干活，但里面的工具能帮其他类更方便地完成工作。</p>
 *
 * <p>在网络模块中的角色：这是WebSocket客户端模式的"小助手"，
 * 为 {@link GameSocketClient} 提供URL解析和消息队列管理两个基础能力，
 * 让GameSocketClient不需要自己处理这些琐碎的细节。</p>
 *
 * <p>提供 WebSocket 连接过程中常用的工具方法，包括从 URL 中提取认证令牌、
 * 管理待发送消息队列等。此类为包级私有（package-private），仅供网络模块内部使用，
 * 不对外暴露。</p>
 *
 * <p>关键设计决策：采用 final 类 + 私有构造器的方式确保不可实例化和不可继承，
 * 所有方法均为静态方法，属于纯工具类模式。</p>
 */
final class WebSocketClientHelper {

    /** 私有构造方法，防止外部实例化这个工具类 */
    private WebSocketClientHelper() {
    }

    /**
     * 从 URL 查询字符串中提取 token 参数值。
     *
     * <p>打个比方：就像从一封信的地址栏里找到"收件人编号"——
     * 在URL中找到"token="后面的那串字符，那就是认证令牌。</p>
     *
     * <p>解析逻辑：查找 "token=" 子串，取其后的内容直到遇到 "&" 分隔符或字符串末尾，
     * 最后对提取的值进行 URL 解码（UTF-8）。若解码失败则返回原始未解码值。</p>
     *
     * @param url 包含 token 参数的完整 URL，例如 "ws://host/path?room=abc&token=xyz123"
     * @return 解码后的 token 字符串；若 url 为 null 或不含 "token=" 参数则返回 null
     */
    static String extractTokenFromUrl(String url) {
        if (url == null) return null;
        int tokenIdx = url.indexOf("token=");
        if (tokenIdx < 0) return null;
        // "token=" 长度为 6，跳过该前缀得到 token 值的起始位置
        int start = tokenIdx + 6;
        // 查找下一个 "&" 分隔符，若不存在则 token 值延伸至字符串末尾
        int end = url.indexOf("&", start);
        String token = end > 0 ? url.substring(start, end) : url.substring(start);
        try {
            return java.net.URLDecoder.decode(token, "UTF-8");
        } catch (Exception e) {
            // URL 解码失败时降级返回原始值，避免因编码问题导致连接中断
            return token;
        }
    }

    /**
     * 向待发送消息队列中安全地添加一条消息，同时保证队列不超过最大容量。
     *
     * <p>打个比方：就像一个固定大小的"待发信箱"，信箱满了就把最早放进去的信扔掉，
     * 腾出空间放新信。这样既不会丢失最新的消息，也不会因为信箱无限增大而占满内存。</p>
     *
     * <p>当队列已满时，采用 FIFO（先进先出）策略移除最早的消息，
     * 确保新消息能够入队。此方法用于 WebSocket 连接尚未建立时缓存待发送消息，
     * 防止因连接延迟导致消息丢失，同时通过容量限制避免内存溢出。</p>
     *
     * @param queue    待发送消息队列，为 null 时直接返回
     * @param message  要入队的 JSON 消息，为 null 时直接返回
     * @param maxSize  队列允许的最大容量，小于等于 0 时直接返回
     */
    static void offerPendingMessage(Queue<JSONObject> queue, JSONObject message, int maxSize) {
        if (queue == null || message == null || maxSize <= 0) return;
        // 队列满时循环移除最早的消息，直到有空间容纳新消息
        while (queue.size() >= maxSize) {
            queue.poll();
        }
        queue.offer(message);
    }
}
