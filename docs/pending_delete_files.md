# 待删除文件清单

> 规则 22：任务过程中需要删除的文件尽可能在任务结束之后删除，可以先做一个文件存储要删掉的文件的名称和路径。
> 本文件记录各轮美化中确认无引用、待删/已删的资源文件。

## 2026-07-22 全方位图标/图案美化

| 文件路径 | 状态 | 说明 |
|---|---|---|
| `app/src/main/res/drawable/ic_launcher_logo.png` | 已删除 | launcher 矢量化：`ic_launcher.xml` foreground 由 `@drawable/ic_launcher_logo`(PNG) 改指向 `@drawable/ic_launcher_foreground`(矢量) 后，该 PNG 无任何引用（grep 确认仅原 `ic_launcher.xml` 一处，已改）。编译验证通过后于本轮末尾删除。回滚需 `git checkout -- app/src/main/res/drawable/ic_launcher_logo.png` 并恢复 `ic_launcher.xml` 引用。 |
