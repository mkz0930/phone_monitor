package com.phonemonitor.app;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容自动分类工具
 */
public class ContentClassifier {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern CODE_PATTERN = Pattern.compile(
            "(\\{[^}]*\\}|function\\s|def\\s|class\\s|import\\s|#include|var\\s|let\\s|const\\s|=>|\\);|\\};|public\\s|private\\s|static\\s)");

    /**
     * 分类内容类型
     */
    public static String classifyContent(String text) {
        if (text == null || text.trim().isEmpty()) return "note";

        String trimmed = text.trim();

        // URL → link
        if (URL_PATTERN.matcher(trimmed).find()) {
            // If it's mostly a URL (>60% of content), it's a link
            Matcher m = URL_PATTERN.matcher(trimmed);
            if (m.find()) {
                String url = m.group(1);
                if (url.length() > trimmed.length() * 0.6) return "link";
            }
            // URL + text → article
            if (trimmed.length() > 100) return "article";
            return "link";
        }

        // Code patterns
        if (CODE_PATTERN.matcher(trimmed).find()) {
            // Multiple code indicators = definitely code
            Matcher cm = CODE_PATTERN.matcher(trimmed);
            int codeHits = 0;
            while (cm.find()) codeHits++;
            if (codeHits >= 2) return "code";
        }

        // Long text → article
        if (trimmed.length() > 200) return "article";

        return "note";
    }

    /**
     * 提取第一个 URL
     */
    public static String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = URL_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * 自动生成标题
     */
    public static String generateTitle(String text, String type) {
        if (text == null || text.trim().isEmpty()) return "无标题";

        String trimmed = text.trim();

        switch (type) {
            case "link": {
                String url = extractUrl(trimmed);
                if (url != null) {
                    try {
                        java.net.URL u = new java.net.URL(url);
                        String host = u.getHost().replaceFirst("^www\\.", "");
                        String path = u.getPath();
                        if (path != null && path.length() > 1) {
                            // Use last path segment
                            String[] parts = path.split("/");
                            String last = parts[parts.length - 1];
                            if (!last.isEmpty() && last.length() < 50) {
                                return host + " · " + last.replaceAll("[_-]", " ");
                            }
                        }
                        return "链接 · " + host;
                    } catch (Exception e) {
                        return "链接";
                    }
                }
                return "链接";
            }
            case "code": {
                // Try to detect language
                if (trimmed.contains("function ") || trimmed.contains("const ") || trimmed.contains("=>"))
                    return "代码片段 · JavaScript";
                if (trimmed.contains("def ") || trimmed.contains("import "))
                    return "代码片段 · Python";
                if (trimmed.contains("public ") || trimmed.contains("class "))
                    return "代码片段 · Java";
                return "代码片段";
            }
            default: {
                // First line or first 30 chars
                String firstLine = trimmed.split("\\n")[0].trim();
                if (firstLine.length() <= 40) return firstLine;
                return firstLine.substring(0, 37) + "…";
            }
        }
    }
}
