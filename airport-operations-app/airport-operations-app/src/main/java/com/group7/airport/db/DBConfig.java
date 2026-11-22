package com.group7.airport.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads database settings.
 * Order: -Ddb.config=path (or ./db.properties)  ->  db.properties on the classpath
 *        ->  built-in defaults (root, empty password).
 */
public final class DBConfig {

    private static final String DEFAULT_URL =
            "jdbc:mysql://localhost:3306/airport_operations"
            + "?useSSL=false&allowPublicKeyRetrieval=true"
            + "&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";

    private final String url;
    private final String user;
    private final String password;
    private final String source;

    private DBConfig(String url, String user, String password, String source) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.source = source;
    }

    public static DBConfig load() {
        Properties props = new Properties();
        String source = "built-in defaults";

        Path file = Path.of(System.getProperty("db.config", "db.properties"));
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(r);
                source = file.toAbsolutePath().toString();
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + file + ": " + e.getMessage(), e);
            }
        } else {
            try (InputStream in = DBConfig.class.getResourceAsStream("/db.properties")) {
                if (in != null) {
                    props.load(in);
                    source = "classpath:/db.properties";
                }
            } catch (IOException ignored) {
                // fall back to defaults
            }
        }

        return new DBConfig(
                props.getProperty("db.url", DEFAULT_URL).trim(),
                props.getProperty("db.user", "root").trim(),
                props.getProperty("db.password", ""),
                source);
    }

    public String url()      { return url; }
    public String user()     { return user; }
    public String password() { return password; }
    public String source()   { return source; }

    /** URL without query parameters, for display in the status bar. */
    public String shortUrl() {
        int q = url.indexOf('?');
        return q < 0 ? url : url.substring(0, q);
    }
}
