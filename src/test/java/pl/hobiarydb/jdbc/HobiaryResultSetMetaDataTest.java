package pl.hobiarydb.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.*;

class HobiaryResultSetMetaDataTest {

    private static final String[] COLS = {"id", "name", "value"};

    @Test
    void columnCount() {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(COLS);
        assertEquals(3, m.getColumnCount());
    }

    @Test
    void emptyColumns() {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(new String[0]);
        assertEquals(0, m.getColumnCount());
    }

    @Test
    void nullColumnsIsTreatedAsEmpty() {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(null);
        assertEquals(0, m.getColumnCount());
    }

    @Test
    void columnNamesOneIndexed() throws SQLException {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(COLS);
        assertEquals("id",    m.getColumnName(1));
        assertEquals("name",  m.getColumnName(2));
        assertEquals("value", m.getColumnName(3));
    }

    @Test
    void columnLabelEqualsName() throws SQLException {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(COLS);
        assertEquals(m.getColumnName(1), m.getColumnLabel(1));
    }

    @Test
    void columnTypeIsVarchar() throws SQLException {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(COLS);
        assertEquals(Types.VARCHAR, m.getColumnType(1));
        assertEquals("VARCHAR", m.getColumnTypeName(1));
        assertEquals("java.lang.String", m.getColumnClassName(1));
    }

    @Test
    void columnIndexZeroThrows() {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(COLS);
        assertThrows(SQLException.class, () -> m.getColumnName(0));
    }

    @Test
    void columnIndexTooLargeThrows() {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(COLS);
        assertThrows(SQLException.class, () -> m.getColumnName(4));
    }

    @Test
    void isNullable() throws SQLException {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(COLS);
        assertEquals(ResultSetMetaData.columnNullableUnknown, m.isNullable(1));
    }

    @Test
    void isReadOnly() throws SQLException {
        HobiaryResultSetMetaData m = new HobiaryResultSetMetaData(COLS);
        assertTrue(m.isReadOnly(1));
        assertFalse(m.isWritable(1));
        assertFalse(m.isDefinitelyWritable(1));
    }
}
