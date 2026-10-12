package pl.hobiarydb.jdbc;

import pl.hobiarydb.jdbc.tcp.HobiaryTcpSocket;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC Driver for HobiaryDB.
 *
 * URL format: jdbc:hobiarydb://host:port
 * Example:    jdbc:hobiarydb://localhost:5566
 *
 * Optional properties:
 *   password — server password (leave empty for open mode)
 *   user     — username sent during auth handshake (optional)
 *   ssl      — enable TLS for open-mode connections (password connections always use TLS)
 *
 * Registration:
 *   Automatic via java.sql.DriverManager (META-INF/services/java.sql.Driver).
 *   Manual: Class.forName("pl.hobiarydb.jdbc.HobiaryDriver");
 */
public class HobiaryDriver implements Driver {

    public static final String URL_PREFIX = HobiaryTcpSocket.URL_PREFIX;

    static {
        try {
            DriverManager.registerDriver(new HobiaryDriver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url))
            return null;

        String[] hostPort = HobiaryTcpSocket.parseHostPort(url);
        String host = hostPort[0];
        int port;
        try {
            port = Integer.parseInt(hostPort[1]);
        } catch (NumberFormatException e) {
            throw new SQLException("Invalid port in HobiaryDB JDBC URL: " + url);
        }

        String password = info != null ? info.getProperty("password") : null;
        String user     = info != null ? info.getProperty("user", "") : "";
        boolean useTls  = password != null && !password.isEmpty() ||
            info != null && Boolean.parseBoolean(info.getProperty("ssl", "false"));

        HobiaryTcpSocket socket = new HobiaryTcpSocket(host, port, user, password, useTls);
        return new HobiaryConnection(socket, url);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return url != null && url.startsWith(URL_PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        DriverPropertyInfo password = new DriverPropertyInfo("password",
                info == null ? null : info.getProperty("password"));
        password.description = "Server password (leave empty for open mode)";
        password.required = false;

        DriverPropertyInfo user = new DriverPropertyInfo("user",
                info == null ? "" : info.getProperty("user", ""));
        user.description = "Username for authentication handshake";
        user.required = false;

        DriverPropertyInfo ssl = new DriverPropertyInfo("ssl",
            info == null ? "false" : info.getProperty("ssl", "false"));
        ssl.description = "Use TLS (always enabled when password is provided)";
        ssl.required = false;

        return new DriverPropertyInfo[]{ password, user, ssl };
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 2;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }
}
