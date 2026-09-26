package androidx.lifecycle.runtime;

/**
 * 测试源集补齐的 library 运行期 R（仅 id）。
 *
 * <p>androidx AAR 不内置 R 类：真实宿主构建时由 AGP 为每个 library 包生成运行期 R.jar，
 * 而 td 的 Robolectric 单测脱离宿主构建管线，运行期 classpath 上没有
 * androidx.lifecycle.runtime.R$id。Fragment.attach → performCreateView →
 * ViewTreeLifecycleOwner.set 会以 {@code view.setTag(R.id.view_tree_lifecycle_owner, ...)}
 * 读写该 id，缺失即 NoClassDefFoundError。此处按 AGP 生成语义补齐常量，取值落在
 * 0x7f 应用资源区间以满足 View.setTag 的 key 校验，且与另两个 ViewTree id 互不相同。
 * 仅限测试使用，不进入任何发布产物。
 */
public final class R {
    /** ViewTree 附加标签：lifecycle owner（真实取值由宿主构建决定，测试内任意唯一即可）。 */
    public static final class id {
        public static final int view_tree_lifecycle_owner = 0x7f0a0001;

        private id() {}
    }

    private R() {}
}
