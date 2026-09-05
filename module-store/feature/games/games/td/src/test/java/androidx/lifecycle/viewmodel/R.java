package androidx.lifecycle.viewmodel;

/**
 * 测试源集补齐的 library 运行期 R（仅 id），语义同 androidx/lifecycle/runtime/R.java：
 * Fragment.performCreateView → ViewTreeViewModelStoreOwner.set 在运行期读取本 id，
 * Maven AAR 不内置 R、宿主构建管线不可用于 Robolectric 单测，故按 AGP 生成语义补齐。
 * 仅限测试使用，不进入任何发布产物。
 */
public final class R {
    /** ViewTree 附加标签：ViewModelStoreOwner（与另两个 ViewTree id 互不相同）。 */
    public static final class id {
        public static final int view_tree_view_model_store_owner = 0x7f0a0002;

        private id() {}
    }

    private R() {}
}
