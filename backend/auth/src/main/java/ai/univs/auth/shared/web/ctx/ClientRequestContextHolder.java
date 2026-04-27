package ai.univs.auth.shared.web.ctx;

public class ClientRequestContextHolder {

    private record ClientRequestContext(String ipAddress, String userAgent) {}

    private static final ThreadLocal<ClientRequestContext> context = new ThreadLocal<>();

    public static void set(String ipAddress, String userAgent) {
        context.set(new ClientRequestContext(ipAddress, userAgent));
    }

    public static String getIpAddress() {
        ClientRequestContext ctx = context.get();
        return ctx != null ? ctx.ipAddress() : null;
    }

    public static String getUserAgent() {
        ClientRequestContext ctx = context.get();
        return ctx != null ? ctx.userAgent() : null;
    }

    public static void clear() {
        context.remove();
    }
}
