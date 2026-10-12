package pl.hobiarydb.jdbc.tcp;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class ParseHostPortTest {

    @Test
    void hostAndExplicitPort() throws SQLException {
        String[] hp = HobiaryTcpSocket.parseHostPort("jdbc:hobiarydb://localhost:5566");
        assertEquals("localhost", hp[0]);
        assertEquals("5566", hp[1]);
    }

    @Test
    void hostOnlyUsesDefaultPort() throws SQLException {
        String[] hp = HobiaryTcpSocket.parseHostPort("jdbc:hobiarydb://myserver");
        assertEquals("myserver", hp[0]);
        assertEquals(String.valueOf(HobiaryTcpSocket.DEFAULT_PORT), hp[1]);
    }

    @Test
    void trailingSlashIsStripped() throws SQLException {
        String[] hp = HobiaryTcpSocket.parseHostPort("jdbc:hobiarydb://localhost:1234/");
        assertEquals("localhost", hp[0]);
        assertEquals("1234", hp[1]);
    }

    @Test
    void ipv4Address() throws SQLException {
        String[] hp = HobiaryTcpSocket.parseHostPort("jdbc:hobiarydb://192.168.1.100:9000");
        assertEquals("192.168.1.100", hp[0]);
        assertEquals("9000", hp[1]);
    }

    @Test
    void nullUrlThrows() {
        assertThrows(SQLException.class, () -> HobiaryTcpSocket.parseHostPort(null));
    }

    @Test
    void wrongPrefixThrows() {
        assertThrows(SQLException.class, () -> HobiaryTcpSocket.parseHostPort("jdbc:postgresql://localhost"));
    }

    @Test
    void emptyHostThrows() {
        assertThrows(SQLException.class, () -> HobiaryTcpSocket.parseHostPort("jdbc:hobiarydb://"));
    }
}
