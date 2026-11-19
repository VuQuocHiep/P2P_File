package vn.edu.p2p.server;

/**
 * MySQL connection shared by the server tests. Every test class uses its own database, which the
 * driver creates on first use, so tests stay isolated without an embedded engine.
 *
 * <p>Point the tests at a local server with {@code DB_HOST}, {@code DB_PORT}, {@code DB_USERNAME}
 * and {@code DB_PASSWORD}; the defaults match {@code application.properties}.
 */
public final class TestDatabase {
    private TestDatabase() {}

    public static String url(String name) {
        return "jdbc:mysql://"
                + host()
                + ":"
                + port()
                + "/"
                + name
                + "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";
    }

    /** Server-level URL without a database, for creating or dropping test schemas. */
    public static String serverUrl() {
        return "jdbc:mysql://"
                + host()
                + ":"
                + port()
                + "/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh";
    }

    private static String host() {
        return value("DB_HOST", "localhost");
    }

    private static String port() {
        return value("DB_PORT", "3306");
    }

    public static String username() {
        return value("DB_USERNAME", "root");
    }

    public static String password() {
        return value("DB_PASSWORD", "");
    }

    private static String value(String key, String fallback) {
        String configured = System.getenv(key);
        return configured == null || configured.isBlank() ? fallback : configured;
    }
}
