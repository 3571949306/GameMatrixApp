package com.gamecenter.app.tools;

public final class ToolSection {
    public final String id;
    public final String title;
    public final int contentLayoutId;
    public boolean visible;

    public ToolSection(String id, String title, int contentLayoutId, boolean visible) {
        this.id = id;
        this.title = title;
        this.contentLayoutId = contentLayoutId;
        this.visible = visible;
    }
}
