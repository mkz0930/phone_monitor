package com.phonemonitor.app;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 常见 App 包名 → 友好中文名 + 分类 + emoji
 */
public class AppDictionary {

    public static class AppInfo {
        public final String name;
        public final String category;
        public final String emoji;

        AppInfo(String name, String category, String emoji) {
            this.name = name;
            this.category = category;
            this.emoji = emoji;
        }
    }

    private static final Map<String, AppInfo> DICT = new HashMap<>();

    static {
        // ===== 社交 =====
        put("com.tencent.mm",                "微信",       "社交", "💬");
        put("com.tencent.mobileqq",          "QQ",         "社交", "💬");
        put("com.tencent.tim",               "TIM",        "社交", "💬");
        put("com.whatsapp",                  "WhatsApp",   "社交", "💬");
        put("org.telegram.messenger",        "Telegram",   "社交", "💬");
        put("com.discord",                   "Discord",    "社交", "💬");
        put("com.instagram.android",         "Instagram",  "社交", "📸");
        put("com.twitter.android",           "X/Twitter",  "社交", "🐦");
        put("com.zhihu.android",             "知乎",       "社交", "💬");
        put("com.sina.weibo",                "微博",       "社交", "💬");
        put("com.facebook.katana",           "Facebook",   "社交", "💬");
        put("com.facebook.orca",             "Messenger",  "社交", "💬");
        put("com.linkedin.android",          "LinkedIn",   "社交", "💼");
        put("com.snapchat.android",          "Snapchat",   "社交", "👻");
        put("com.reddit.frontpage",          "Reddit",     "社交", "💬");
        put("com.tencent.wework",            "企业微信",   "社交", "💬");
        put("jp.naver.line.android",         "LINE",       "社交", "💬");
        put("com.kakao.talk",                "KakaoTalk",  "社交", "💬");
        put("com.immomo.momo",               "陌陌",       "社交", "💬");
        put("com.p1.mobile.putong",          "探探",       "社交", "💬");
        put("com.ss.android.lark",           "飞书",       "社交", "💬");
        put("com.larksuite.suite",           "Lark",       "社交", "💬");

        // ===== 视频 =====
        put("com.ss.android.ugc.aweme",      "抖音",       "视频", "🎵");
        put("com.zhiliaoapp.musically",       "TikTok",    "视频", "🎵");
        put("com.google.android.youtube",     "YouTube",   "视频", "▶️");
        put("tv.danmaku.bili",                "哔哩哔哩",  "视频", "📺");
        put("com.tencent.qqlive",             "腾讯视频",  "视频", "📺");
        put("com.youku.phone",                "优酷",      "视频", "📺");
        put("com.qiyi.video",                 "爱奇艺",    "视频", "📺");
        put("com.hunantv.imgo.activity",      "芒果TV",    "视频", "📺");
        put("com.netflix.mediaclient",        "Netflix",   "视频", "📺");
        put("com.smile.gifmaker",             "快手",      "视频", "🎵");
        put("com.kuaishou.nebula",            "快手极速版", "视频", "🎵");
        put("com.disney.disneyplus",          "Disney+",   "视频", "📺");
        put("com.hbo.hbonow",                "HBO Max",    "视频", "📺");
        put("com.amazon.avod.thirdpartyclient","Prime Video","视频","📺");
        put("com.ss.android.ugc.aweme.lite",  "抖音极速版", "视频", "🎵");
        put("com.tencent.weishi",             "微视",      "视频", "📺");

        // ===== 音乐 =====
        put("com.netease.cloudmusic",         "网易云音乐", "音乐", "🎵");
        put("com.tencent.qqmusic",            "QQ音乐",    "音乐", "🎵");
        put("com.kugou.android",              "酷狗音乐",  "音乐", "🎵");
        put("cn.kuwo.player",                 "酷我音乐",  "音乐", "🎵");
        put("com.spotify.music",              "Spotify",   "音乐", "🎵");
        put("com.apple.android.music",        "Apple Music","音乐", "🎵");

        // ===== 工作 =====
        put("com.google.android.gm",          "Gmail",     "工作", "📧");
        put("com.microsoft.office.outlook",    "Outlook",   "工作", "📧");
        put("com.microsoft.teams",             "Teams",     "工作", "💼");
        put("com.slack",                       "Slack",     "工作", "💼");
        put("com.alibaba.android.rimet",       "钉钉",     "工作", "💼");
        put("notion.id",                       "Notion",    "工作", "📝");
        put("com.todoist",                     "Todoist",   "工作", "✅");
        put("com.google.android.calendar",     "Google日历","工作", "📅");
        put("com.google.android.apps.docs",    "Google Docs","工作","📄");
        put("com.microsoft.office.word",       "Word",      "工作", "📄");
        put("com.microsoft.office.excel",      "Excel",     "工作", "📊");
        put("com.google.android.apps.meetings","Google Meet","工作", "📹");
        put("us.zoom.videomeetings",           "Zoom",      "工作", "📹");
        put("com.tencent.wemeet",              "腾讯会议",  "工作", "📹");

        // ===== 购物 =====
        put("com.taobao.taobao",              "淘宝",      "购物", "🛒");
        put("com.jingdong.app.mall",          "京东",      "购物", "🛒");
        put("com.xunmeng.pinduoduo",          "拼多多",    "购物", "🛒");
        put("com.xingin.xhs",                 "小红书",    "购物", "📕");
        put("com.amazon.mShop.android.shopping","Amazon",   "购物", "🛒");
        put("com.meituan",                    "美团",      "购物", "🛒");
        put("me.ele",                         "饿了么",    "购物", "🛒");
        put("com.dianping.v1",                "大众点评",  "购物", "🛒");
        put("com.achievo.vipshop",            "唯品会",    "购物", "🛒");

        // ===== 阅读 =====
        put("com.douban.frodo",               "豆瓣",      "阅读", "📖");
        put("com.ss.android.article.news",    "今日头条",  "阅读", "📰");
        put("com.tencent.news",               "腾讯新闻",  "阅读", "📰");
        put("com.netease.newsreader.activity", "网易新闻",  "阅读", "📰");
        put("com.ss.android.article.lite",    "头条极速版", "阅读", "📰");
        put("com.chaozh.iReaderFree",         "掌阅",      "阅读", "📖");
        put("com.dragon.read",                "番茄小说",  "阅读", "📖");
        put("com.amazon.kindle",              "Kindle",    "阅读", "📖");

        // ===== 游戏 =====
        put("com.tencent.tmgp.sgame",         "王者荣耀",  "游戏", "🎮");
        put("com.tencent.ig",                 "和平精英",  "游戏", "🎮");
        put("com.miHoYo.Yuanshen",            "原神",      "游戏", "🎮");
        put("com.miHoYo.hkrpg",              "崩坏:星穹铁道","游戏","🎮");
        put("com.supercell.clashofclans",     "部落冲突",  "游戏", "🎮");
        put("com.riotgames.league.wildrift",  "英雄联盟手游","游戏","🎮");

        // ===== 工具 =====
        put("com.android.chrome",             "Chrome",    "工具", "🌐");
        put("com.UCMobile",                   "UC浏览器",  "工具", "🌐");
        put("com.baidu.searchbox",            "百度",      "工具", "🔍");
        put("com.autonavi.minimap",           "高德地图",  "工具", "🗺️");
        put("com.baidu.BaiduMap",             "百度地图",  "工具", "🗺️");
        put("com.google.android.apps.maps",   "Google Maps","工具","🗺️");
        put("com.eg.android.AlipayGphone",    "支付宝",    "工具", "💰");
        put("com.tencent.mtt",               "QQ浏览器",   "工具", "🌐");
        put("com.android.vending",            "Play商店",  "工具", "🏪");
        put("com.apple.android.store",        "App Store", "工具", "🏪");
        put("com.samsung.android.app.sbrowser","三星浏览器","工具", "🌐");
        put("mark.via",                       "Via浏览器",  "工具", "🌐");
        put("com.google.android.apps.photos", "Google相册", "工具", "🖼️");
        put("com.android.camera",             "相机",      "工具", "📷");
        put("com.android.settings",           "设置",      "系统", "⚙️");
        put("com.android.systemui",           "系统界面",  "系统", "⚙️");
        put("com.android.launcher3",          "桌面",      "系统", "🏠");

        // ===== 出行 =====
        put("com.sdu.didi.psnger",            "滴滴出行",  "出行", "🚗");
        put("com.Qunar",                      "去哪儿",    "出行", "✈️");
        put("ctrip.android.view",             "携程",      "出行", "✈️");
        put("com.didi.global.passenger",      "DiDi",      "出行", "🚗");
    }

    private static void put(String pkg, String name, String category, String emoji) {
        DICT.put(pkg, new AppInfo(name, category, emoji));
    }

    /**
     * 查找 App 信息，找不到返回 null
     */
    public static AppInfo lookup(String packageName) {
        // 精确匹配
        AppInfo info = DICT.get(packageName);
        if (info != null) return info;

        // 模糊匹配（处理变体包名）
        for (Map.Entry<String, AppInfo> entry : DICT.entrySet()) {
            if (packageName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 获取分类，未知返回 "其他"
     */
    public static String getCategory(String packageName) {
        AppInfo info = lookup(packageName);
        return info != null ? info.category : "其他";
    }

    /**
     * 获取所有已知分类（有序），key=分类名, value=emoji
     */
    public static Map<String, String> getAllCategories() {
        LinkedHashMap<String, String> cats = new LinkedHashMap<>();
        cats.put("社交", "💬");
        cats.put("视频", "🎬");
        cats.put("音乐", "🎵");
        cats.put("工作", "💼");
        cats.put("购物", "🛒");
        cats.put("阅读", "📖");
        cats.put("游戏", "🎮");
        cats.put("工具", "🔧");
        cats.put("出行", "🚗");
        cats.put("系统", "⚙️");
        cats.put("其他", "📦");
        return cats;
    }

    /**
     * 获取分类 emoji
     */
    public static String getCategoryEmoji(String category) {
        switch (category) {
            case "社交": return "💬";
            case "视频": return "🎬";
            case "音乐": return "🎵";
            case "工作": return "💼";
            case "购物": return "🛒";
            case "阅读": return "📖";
            case "游戏": return "🎮";
            case "工具": return "🔧";
            case "出行": return "🚗";
            case "系统": return "⚙️";
            default:     return "📦";
        }
    }
}
