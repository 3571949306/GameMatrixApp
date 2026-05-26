package com.gamecenter.app.ai;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.gamecenter.app.ai.cloud.AiApiClient;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AI 助手 ViewModel — 管理 AI 聊天的 UI 状态与业务逻辑。
 *
 * <p>你可以把这个类想象成一个"聊天室管理员"：
 * 它负责记录谁说了什么（聊天消息列表）、当前聊天室的状态（空闲/加载中/成功/出错），
 * 以及在用户离开聊天室时打扫卫生（取消未完成的请求、关闭线程池）。</p>
 *
 * <p>该 ViewModel 遵循 Android MVVM 架构，将 AI 聊天的业务逻辑从 UI 层（AiFragment）中剥离，
 * 实现数据与界面的解耦。ViewModel 的生命周期独立于 Activity/Fragment 的重建，
 * 因此在屏幕旋转等配置变更时，聊天消息和 UI 状态不会丢失。</p>
 *
 * <p>核心设计决策：</p>
 * <ul>
 *   <li>使用 {@link MutableLiveData}&lt;{@link AiUiState}&gt; 管理 UI 状态，
 *       通过 {@link LiveData} 暴露给 UI 层观察，实现响应式数据更新。</li>
 *   <li>使用 {@link ExecutorService}（单线程线程池）执行云端 AI 调用，
 *       避免阻塞主线程，同时保证请求串行执行，防止并发冲突。</li>
 *   <li>通过 {@link AtomicBoolean} 标记当前是否有正在进行的请求，
 *       防止用户重复点击发送导致请求堆积。</li>
 *   <li>在 {@link #onCleared()} 中取消所有进行中的请求并关闭线程池，
 *       防止 ViewModel 销毁后后台线程继续运行导致内存泄漏。</li>
 *   <li>所有异步结果通过 {@link MutableLiveData#postValue(Object)} 投递到主线程，
 *       保证 UI 层在主线程安全地更新界面。</li>
 * </ul>
 *
 * <p>使用方式（在 AiFragment 中）：</p>
 * <pre>
 *   AiViewModel viewModel = new ViewModelProvider(this).get(AiViewModel.class);
 *   viewModel.getUiState().observe(getViewLifecycleOwner(), state -&gt; {
 *       // 根据 state 更新 UI
 *   });
 *   viewModel.getChatMessages().observe(getViewLifecycleOwner(), messages -&gt; {
 *       // 更新消息列表
 *   });
 *   viewModel.sendMessage("你好");
 * </pre>
 */
public class AiViewModel extends ViewModel {

    private static final String TAG = "AiViewModel";

    /**
     * 后台线程执行器，用于执行云端 AI 调用等耗时操作。
     * <p>
     * 使用单线程线程池（{@link Executors#newSingleThreadExecutor}），
     * 保证 AI 请求串行执行，避免并发推理导致资源竞争和响应乱序。
     * 就像银行柜台只开一个窗口，客户排队依次办理，不会出现插队和混乱。
     */
    private final ExecutorService executor;

    /**
     * 当前是否正在进行 AI 请求的标记。
     * <p>
     * 使用 {@link AtomicBoolean} 保证多线程环境下的原子性操作，
     * 防止发送按钮重复触发导致多个请求同时执行。
     */
    private final AtomicBoolean isRequesting = new AtomicBoolean(false);

    /**
     * 当前使用的 AI 供应商配置。
     * <p>
     * 默认使用本地规则引擎配置，用户可通过 {@link #setProvider(AiProviderConfig)} 切换。
     * 切换供应商后，后续的 AI 请求将使用新的配置。
     */
    private AiProviderConfig currentProvider;

    /**
     * UI 状态的内部可变 LiveData，仅供 ViewModel 内部修改。
     * <p>
     * 遵循 LiveData 的"最小暴露原则"：ViewModel 内部持有 MutableLiveData，
     * 对外只暴露不可变的 LiveData，防止 UI 层意外修改状态。
     */
    private final MutableLiveData<AiUiState> _uiState = new MutableLiveData<>();

    /**
     * UI 状态的对外只读 LiveData，供 UI 层观察。
     */
    private final LiveData<AiUiState> uiState = _uiState;

    /**
     * 聊天消息列表的内部可变 LiveData。
     */
    private final MutableLiveData<List<ChatMessage>> _chatMessages = new MutableLiveData<>();

    /**
     * 聊天消息列表的对外只读 LiveData，供 UI 层观察。
     */
    private final LiveData<List<ChatMessage>> chatMessages = _chatMessages;

    /**
     * 内部维护的聊天消息列表，所有增删操作在此列表上进行，
     * 修改后通过 {@link MutableLiveData#postValue(Object)} 通知观察者。
     */
    private final List<ChatMessage> messageList = new ArrayList<>();

    /**
     * 构造 ViewModel，初始化默认状态和线程池。
     * <p>
     * 初始化流程（就像新开张的聊天室，先做好准备工作）：
     * <ol>
     *   <li>设置默认供应商为本地规则引擎</li>
     *   <li>设置初始 UI 状态为 Idle（空闲）</li>
     *   <li>创建单线程执行器</li>
     *   <li>初始化空的消息列表</li>
     * </ol>
     */
    public AiViewModel() {
        this.currentProvider = AiProviderConfig.localConfig();
        this._uiState.setValue(AiUiState.idle());
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "GC-AI-ViewModel");
            t.setDaemon(true);
            return t;
        });
        this._chatMessages.setValue(Collections.unmodifiableList(new ArrayList<>(messageList)));
    }

    /**
     * 获取 UI 状态的 LiveData，供 UI 层观察状态变化。
     * <p>
     * 观察此 LiveData 可以在 UI 状态变化时自动更新界面：
     * <ul>
     *   <li>{@link AiUiState.State#IDLE} — 空闲状态，可正常交互</li>
     *   <li>{@link AiUiState.State#LOADING} — 加载中，应禁用发送按钮并显示进度</li>
     *   <li>{@link AiUiState.State#SUCCESS} — 请求成功，可展示 AI 回复</li>
     *   <li>{@link AiUiState.State#ERROR} — 请求失败，应显示错误提示</li>
     * </ul>
     *
     * @return UI 状态的只读 LiveData
     */
    public LiveData<AiUiState> getUiState() {
        return uiState;
    }

    /**
     * 获取聊天消息列表的 LiveData，供 UI 层观察消息变化。
     * <p>
     * 每次消息列表发生变化（新增、清空）时，观察者都会收到通知，
     * 拿到的是不可变的消息列表副本，防止 UI 层意外修改数据。
     *
     * @return 聊天消息列表的只读 LiveData
     */
    public LiveData<List<ChatMessage>> getChatMessages() {
        return chatMessages;
    }

    /**
     * 发送用户消息并请求 AI 回复。
     * <p>
     * 流程（就像寄信：写信 → 投递 → 等回信 → 读回信）：
     * <ol>
     *   <li>校验输入内容，空消息直接忽略</li>
     *   <li>检查是否有正在进行的请求，防止重复提交</li>
     *   <li>将用户消息添加到聊天列表并通知 UI</li>
     *   <li>切换到 Loading 状态，UI 层应显示加载指示器</li>
     *   <li>在后台线程中调用 AI API 获取回复</li>
     *   <li>根据结果更新 UI 状态和消息列表</li>
     * </ol>
     *
     * @param message 用户输入的消息文本
     */
    public void sendMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        if (!isRequesting.compareAndSet(false, true)) {
            Log.w(TAG, "已有请求正在进行中，忽略重复发送");
            return;
        }

        ChatMessage userMsg = new ChatMessage(message.trim(), true);
        addMessage(userMsg);
        _uiState.postValue(AiUiState.loading());

        executor.execute(() -> {
            try {
                AiApiClient client = new AiApiClient(currentProvider);
                AiResult result = client.chatSync("你是一个有用的助手。请用简体中文回答。", message.trim());

                if (result.success) {
                    ChatMessage aiMsg = new ChatMessage(result.content, false);
                    addMessage(aiMsg);
                    _uiState.postValue(AiUiState.success(result.content));
                } else {
                    _uiState.postValue(AiUiState.error(result.message));
                }
            } catch (Exception e) {
                Log.e(TAG, "AI 请求异常", e);
                _uiState.postValue(AiUiState.error("请求失败: " + e.getMessage()));
            } finally {
                isRequesting.set(false);
            }
        });
    }

    /**
     * 切换 AI 供应商配置。
     * <p>
     * 切换后，后续的 {@link #sendMessage(String)} 调用将使用新的供应商配置。
     * 若当前有正在进行的请求，该请求仍使用旧配置完成，
     * 新配置仅对后续请求生效。
     * 就像换了一家快递公司，已经在途的包裹还是由旧公司送达，
     * 新下的单才走新公司。
     *
     * @param config 新的 AI 供应商配置；若为 null 则忽略
     */
    public void setProvider(AiProviderConfig config) {
        if (config != null) {
            this.currentProvider = config;
            Log.d(TAG, "供应商已切换为: " + config.providerName + " · " + config.modelName);
        }
    }

    /**
     * 清空所有聊天历史记录。
     * <p>
     * 清空后，消息列表变为空，UI 状态重置为 Idle。
     * 此操作不可撤销，就像清空了聊天记录就无法恢复一样。
     */
    public void clearHistory() {
        messageList.clear();
        _chatMessages.postValue(Collections.unmodifiableList(new ArrayList<>()));
        _uiState.postValue(AiUiState.idle());
        Log.d(TAG, "聊天历史已清空");
    }

    /**
     * 向消息列表中添加一条消息，并通知观察者。
     * <p>
     * 每次添加消息后，都会创建一个不可变的列表副本并通过
     * {@link MutableLiveData#postValue(Object)} 通知 UI 层更新。
     * 使用不可变副本是为了防止 UI 层直接修改列表导致数据不一致。
     *
     * @param message 要添加的聊天消息
     */
    private void addMessage(ChatMessage message) {
        messageList.add(message);
        _chatMessages.postValue(Collections.unmodifiableList(new ArrayList<>(messageList)));
    }

    /**
     * ViewModel 被销毁时释放资源。
     * <p>
     * 当关联的 Activity/Fragment 被永久销毁（非配置变更）时调用。
     * 在此执行清理工作（就像离职时归还工牌、关闭电脑）：
     * <ul>
     *   <li>立即关闭线程池，中断所有正在执行的 AI 请求</li>
     *   <li>重置请求标记，防止线程池中的任务在关闭后继续修改 LiveData</li>
     * </ul>
     * <p>
     * 注意：使用 {@link ExecutorService#shutdownNow()} 而非 {@link ExecutorService#shutdown()}，
     * 因为 shutdown() 会等待正在执行的任务完成，而 ViewModel 销毁后不应再有任务继续运行。
     */
    @Override
    protected void onCleared() {
        super.onCleared();
        isRequesting.set(false);
        executor.shutdownNow();
        Log.d(TAG, "ViewModel 已清理，线程池已关闭");
    }

    /**
     * AI UI 状态模型 — 描述 AI 聊天界面的当前状态。
     *
     * <p>你可以把 AiUiState 想象成红绿灯：
     * 绿灯（Idle）表示可以通行（用户可以发送消息），
     * 黄灯（Loading）表示等待中（AI 正在思考），
     * 绿灯闪烁（Success）表示通行成功（AI 回复已到达），
     * 红灯（Error）表示禁止通行（出了问题，需要处理）。</p>
     *
     * <p>该类封装了 AI 聊天界面的四种状态，UI 层通过观察 {@link LiveData}&lt;AiUiState&gt;
     * 来响应状态变化并更新界面。采用不可变设计（final 类 + final 字段），
     * 确保状态一旦创建就不会被修改，保证线程安全。</p>
     *
     * <p>四种状态说明：</p>
     * <ul>
     *   <li>{@link State#IDLE} — 空闲状态，等待用户输入</li>
     *   <li>{@link State#LOADING} — 加载状态，AI 正在处理请求</li>
     *   <li>{@link State#SUCCESS} — 成功状态，AI 已返回结果，附带回复文本</li>
     *   <li>{@link State#ERROR} — 错误状态，请求失败，附带错误描述</li>
     * </ul>
     */
    public static final class AiUiState {

        /**
         * UI 状态枚举，定义四种可能的状态。
         */
        public enum State {
            /** 空闲状态，等待用户输入 */
            IDLE,
            /** 加载状态，AI 正在处理请求 */
            LOADING,
            /** 成功状态，AI 已返回结果 */
            SUCCESS,
            /** 错误状态，请求失败 */
            ERROR
        }

        /** 当前状态 */
        public final State state;

        /** 附加文本；SUCCESS 时为 AI 回复内容，ERROR 时为错误描述，其他状态为空字符串 */
        public final String text;

        private AiUiState(State state, String text) {
            this.state = state;
            this.text = text != null ? text : "";
        }

        /**
         * 创建空闲状态实例。
         *
         * @return Idle 状态的 AiUiState
         */
        public static AiUiState idle() {
            return new AiUiState(State.IDLE, "");
        }

        /**
         * 创建加载中状态实例。
         *
         * @return Loading 状态的 AiUiState
         */
        public static AiUiState loading() {
            return new AiUiState(State.LOADING, "");
        }

        /**
         * 创建成功状态实例。
         *
         * @param text AI 回复的文本内容
         * @return Success 状态的 AiUiState
         */
        public static AiUiState success(String text) {
            return new AiUiState(State.SUCCESS, text);
        }

        /**
         * 创建错误状态实例。
         *
         * @param message 错误描述信息
         * @return Error 状态的 AiUiState
         */
        public static AiUiState error(String message) {
            return new AiUiState(State.ERROR, message);
        }
    }

    /**
     * 聊天消息模型 — 表示一条对话消息。
     *
     * <p>你可以把 ChatMessage 想象成微信聊天中的一条消息气泡：
     * 右边蓝色的是你发的（isUser=true），左边白色的是对方回的（isUser=false），
     * 每条消息都有内容和发送时间。</p>
     *
     * <p>该类是 AiViewModel 内部使用的消息数据结构，用于在 ViewModel 和 UI 层之间
     * 传递聊天消息。采用不可变设计（final 类 + final 字段），保证消息一旦创建就不会被修改。</p>
     *
     * <p>与 {@link com.gamecenter.app.ai.data.AiMessage} 的区别：</p>
     * <ul>
     *   <li>ChatMessage 是 ViewModel 层的轻量级消息模型，仅包含展示所需的最少字段</li>
     *   <li>AiMessage 是数据层的完整消息模型，包含 id、role、taskType、source 等持久化字段</li>
     *   <li>ChatMessage 适用于 UI 绑定场景，AiMessage 适用于数据存储和路由场景</li>
     * </ul>
     */
    public static final class ChatMessage {

        /** 消息文本内容 */
        public final String content;

        /** 是否为用户发送的消息；true=用户消息（右侧蓝色气泡），false=AI 回复（左侧白色气泡） */
        public final boolean isUser;

        /** 消息创建的时间戳（毫秒级 Unix 时间），用于消息排序和时间显示 */
        public final long timestamp;

        /**
         * 构造聊天消息，自动使用当前系统时间作为时间戳。
         *
         * @param content 消息文本内容
         * @param isUser  是否为用户消息
         */
        public ChatMessage(String content, boolean isUser) {
            this(content, isUser, System.currentTimeMillis());
        }

        /**
         * 全参数构造方法，用于从已知数据（如数据库恢复）创建消息实例。
         *
         * @param content   消息文本内容
         * @param isUser    是否为用户消息
         * @param timestamp 消息时间戳（毫秒）
         */
        public ChatMessage(String content, boolean isUser, long timestamp) {
            this.content = content;
            this.isUser = isUser;
            this.timestamp = timestamp;
        }
    }
}
