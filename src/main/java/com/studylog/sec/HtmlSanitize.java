package com.studylog.sec;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * 记录正文是图文混排富文本，服务端统一清洗，防存储型 XSS：
 * 只保留编辑器会产生的安全标签/属性，图片允许 data: base64 与 http(s)，
 * 禁止 script/iframe/object/事件属性（on*）/javascript: 链接。
 */
public final class HtmlSanitize {

    private static final Safelist SAFE;

    static {
        Safelist sl = Safelist.none()
                .addTags("a", "b", "blockquote", "br", "code", "div", "em", "i", "img",
                        "li", "ol", "p", "pre", "s", "span", "strong", "u", "ul",
                        "h1", "h2", "h3", "h4", "table", "tbody", "td", "th", "thead", "tr", "hr",
                        "video", "source")
                .addAttributes("a", "href", "title")
                .addAttributes("img", "src", "alt", "style", "width", "height")
                .addAttributes("video", "src", "controls", "preload", "poster", "style", "width", "height")
                .addAttributes("source", "src", "type")
                .addAttributes("span", "style")
                .addAttributes("div", "style")
                .addAttributes("p", "style")
                .addAttributes("td", "colspan", "rowspan")
                .addAttributes("th", "colspan", "rowspan")
                // 仅允许安全的 style 属性值（颜色/粗斜体等），禁止 expression/url(javascript:)
                .addEnforcedAttribute("a", "rel", "nofollow noreferrer")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https")
                .addProtocols("blockquote", "cite", "http", "https");
        // jsoup 原生支持 data: 协议；图片/视频均允许 data: base64 内嵌
        sl.addProtocols("img", "src", "data");
        sl.addProtocols("video", "src", "data", "http", "https");
        sl.addProtocols("video", "poster", "http", "https");
        sl.addProtocols("source", "src", "data", "http", "https");
        SAFE = sl;
    }

    private HtmlSanitize() {}

    public static String clean(String html) {
        if (html == null) return "";
        String out = Jsoup.clean(html, SAFE);
        // jsoup 会放行 style 属性，二次拦截危险 CSS（expression / url() 脚本）
        out = out.replaceAll("(?i)style=\"[^\"]*(expression|javascript:|vbscript:)[^\"]*\"", "");
        return out;
    }
}
