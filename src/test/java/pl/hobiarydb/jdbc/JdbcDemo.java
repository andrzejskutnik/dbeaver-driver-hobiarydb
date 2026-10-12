package pl.hobiarydb.jdbc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Loopback protocol fixture, not a database engine or SQL evaluator.
 */
public final class JdbcDemo {
    private static final String SELECT = "SELECT id, name, note FROM demo_people";
    private static final String INVALID = "SELECT * FROM missing_table";
    private static final String ERROR = "Demo fixture: missing_table does not exist";

    private JdbcDemo() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("This self-contained demo accepts no arguments.");
        }
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
             var executor = Executors.newSingleThreadExecutor()) {
            server.setSoTimeout(10_000);
            var fixture = executor.submit(() -> {
                serve(server);
                return null;
            });
            var url = "jdbc:hobiarydb://127.0.0.1:" + server.getLocalPort();
            System.out.println("Loopback protocol fixture (no native database): " + url);
            try (var connection = DriverManager.getConnection(url);
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery(SELECT)) {
                var metadata = rows.getMetaData();
                require(metadata.getColumnCount() == 3, "Expected three columns");
                require("name".equals(metadata.getColumnName(2)), "Column metadata mismatch");
                require(rows.next(), "Missing first row");
                require(rows.getInt("id") == 42 && "Ada".equals(rows.getString("name")), "First row mismatch");
                require(rows.getString("note") == null && rows.wasNull(), "SQL NULL mismatch");
                System.out.println("42 | Ada | NULL");
                require(rows.next(), "Missing second row");
                require(rows.getInt("id") == 43 && "Grace".equals(rows.getString("name")), "Second row mismatch");
                require("compiler".equals(rows.getString("note")), "Second note mismatch");
                System.out.println("43 | Grace | compiler");
                require(!rows.next(), "Unexpected extra row");
                boolean rejected = false;
                try {
                    statement.executeQuery(INVALID);
                } catch (SQLException error) {
                    require(ERROR.equals(error.getMessage()), "Unexpected server error");
                    rejected = true;
                    System.out.println("Server error propagated as SQLException.");
                }
                require(rejected, "Expected server error");
            }
            fixture.get(10, TimeUnit.SECONDS);
            System.out.println("PASS: JDBC auto-loading, TCP framing, metadata, rows, NULL and server error verified.");
        }
    }

    private static void serve(ServerSocket server) throws IOException {
        try (var socket = server.accept();
             var input = new DataInputStream(socket.getInputStream());
             var output = new DataOutputStream(socket.getOutputStream())) {
            socket.setSoTimeout(10_000);
            var name = "DemoFixture".getBytes(StandardCharsets.UTF_8);
            output.writeByte(1);
            output.writeByte(name.length);
            output.write(name);
            output.writeByte(0);
            output.flush();
            requireQuery(input, SELECT);
            output.writeByte(0);
            output.writeShort(3);
            output.writeInt(2);
            output.writeInt(0);
            for (var column : new String[]{"id", "name", "note"}) {
                var bytes = column.getBytes(StandardCharsets.UTF_8);
                output.writeShort(bytes.length);
                output.write(bytes);
            }
            for (var row : new String[][]{{"42", "Ada", null}, {"43", "Grace", "compiler"}}) {
                for (var cell : row) {
                    if (cell == null) {
                        output.writeInt(-1);
                    } else {
                        writeText(output, cell);
                    }
                }
            }
            output.flush();
            requireQuery(input, INVALID);
            output.writeByte(1);
            writeText(output, ERROR);
            output.flush();
        }
    }

    private static void requireQuery(DataInputStream input, String expected) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 65_536) {
            throw new IOException("Invalid demo query length: " + length);
        }
        var bytes = input.readNBytes(length);
        if (bytes.length != length || !expected.equals(new String(bytes, StandardCharsets.UTF_8))) {
            throw new IOException("Unexpected query sent by JDBC driver");
        }
    }

    private static void writeText(DataOutputStream output, String value) throws IOException {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
