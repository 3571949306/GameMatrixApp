package com.gamecenter.app.browser.core.player;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * 播放器接管所需的注入脚本集合。
 *
 * <p><b>设计取舍（关键）</b>：夸克式"内置播放器"有两条路——
 * <ol>
 *   <li><b>原生播放器直连</b>：拿视频 URL 交给 VideoView/ExoPlayer 重新缓冲。缺点致命：
 *       现代站点（B 站、腾讯、爱奇艺）多为 MSE/HLS，源是 {@code blob:} 或分片 m3u8，
 *       脱离页面上下文根本拿不到完整流，直连成功率极低。</li>
 *   <li><b>接管页面 video 元素（本实现的默认路径）</b>：视频仍由网页内核解码播放，
 *       我们把 {@code <video>} 提到最前铺满，并用原生 UI 覆盖在它上面，
 *       所有控制通过 DOM API 下发；因此 blob:/MSE/HLS 通常仍由原页面负责，
 *       但 DRM、Shadow DOM、iframe、Canvas/WebGL 等实现仍取决于站点，不能承诺全部兼容。</li>
 * </ol>
 * 因此本类提供的是第 2 条路的脚本；直链（mp4/mp3）才走原生播放器兜底。
 *
 * <p><b>安全约束</b>：所有脚本只接受 Java 侧构造的数值/固定枚举参数，
 * 任何来自网页的字符串都不会被拼进脚本，避免二次注入。
 */
public final class BrowserVideoJs {

    private BrowserVideoJs() {}

    // ===== 动作名（内部常量，禁止外部传参） =====
    public static final String ACTION_PLAY = "play";
    public static final String ACTION_PAUSE = "pause";
    public static final String ACTION_TOGGLE = "toggle";
    /** 参数为秒（double）。 */
    public static final String ACTION_SEEK = "seek";
    /** 参数为倍速（double）。 */
    public static final String ACTION_RATE = "rate";
    public static final String ACTION_MUTE = "mute";
    /** 参数为音量 0-1（double）。 */
    public static final String ACTION_VOLUME = "volume";
    public static final String ACTION_LOOP = "loop";

    /**
     * 探测脚本：挑选"最值得接管"的 video 元素并回传状态 JSON。
     *
     * <p>打分规则：可视面积 × 正在播放加权 × 已有尺寸加权 × 有真实源加权，
     * 这样页面里那些 1x1 的占位/广告 video 不会被误接管。
     */
    public static final String DETECT =
            "(function(){try{" +
            "var vs=document.querySelectorAll('video');" +
            "if(!vs||!vs.length)return JSON.stringify({count:0});" +
            "var best=null,bestScore=-1,bestIdx=-1;" +
            "for(var i=0;i<vs.length;i++){" +
            "var v=vs[i];var r=v.getBoundingClientRect();" +
            "var hasSize=(v.videoWidth>0&&v.videoHeight>0);" +
            "var playing=(!v.paused&&!v.ended&&v.currentTime>0);" +
            "var area=Math.max(1,(r.width||0)*(r.height||0));" +
            "var score=area*(playing?3:1)*(hasSize?1.5:0.3)*((v.currentSrc||v.src)?1:0.2);" +
            "if(score>bestScore){bestScore=score;best=v;bestIdx=i;}}" +
            "if(!best)return JSON.stringify({count:0});" +
            "var d=best.duration;if(!isFinite(d))d=0;" +
            "return JSON.stringify({count:vs.length,index:bestIdx," +
            "currentTime:best.currentTime||0,duration:d," +
            "paused:!!best.paused,ended:!!best.ended,muted:!!best.muted," +
            "volume:best.volume,rate:best.playbackRate||1," +
            "width:best.videoWidth||0,height:best.videoHeight||0," +
            "currentSrc:best.currentSrc||best.src||'',title:document.title||''});" +
            "}catch(e){return JSON.stringify({count:0});}})();";

    /** 锁定要接管的 video 元素（后续所有动作都作用于它）。 */
    @NonNull
    public static String select(int index) {
        return "(function(i){try{" +
                "var vs=document.querySelectorAll('video');" +
                "var v=(vs&&vs[i])?vs[i]:(vs&&vs[0]);" +
                "window.__gmVideo=v||null;return !!v;" +
                "}catch(e){return false;}})(" + Math.max(0, index) + ");";
    }

    /**
     * 生成一个动作脚本。
     *
     * @param action 必须是本类的 ACTION_* 常量
     * @param value  数值参数（秒 / 倍速 / 音量），非法值在 JS 侧兜底
     */
    @NonNull
    public static String action(@NonNull String action, double value) {
        // This is a WebView script boundary. Do not trust callers to pass only the
        // internal constants: reject unknown values before any string concatenation.
        if (!isSupportedAction(action)) {
            return "(function(){return 0;})();";
        }
        String safeValue = String.format(Locale.US, "%.6f", sanitize(value));
        return "(function(a,v){try{" +
                "var V=window.__gmVideo;" +
                "if(!V){var vs=document.querySelectorAll('video');V=(vs&&vs[0])||null;}" +
                "if(!V)return 0;" +
                "switch(a){" +
                "case 'play':V.play();return 1;" +
                "case 'pause':V.pause();return 1;" +
                "case 'toggle':if(V.paused){V.play();return 1;}V.pause();return 0;" +
                "case 'seek':if(isFinite(v)){V.currentTime=Math.max(0,v);}return 1;" +
                "case 'rate':V.playbackRate=v;V.defaultPlaybackRate=v;return 1;" +
                "case 'mute':V.muted=!V.muted;return V.muted?1:0;" +
                "case 'volume':V.volume=Math.min(1,Math.max(0,v));return 1;" +
                "case 'loop':V.loop=!V.loop;return V.loop?1:0;" +
                "default:return 0;}" +
                "}catch(e){return 0;}})('" + action + "'," + safeValue + ");";
    }

    private static boolean isSupportedAction(@NonNull String action) {
        return ACTION_PLAY.equals(action)
                || ACTION_PAUSE.equals(action)
                || ACTION_TOGGLE.equals(action)
                || ACTION_SEEK.equals(action)
                || ACTION_RATE.equals(action)
                || ACTION_MUTE.equals(action)
                || ACTION_VOLUME.equals(action)
                || ACTION_LOOP.equals(action);
    }

    /**
     * 接管：把 video 元素提到 body 末尾并铺满视口，从而"跳过网页自己的播放器"。
     *
     * <p>会先备份原始 style / 父节点 / 后继节点，供 {@link #RELEASE} 还原。
     * 幂等：重复调用不会重复备份。
     */
    public static final String TAKE_OVER =
            "(function(){try{" +
            "var v=window.__gmVideo;if(!v)return 0;" +
            "if(v.__gmTakeoverSaved!==true){" +
            "v.__gmTakeoverSaved=true;" +
            "v.__gmSavedStyle=v.getAttribute('style');" +
            "v.__gmSavedParent=v.parentNode;" +
            "v.__gmSavedNext=v.nextSibling;" +
            "var root=document.documentElement;" +
            "v.__gmSavedOverflow=root?root.style.getPropertyValue('overflow'):'';" +
            "v.__gmSavedOverflowPriority=root?root.style.getPropertyPriority('overflow'):'';}" +
            "var s=v.style;" +
            "s.setProperty('position','fixed','important');" +
            "s.setProperty('left','0px','important');" +
            "s.setProperty('top','0px','important');" +
            "s.setProperty('width','100vw','important');" +
            "s.setProperty('height','100vh','important');" +
            "s.setProperty('object-fit','contain','important');" +
            "s.setProperty('z-index','2147483647','important');" +
            "s.setProperty('background','#000','important');" +
            "s.setProperty('margin','0','important');" +
            "s.setProperty('max-width','none','important');" +
            "s.setProperty('max-height','none','important');" +
            "s.setProperty('transform','none','important');" +
            "if(document.body&&v.parentNode!==document.body){document.body.appendChild(v);}" +
            "try{if(document.documentElement)document.documentElement.style.setProperty('overflow','hidden','important');}catch(e){}" +
            "return 1;" +
            "}catch(e){return 0;}})();";

    /** 还原接管前的 DOM 结构与内联样式。 */
    public static final String RELEASE =
            "(function(){try{" +
            "var v=window.__gmVideo;if(!v)return 0;" +
            "if(v.__gmTakeoverSaved!==true)return 1;" +
            "var saved=v.__gmSavedStyle;" +
            "try{if(saved===undefined||saved===null)v.removeAttribute('style');" +
            "else v.setAttribute('style',saved);}catch(e){}" +
            "var p=v.__gmSavedParent,n=v.__gmSavedNext;" +
            "if(p&&typeof p.insertBefore==='function'){" +
            "try{if(n&&n.parentNode===p){p.insertBefore(v,n);}else{p.appendChild(v);}}catch(e){}}" +
            "try{var root=document.documentElement;" +
            "if(root){var ov=v.__gmSavedOverflow||'';var op=v.__gmSavedOverflowPriority||'';" +
            "if(ov)root.style.setProperty('overflow',ov,op);else root.style.removeProperty('overflow');}}catch(e){}" +
            "v.__gmTakeoverSaved=undefined;v.__gmSavedStyle=undefined;" +
            "v.__gmSavedParent=undefined;v.__gmSavedNext=undefined;" +
            "v.__gmSavedOverflow=undefined;v.__gmSavedOverflowPriority=undefined;" +
            "return 1;" +
            "}catch(e){return 0;}})();";

    /**
     * 只改样式、不移动 DOM 节点的温和接管模式。
     *
     * <p>少数站点（会监听 DOM 变化的播放器）在节点被移动后会重渲染或抛错，
     * 此时降级使用该模式：视频仍留在原容器内，仅靠 z-index 与 fixed 定位压在最上层。
     */
    public static final String TAKE_OVER_STYLE_ONLY =
            "(function(){try{" +
            "var v=window.__gmVideo;if(!v)return 0;" +
            "if(v.__gmTakeoverSaved!==true){" +
            "v.__gmTakeoverSaved=true;" +
            "v.__gmSavedStyle=v.getAttribute('style');" +
            "v.__gmSavedParent=v.parentNode;" +
            "v.__gmSavedNext=v.nextSibling;" +
            "var root=document.documentElement;" +
            "v.__gmSavedOverflow=root?root.style.getPropertyValue('overflow'):'';" +
            "v.__gmSavedOverflowPriority=root?root.style.getPropertyPriority('overflow'):'';}" +
            "var s=v.style;" +
            "s.setProperty('position','fixed','important');" +
            "s.setProperty('left','0px','important');" +
            "s.setProperty('top','0px','important');" +
            "s.setProperty('width','100vw','important');" +
            "s.setProperty('height','100vh','important');" +
            "s.setProperty('object-fit','contain','important');" +
            "s.setProperty('z-index','2147483647','important');" +
            "s.setProperty('background','#000','important');" +
            "s.setProperty('transform','none','important');" +
            "return 1;" +
            "}catch(e){return 0;}})();";

    /**
     * 接管效果校验：脚本执行成功 ≠ 视频真的被提到最前。
     *
     * <p>部分站点会监听 DOM 变化并重渲染，把被移动的 video 元素塞回原容器或直接销毁，
     * 导致"接管成功"但画面消失。这个脚本回传元素的实际几何与可见性，
     * 由 {@code BrowserVideoController} 判定是否需要降级或回滚。
     */
    public static final String VERIFY =
            "(function(){try{" +
            "var v=window.__gmVideo;if(!v)return JSON.stringify({ok:false,reason:'no-element'});" +
            "if(!v.isConnected)return JSON.stringify({ok:false,reason:'detached'});" +
            "var r=v.getBoundingClientRect();" +
            "var style=window.getComputedStyle(v);" +
            "var vis=(style.display!=='none')&&(style.visibility!=='hidden')" +
            "&&(parseFloat(style.opacity||'1')>0.01);" +
            "var z=parseInt(style.zIndex,10);if(isNaN(z))z=0;" +
            "var big=(r.width>=80)&&(r.height>=60);" +
            "return JSON.stringify({ok:(vis&&big),w:Math.round(r.width),h:Math.round(r.height)," +
            "top:Math.round(r.top),left:Math.round(r.left),z:z,vis:vis,paused:!!v.paused});" +
            "}catch(e){return JSON.stringify({ok:false,reason:'exception'});}})();";

    /**
     * 接管后把视频画面摆到指定矩形（小窗模式用）。
     *
     * <p>调用方传入 Android View 像素；脚本用 devicePixelRatio 转成 WebView
     * 视口 CSS 像素，避免高密度设备上的小窗错位。
     */
    @NonNull
    public static String setRect(int leftPx, int topPx, int widthPx, int heightPx) {
        int l = Math.max(0, leftPx);
        int t = Math.max(0, topPx);
        int w = Math.max(1, widthPx);
        int h = Math.max(1, heightPx);
        return "(function(l,t,w,h){try{" +
                "var v=window.__gmVideo;if(!v)return 0;" +
                "var s=v.style;" +
                "s.setProperty('position','fixed','important');" +
                "var d=(window.devicePixelRatio&&isFinite(window.devicePixelRatio))?window.devicePixelRatio:1;" +
                "s.setProperty('left',(l/d)+'px','important');" +
                "s.setProperty('top',(t/d)+'px','important');" +
                "s.setProperty('width',(w/d)+'px','important');" +
                "s.setProperty('height',(h/d)+'px','important');" +
                "s.setProperty('object-fit','contain','important');" +
                "s.setProperty('z-index','2147483647','important');" +
                "s.setProperty('background','#000','important');" +
                "return 1;" +
                "}catch(e){return 0;}})(" + l + "," + t + "," + w + "," + h + ");";
    }

    private static double sanitize(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0d;
        return value;
    }
}
