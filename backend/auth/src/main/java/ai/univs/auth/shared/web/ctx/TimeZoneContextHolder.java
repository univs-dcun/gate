package ai.univs.auth.shared.web.ctx;

public class TimeZoneContextHolder {

    private static final ThreadLocal<String> context = new ThreadLocal<>();

    public static void set(String timezone) {
        context.set(timezone);
    }

    public static String get() {
        return context.get();
    }

    public static void clear() {
        context.remove();
    }
}
