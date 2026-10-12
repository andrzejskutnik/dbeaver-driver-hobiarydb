package pl.hobiarydb.jdbc;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import static org.junit.jupiter.api.Assertions.*;

class HobiaryResultSetTest {

    // Helper: builds a ResultSet with given columns and rows
    private static HobiaryResultSet rs(String[] cols, String[][] rows) {
        return new HobiaryResultSet(cols, rows, null);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Test
    void nextAdvancesCursor() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}, {"2"}, {"3"}});
        assertTrue(r.next());
        assertEquals(1, r.getRow());
        assertTrue(r.next());
        assertEquals(2, r.getRow());
        assertTrue(r.next());
        assertEquals(3, r.getRow());
        assertFalse(r.next());
    }

    @Test
    void emptyResultSetNextReturnsFalse() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[0][]);
        assertFalse(r.next());
    }

    @Test
    void isBeforeFirstAfterConstruction() {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}});
        assertTrue(r.isBeforeFirst());
        assertFalse(r.isFirst());
    }

    @Test
    void isFirstAfterOneNext() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}, {"2"}});
        r.next();
        assertTrue(r.isFirst());
        assertFalse(r.isLast());
    }

    @Test
    void isLastOnLastRow() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}, {"2"}});
        r.next(); r.next();
        assertTrue(r.isLast());
    }

    @Test
    void beforeFirstResetsToStart() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}, {"2"}});
        r.next(); r.next();
        r.beforeFirst();
        assertTrue(r.isBeforeFirst());
        assertTrue(r.next());
        assertEquals("1", r.getString(1));
    }

    @Test
    void firstJumpsToRow1() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"a"}, {"b"}});
        r.next(); r.next();
        assertTrue(r.first());
        assertEquals("a", r.getString(1));
    }

    @Test
    void lastJumpsToFinalRow() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"a"}, {"b"}, {"c"}});
        assertTrue(r.last());
        assertEquals("c", r.getString(1));
    }

    // ── Typed getters ─────────────────────────────────────────────────────────

    @Test
    void getStringByIndex() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"a", "b"}, new String[][]{{"hello", "world"}});
        r.next();
        assertEquals("hello", r.getString(1));
        assertEquals("world", r.getString(2));
    }

    @Test
    void getStringByLabel() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"Name"}, new String[][]{{"Alice"}});
        r.next();
        assertEquals("Alice", r.getString("Name"));
        assertEquals("Alice", r.getString("name")); // case-insensitive
    }

    @Test
    void getIntParsesString() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"n"}, new String[][]{{"42"}});
        r.next();
        assertEquals(42, r.getInt(1));
    }

    @Test
    void getLongParsesString() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"n"}, new String[][]{{"9876543210"}});
        r.next();
        assertEquals(9876543210L, r.getLong(1));
    }

    @Test
    void getDoubleParsesString() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"v"}, new String[][]{{"3.14"}});
        r.next();
        assertEquals(3.14, r.getDouble(1), 1e-9);
    }

    @Test
    void getBooleanTrueOnNonZeroNonFalse() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"b"}, new String[][]{{"1"}, {"true"}, {"yes"}, {"0"}, {"false"}});
        r.next(); assertTrue(r.getBoolean(1));
        r.next(); assertTrue(r.getBoolean(1));
        r.next(); assertTrue(r.getBoolean(1));
        r.next(); assertFalse(r.getBoolean(1));
        r.next(); assertFalse(r.getBoolean(1));
    }

    @Test
    void getBigDecimal() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"v"}, new String[][]{{"123.456"}});
        r.next();
        assertEquals(new BigDecimal("123.456"), r.getBigDecimal(1));
    }

    @Test
    void getBytesUtf8() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"v"}, new String[][]{{"hi"}});
        r.next();
        assertArrayEquals("hi".getBytes(java.nio.charset.StandardCharsets.UTF_8), r.getBytes(1));
    }

    @Test
    void getObjectReturnsString() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"v"}, new String[][]{{"test"}});
        r.next();
        assertEquals("test", r.getObject(1));
    }

    // ── NULL handling ─────────────────────────────────────────────────────────

    @Test
    void nullValueSetsWasNull() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"v"}, new String[][]{{null}});
        r.next();
        assertNull(r.getString(1));
        assertTrue(r.wasNull());
    }

    @Test
    void nullIntReturnsZero() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"v"}, new String[][]{{null}});
        r.next();
        assertEquals(0, r.getInt(1));
        assertTrue(r.wasNull());
    }

    @Test
    void nullBytesReturnsNull() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"v"}, new String[][]{{null}});
        r.next();
        assertNull(r.getBytes(1));
    }

    @Test
    void wasNullFalseForNonNull() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"v"}, new String[][]{{"x"}});
        r.next();
        r.getString(1);
        assertFalse(r.wasNull());
    }

    // ── Column search ─────────────────────────────────────────────────────────

    @Test
    void findColumnCaseInsensitive() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"Id", "Name"}, new String[][]{{"1", "A"}});
        assertEquals(1, r.findColumn("id"));
        assertEquals(1, r.findColumn("ID"));
        assertEquals(2, r.findColumn("name"));
    }

    @Test
    void findColumnNotFoundThrows() {
        HobiaryResultSet r = rs(new String[]{"id"}, new String[][]{{"1"}});
        assertThrows(SQLException.class, () -> r.findColumn("missing"));
    }

    // ── Out-of-range column index ─────────────────────────────────────────────

    @Test
    void columnIndexZeroThrows() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}});
        r.next();
        assertThrows(SQLException.class, () -> r.getString(0));
    }

    @Test
    void columnIndexTooLargeThrows() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}});
        r.next();
        assertThrows(SQLException.class, () -> r.getString(2));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Test
    void closeSetsClosed() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}});
        assertFalse(r.isClosed());
        r.close();
        assertTrue(r.isClosed());
    }

    @Test
    void operationOnClosedResultSetThrows() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}});
        r.close();
        assertThrows(SQLException.class, r::next);
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    @Test
    void getMetaDataReturnsCorrectColumns() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"a", "b"}, new String[][]{});
        ResultSetMetaData m = r.getMetaData();
        assertEquals(2, m.getColumnCount());
        assertEquals("a", m.getColumnName(1));
        assertEquals("b", m.getColumnName(2));
    }

    // ── Cursor attributes ─────────────────────────────────────────────────────

    @Test
    void typeIsForwardOnly() {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[0][]);
        assertEquals(java.sql.ResultSet.TYPE_FORWARD_ONLY, r.getType());
    }

    @Test
    void concurrencyIsReadOnly() {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[0][]);
        assertEquals(java.sql.ResultSet.CONCUR_READ_ONLY, r.getConcurrency());
    }

    // ── Unsupported operations ────────────────────────────────────────────────

    @Test
    void updateThrowsFeatureNotSupported() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}});
        r.next();
        assertThrows(SQLFeatureNotSupportedException.class, () -> r.updateString(1, "x"));
    }

    @Test
    void getTimestampThrowsFeatureNotSupported() throws SQLException {
        HobiaryResultSet r = rs(new String[]{"x"}, new String[][]{{"1"}});
        r.next();
        assertThrows(SQLFeatureNotSupportedException.class, () -> r.getTimestamp(1));
    }
}
