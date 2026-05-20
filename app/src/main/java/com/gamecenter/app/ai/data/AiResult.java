package com.gamecenter.app.ai.data;

/**
 * AI 统一返回结果 — 封装 AI 处理请求的返回数据。
 *
 * <p>该类是 AI 模块所有处理请求的统一返回类型，无论是成功还是失败，
 * 都通过该类封装结果。采用 Builder 模式构建，支持链式调用，
 * 使调用方可以灵活地附加 source 和 errorCode 等可选信息。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>不可变设计（final 类 + final 字段），结果一旦创建不可修改</li>
 *   <li>使用 Builder 模式，支持可选参数的链式设置</li>
 *   <li>成功和失败通过静态工厂方法区分，语义清晰，避免构造器参数混淆</li>
 *   <li>errorCode 字段支持细粒度错误分类，便于上层做差异化处理</li>
 * </ul>
 */
public final class AiResult {

    /** 处理是否成功 */
    public final boolean success;

    /** 结果描述信息；成功时通常为空，失败时为错误描述 */
    public final String message;

    /** AI 处理输出的文本内容；成功时有效 */
    public final String content;

    /** 结果来源标识，"local" 表示本地端侧模型，"cloud" 表示云端 API */
    public final String source;

    /** 错误码，空字符串表示无错误；非空时可用于错误分类和国际化提示 */
    public final String errorCode;

    /**
     * 私有构造方法，仅通过 Builder 创建实例。
     *
     * @param builder 构建器实例，包含所有字段的值
     */
    private AiResult(Builder builder) {
        this.success = builder.success;
        this.message = builder.message;
        this.content = builder.content;
        this.source = builder.source;
        this.errorCode = builder.errorCode;
    }

    /**
     * 创建一个成功结果的 Builder。
     * 成功时 text 参数赋值给 content 字段。
     *
     * @param content AI 处理输出的文本内容
     * @return Builder 实例，可继续链式设置 source、errorCode 等可选字段
     */
    public static Builder success(String content) {
        return new Builder(true, content);
    }

    /**
     * 创建一个失败结果的 Builder。
     * 失败时 text 参数赋值给 message 字段。
     *
     * @param message 错误描述信息
     * @return Builder 实例，可继续链式设置 source、errorCode 等可选字段
     */
    public static Builder fail(String message) {
        return new Builder(false, message);
    }

    /**
     * AiResult 的构建器，支持链式调用设置可选参数。
     *
     * <p>默认值：</p>
     * <ul>
     *   <li>source 默认为 "cloud"（大多数 AI 调用来自云端）</li>
     *   <li>errorCode 默认为空字符串（无错误）</li>
     * </ul>
     */
    public static class Builder {
        private boolean success;
        private String message;
        private String content;
        private String source = "cloud";
        private String errorCode = "";

        /**
         * 私有构造方法，根据成功/失败状态将 text 赋值到不同字段。
         * 成功时 text → content，失败时 text → message，避免字段混淆。
         *
         * @param success 处理是否成功
         * @param text    成功时为输出内容，失败时为错误信息
         */
        private Builder(boolean success, String text) {
            this.success = success;
            if (success) {
                this.content = text;
            } else {
                this.message = text;
            }
        }

        /**
         * 设置结果来源。
         *
         * @param source 来源标识（"local" / "cloud"）
         * @return 当前 Builder 实例，支持链式调用
         */
        public Builder source(String source) {
            this.source = source;
            return this;
        }

        /**
         * 设置错误码。
         *
         * @param code 错误码字符串
         * @return 当前 Builder 实例，支持链式调用
         */
        public Builder errorCode(String code) {
            this.errorCode = code;
            return this;
        }

        /**
         * 构建 AiResult 实例。
         *
         * @return 不可变的 AiResult 实例
         */
        public AiResult build() {
            return new AiResult(this);
        }
    }
}
