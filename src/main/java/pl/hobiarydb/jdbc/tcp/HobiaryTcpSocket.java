package pl.hobiarydb.jdbc.tcp;

import java.io.*;
import java.net.Socket;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;

/**
 * Low-level TCP connection to HobiaryDB server.
 *
 * Binary big-endian protocol:
 *   Handshake  Server→Client: [u8 version][u8 nameLen][bytes name][u8 authRequired]
 *              if authRequired==1: [u8[16] nonce]
 *   Auth       Client→Server: [u8 usernameLen][bytes username][u8[32] token]
 *              token = SHA-256(nonce[16] || SHA-256(UTF8(password))[32])
 *              Server→Client: [u8 result]  0=OK 1=denied
 *   Query      Client→Server: [u32be sqlLen][bytes sql]
 *   Response   OK:    [u8=0][u16be colCount][u32be rowCount][u32be affected]
 *                     for each col: [u16be nameLen][bytes name]
 *                     for each row x col: [u32be valLen (0xFFFFFFFF=NULL)][bytes val]
 *              Error: [u8=1][u32be msgLen][bytes message]
 *
 * URL format: jdbc:hobiarydb://host:port  (default port: 5566)
 */
public class HobiaryTcpSocket implements Closeable {

    public static final String URL_PREFIX = "jdbc:hobiarydb://";
    public static final int    DEFAULT_PORT = 5566;

    private final Socket           socket;
    private final DataInputStream  in;
    private final DataOutputStream out;

    public HobiaryTcpSocket(String host, int port, String username, String password)
            throws SQLException {
        this(host, port, username, password, password != null && !password.isEmpty());
    }

    public HobiaryTcpSocket(String host, int port, String username, String password, boolean useTls)
            throws SQLException {
        try {
            if (password != null && !password.isEmpty() && !useTls) {
                throw new SQLException("Password authentication requires TLS.");
            }
            if (useTls) {
                SSLSocket sslSocket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port);
                SSLParameters sslParameters = sslSocket.getSSLParameters();
                sslParameters.setEndpointIdentificationAlgorithm("HTTPS");
                sslSocket.setSSLParameters(sslParameters);
                sslSocket.startHandshake();
                socket = sslSocket;
            } else {
                socket = new Socket(host, port);
            }
            socket.setTcpNoDelay(true);
            in  = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            performHandshake(username != null ? username : "", password);
        } catch (IOException e) {
            throw new SQLException("Cannot connect to HobiaryDB at " + host + ":" + port
                    + " — " + e.getMessage(), e);
        } catch (ClassCastException e) {
            throw new SQLException("TLS socket provider is not an SSLSocket provider.", e);
        }
    }

    // ── Handshake ─────────────────────────────────────────────────────────────

    private void performHandshake(String username, String password)
            throws IOException, SQLException {
        // version (1B)
        in.readUnsignedByte();

        // server name
        int nameLen = in.readUnsignedByte();
        readExact(nameLen);

        // auth_required
        int authRequired = in.readUnsignedByte();

        if (authRequired == 0) {
            return;
        }

        if (password == null || password.isEmpty()) {
            throw new SQLException("Server requires authentication but no password was provided.");
        }

        // nonce (16B)
        byte[] nonce = readExact(16);

        // token = SHA-256(nonce[16] || SHA-256(UTF8(password))[32])
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] pwdHash = sha.digest(password.getBytes(StandardCharsets.UTF_8));
            sha.reset();
            sha.update(nonce);
            sha.update(pwdHash);
            byte[] token = sha.digest();

            // [u8 usernameLen][bytes username][u8[32] token]
            byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
            out.writeByte(Math.min(usernameBytes.length, 255));
            if (usernameBytes.length > 0) {
                out.write(usernameBytes, 0, Math.min(usernameBytes.length, 255));
            }
            out.write(token);
            out.flush();

            int result = in.readUnsignedByte();
            if (result != 0) {
                throw new SQLException("Authentication failed: invalid password.");
            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Sends a SQL statement and returns the server's response.
     * Synchronized — one in-flight request per connection.
     */
    public synchronized QueryResult execute(String sql) throws SQLException {
        if (socket.isClosed()) {
            throw new SQLException("Connection is closed.");
        }
        try {
            byte[] sqlBytes = sql.getBytes(StandardCharsets.UTF_8);
            out.writeInt(sqlBytes.length);   // uint32be
            out.write(sqlBytes);
            out.flush();
            return readResponse();
        } catch (IOException e) {
            throw new SQLException("Communication error: " + e.getMessage(), e);
        }
    }

    private QueryResult readResponse() throws IOException, SQLException {
        int status = in.readUnsignedByte();

        if (status != 0) {
            int msgLen     = in.readInt(); // uint32be (signed read OK — max 64 KB)
            byte[] msgBytes = readExact(msgLen);
            throw new SQLException(new String(msgBytes, StandardCharsets.UTF_8));
        }

        int colCount = in.readUnsignedShort();              // uint16be
        long rowCountL = Integer.toUnsignedLong(in.readInt()); // uint32be
        int affected  = in.readInt();                       // uint32be (treated as signed)
        int rowCount  = (int) rowCountL;

        String[] columns = new String[colCount];
        for (int i = 0; i < colCount; i++) {
            int len = in.readUnsignedShort();
            columns[i] = new String(readExact(len), StandardCharsets.UTF_8);
        }

        String[][] rows = new String[rowCount][];
        for (int r = 0; r < rowCount; r++) {
            rows[r] = new String[colCount];
            for (int c = 0; c < colCount; c++) {
                long valLen = Integer.toUnsignedLong(in.readInt()); // uint32be
                if (valLen == 0xFFFFFFFFL) {
                    rows[r][c] = null;
                } else {
                    rows[r][c] = new String(readExact((int) valLen), StandardCharsets.UTF_8);
                }
            }
        }

        return new QueryResult(columns, rows, affected);
    }

    // ── State ─────────────────────────────────────────────────────────────────

    /** Reads exactly {@code n} bytes. Compatible with Java 8+ (replaces readNBytes). */
    private byte[] readExact(int n) throws IOException {
        if (n == 0) return new byte[0];
        byte[] buf = new byte[n];
        int read = 0;
        while (read < n) {
            int r = in.read(buf, read, n - read);
            if (r < 0) throw new java.io.EOFException("Unexpected end of stream after " + read + "/" + n + " bytes");
            read += r;
        }
        return buf;
    }

    public boolean isClosed() {
        return socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    // ── URL parsing ───────────────────────────────────────────────────────────

    /**
     * Parses host and port from a {@code jdbc:hobiarydb://host:port} URL.
     *
     * @return {@code String[]{host, port}} — port defaults to "5566" if omitted
     */
    public static String[] parseHostPort(String url) throws SQLException {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            throw new SQLException("Invalid HobiaryDB JDBC URL: " + url);
        }
        String authority = url.substring(URL_PREFIX.length());

        // Strip optional path component (e.g. jdbc:hobiarydb://localhost:5566/)
        int slash = authority.indexOf('/');
        if (slash >= 0) {
            authority = authority.substring(0, slash);
        }
        if (authority.isEmpty()) {
            throw new SQLException("Host is missing in HobiaryDB JDBC URL: " + url);
        }

        int colon = authority.lastIndexOf(':');
        if (colon < 0) {
            return new String[]{ authority, String.valueOf(DEFAULT_PORT) };
        }
        return new String[]{ authority.substring(0, colon), authority.substring(colon + 1) };
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * Result of a SQL execution returned by the server.
     *
     * @param columns column names (empty array for DML)
     * @param rows    string values; null element = SQL NULL
     * @param affected rows affected by DML (0 for SELECT)
     */
    public static final class QueryResult {
        private final String[]   columns;
        private final String[][] rows;
        private final int        affected;

        public QueryResult(String[] columns, String[][] rows, int affected) {
            this.columns  = columns;
            this.rows     = rows;
            this.affected = affected;
        }

        public String[]   columns()  { return columns; }
        public String[][] rows()     { return rows; }
        public int        affected() { return affected; }

        public boolean hasResultSet() {
            return columns.length > 0;
        }
    }
}
