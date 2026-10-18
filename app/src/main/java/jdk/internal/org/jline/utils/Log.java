package jdk.internal.org.jline.utils;

/**
 * Log stub for rtklib Java port.
 * 原 JAR 使用 jline 的 Log 类，此处提供兼容存根。
 */
public class Log {
    public static void trace(Object[] args) {
        StringBuilder sb = new StringBuilder();
        if (args != null && args.length >= 2 && args[0] instanceof Integer && args[1] instanceof String) {
            String fmt = (String) args[1];
            int idx = 2;
            for (int i = 0; i < fmt.length(); i++) {
                if (fmt.charAt(i) == '%' && i + 1 < fmt.length() && idx < args.length) {
                    sb.append(args[idx++]);
                } else if (fmt.charAt(i) != '%') {
                    sb.append(fmt.charAt(i));
                }
            }
        }
        System.out.print(sb.toString());
    }

    public static void debug(Object[] args) {
        trace(args);
    }
}
