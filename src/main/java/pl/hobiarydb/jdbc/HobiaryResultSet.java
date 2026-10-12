package pl.hobiarydb.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Calendar;
import java.util.Map;

/**
 * Forward-only, read-only ResultSet backed by string columns + rows from TCP response.
 * All values are stored as Strings; numeric/boolean conversions are done on access.
 */
public class HobiaryResultSet implements ResultSet {

    private final String[]   columns;
    private final String[][] rows;
    private final Statement  ownerStatement;

    private int        cursor    = -1;
    private boolean    closed    = false;
    private boolean    wasNull   = false;
    private SQLWarning warnings  = null;

    HobiaryResultSet(String[] columns, String[][] rows, Statement ownerStatement) {
        this.columns       = columns;
        this.rows          = rows;
        this.ownerStatement = ownerStatement;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Override public boolean next() throws SQLException { checkOpen(); if (cursor + 1 < rows.length) { cursor++; return true; } return false; }
    @Override public boolean isBeforeFirst() { return cursor < 0 && rows.length > 0; }
    @Override public boolean isAfterLast()   { return cursor >= rows.length; }
    @Override public boolean isFirst()       { return cursor == 0; }
    @Override public boolean isLast()        { return rows.length > 0 && cursor == rows.length - 1; }
    @Override public int getRow()            { return cursor + 1; }
    @Override public void beforeFirst() throws SQLException { checkOpen(); cursor = -1; }
    @Override public void afterLast()   throws SQLException { checkOpen(); cursor = rows.length; }
    @Override public boolean first() throws SQLException { checkOpen(); if (rows.length == 0) return false; cursor = 0; return true; }
    @Override public boolean last()  throws SQLException { checkOpen(); if (rows.length == 0) return false; cursor = rows.length - 1; return true; }
    @Override public boolean absolute(int row) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean relative(int rows) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean previous() throws SQLException { throw new SQLFeatureNotSupportedException(); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override public void close()        { closed = true; }
    @Override public boolean isClosed()  { return closed; }
    @Override public Statement getStatement() { return ownerStatement; }
    @Override public SQLWarning getWarnings() { return warnings; }
    @Override public void clearWarnings()     { warnings = null; }
    @Override public String getCursorName() throws SQLException { throw new SQLFeatureNotSupportedException(); }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Override
    public ResultSetMetaData getMetaData() {
        return new HobiaryResultSetMetaData(columns);
    }

    // ── Column helpers ────────────────────────────────────────────────────────

    private void checkOpen() throws SQLException {
        if (closed) throw new SQLException("ResultSet is closed");
    }

    private void checkRow() throws SQLException {
        if (cursor < 0 || cursor >= rows.length)
            throw new SQLException("No current row");
    }

    private int columnIndex(String label) throws SQLException {
        for (int i = 0; i < columns.length; i++) {
            if (label.equalsIgnoreCase(columns[i])) return i + 1;
        }
        throw new SQLException("Column not found: " + label);
    }

    private String rawValue(int columnIndex) throws SQLException {
        checkOpen();
        checkRow();
        if (columnIndex < 1 || columnIndex > columns.length)
            throw new SQLException("Column index out of range: " + columnIndex);
        String v = rows[cursor][columnIndex - 1];
        wasNull = (v == null);
        return v;
    }

    // ── Typed getters by index ────────────────────────────────────────────────

    @Override public String getString(int i) throws SQLException { return rawValue(i); }
    @Override public boolean getBoolean(int i) throws SQLException { String v = rawValue(i); return !wasNull && !v.equals("0") && !v.equalsIgnoreCase("false"); }
    @Override public byte    getByte(int i)    throws SQLException { String v = rawValue(i); return wasNull ? 0 : Byte.parseByte(v); }
    @Override public short   getShort(int i)   throws SQLException { String v = rawValue(i); return wasNull ? 0 : Short.parseShort(v); }
    @Override public int     getInt(int i)     throws SQLException { String v = rawValue(i); return wasNull ? 0 : Integer.parseInt(v); }
    @Override public long    getLong(int i)    throws SQLException { String v = rawValue(i); return wasNull ? 0L : Long.parseLong(v); }
    @Override public float   getFloat(int i)   throws SQLException { String v = rawValue(i); return wasNull ? 0f : Float.parseFloat(v); }
    @Override public double  getDouble(int i)  throws SQLException { String v = rawValue(i); return wasNull ? 0d : Double.parseDouble(v); }

    @Override
    public byte[] getBytes(int i) throws SQLException {
        String v = rawValue(i);
        return wasNull ? null : v.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Object getObject(int i) throws SQLException {
        return rawValue(i);
    }

    @Override
    public BigDecimal getBigDecimal(int i) throws SQLException {
        String v = rawValue(i);
        return wasNull ? null : new BigDecimal(v);
    }

    @Override
    public BigDecimal getBigDecimal(int i, int scale) throws SQLException {
        BigDecimal bd = getBigDecimal(i);
        return bd == null ? null : bd.setScale(scale, java.math.RoundingMode.HALF_UP);
    }

    // ── Typed getters by label ────────────────────────────────────────────────

    @Override public String getString(String col)     throws SQLException { return getString(columnIndex(col)); }
    @Override public boolean getBoolean(String col)   throws SQLException { return getBoolean(columnIndex(col)); }
    @Override public byte    getByte(String col)      throws SQLException { return getByte(columnIndex(col)); }
    @Override public short   getShort(String col)     throws SQLException { return getShort(columnIndex(col)); }
    @Override public int     getInt(String col)       throws SQLException { return getInt(columnIndex(col)); }
    @Override public long    getLong(String col)      throws SQLException { return getLong(columnIndex(col)); }
    @Override public float   getFloat(String col)     throws SQLException { return getFloat(columnIndex(col)); }
    @Override public double  getDouble(String col)    throws SQLException { return getDouble(columnIndex(col)); }
    @Override public byte[]  getBytes(String col)     throws SQLException { return getBytes(columnIndex(col)); }
    @Override public Object  getObject(String col)    throws SQLException { return getObject(columnIndex(col)); }
    @Override public BigDecimal getBigDecimal(String col) throws SQLException { return getBigDecimal(columnIndex(col)); }
    @Override public BigDecimal getBigDecimal(String col, int scale) throws SQLException { return getBigDecimal(columnIndex(col), scale); }

    @Override public boolean wasNull() { return wasNull; }

    // ── Column search ─────────────────────────────────────────────────────────

    @Override
    public int findColumn(String columnLabel) throws SQLException {
        return columnIndex(columnLabel);
    }

    // ── Not supported ─────────────────────────────────────────────────────────

    @Override public InputStream getAsciiStream(int i)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getUnicodeStream(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getBinaryStream(int i)  throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getAsciiStream(String c)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getUnicodeStream(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getBinaryStream(String c)  throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Date     getDate(int i)      throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Time     getTime(int i)      throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Timestamp getTimestamp(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Date     getDate(String c)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Time     getTime(String c)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Timestamp getTimestamp(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Date     getDate(int i, Calendar cal)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Time     getTime(int i, Calendar cal)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Timestamp getTimestamp(int i, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Date     getDate(String c, Calendar cal)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Time     getTime(String c, Calendar cal)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Timestamp getTimestamp(String c, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Object   getObject(int i, Map<String, Class<?>> m) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Ref      getRef(int i)  throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Blob     getBlob(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Clob     getClob(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Array    getArray(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Object   getObject(String c, Map<String, Class<?>> m) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Ref      getRef(String c)  throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Blob     getBlob(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Clob     getClob(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Array    getArray(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public URL      getURL(int i)  throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public URL      getURL(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public NClob    getNClob(int i)  throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public NClob    getNClob(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public SQLXML   getSQLXML(int i)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public SQLXML   getSQLXML(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public String   getNString(int i)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public String   getNString(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Reader   getNCharacterStream(int i)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Reader   getNCharacterStream(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Reader   getCharacterStream(int i)   throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Reader   getCharacterStream(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T    getObject(int i, Class<T> type) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T    getObject(String c, Class<T> type) throws SQLException { throw new SQLFeatureNotSupportedException(); }

    // ── Cursor / fetch / holdability ──────────────────────────────────────────

    @Override public int getFetchDirection() { return FETCH_FORWARD; }
    @Override public void setFetchDirection(int d) { }
    @Override public int getFetchSize() { return 0; }
    @Override public void setFetchSize(int r) { }
    @Override public int getType() { return TYPE_FORWARD_ONLY; }
    @Override public int getConcurrency() { return CONCUR_READ_ONLY; }
    @Override public int getHoldability() { return CLOSE_CURSORS_AT_COMMIT; }

    // ── Updates (not supported) ───────────────────────────────────────────────

    @Override public boolean rowUpdated()  throws SQLException { return false; }
    @Override public boolean rowInserted() throws SQLException { return false; }
    @Override public boolean rowDeleted()  throws SQLException { return false; }
    @Override public void updateNull(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBoolean(int i, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateByte(int i, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateShort(int i, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateInt(int i, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateLong(int i, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateFloat(int i, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDouble(int i, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBigDecimal(int i, BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateString(int i, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBytes(int i, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDate(int i, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTime(int i, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTimestamp(int i, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int i, InputStream x, int len) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int i, InputStream x, int len) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int i, Reader x, int len) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(int i, Object x, int scale) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(int i, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNull(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBoolean(String c, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateByte(String c, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateShort(String c, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateInt(String c, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateLong(String c, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateFloat(String c, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDouble(String c, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBigDecimal(String c, BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateString(String c, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBytes(String c, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDate(String c, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTime(String c, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTimestamp(String c, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String c, InputStream x, int len) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String c, InputStream x, int len) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String c, Reader x, int len) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(String c, Object x, int scale) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(String c, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void insertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void deleteRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void refreshRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void cancelRowUpdates() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void moveToInsertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void moveToCurrentRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRef(int i, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRef(String c, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int i, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String c, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int i, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String c, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateArray(int i, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateArray(String c, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRowId(int i, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRowId(String c, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public RowId getRowId(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public RowId getRowId(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNString(int i, String s) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNString(String c, String s) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int i, NClob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String c, NClob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateSQLXML(int i, SQLXML x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateSQLXML(String c, SQLXML x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(int i, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(String c, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int i, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int i, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int i, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String c, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String c, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String c, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int i, InputStream is, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String c, InputStream is, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int i, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String c, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int i, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String c, Reader r, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(int i, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(String c, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int i, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int i, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int i, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String c, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String c, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String c, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int i, InputStream is) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String c, InputStream is) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int i, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String c, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int i, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String c, Reader r) throws SQLException { throw new SQLFeatureNotSupportedException(); }

    // ── Wrapper ───────────────────────────────────────────────────────────────

    @Override public <T> T unwrap(Class<T> iface) throws SQLException { if (iface.isInstance(this)) return iface.cast(this); throw new SQLException("Not a wrapper for " + iface); }
    @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
}