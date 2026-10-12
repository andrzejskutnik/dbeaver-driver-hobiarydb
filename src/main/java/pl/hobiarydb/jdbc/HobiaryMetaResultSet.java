package pl.hobiarydb.jdbc;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * Simple in-memory, forward-only ResultSet for DatabaseMetaData responses.
 * Stores rows as Object arrays; all values returned as Strings via getString().
 */
class HobiaryMetaResultSet implements ResultSet {

    private final String[] columns;
    private final List<Object[]> rows;
    private int cursor = -1;
    private boolean closed = false;
    private boolean wasNull = false;

    HobiaryMetaResultSet(String[] columns) {
        this.columns = columns;
        this.rows = new ArrayList<>();
    }

    void addRow(Object... values) {
        rows.add(values);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Override public boolean next() throws SQLException { checkOpen(); if (cursor + 1 < rows.size()) { cursor++; return true; } return false; }
    @Override public boolean isBeforeFirst() { return cursor < 0 && !rows.isEmpty(); }
    @Override public boolean isAfterLast()   { return cursor >= rows.size(); }
    @Override public boolean isFirst()       { return cursor == 0; }
    @Override public boolean isLast()        { return cursor == rows.size() - 1; }
    @Override public int getRow()            { return cursor + 1; }
    @Override public void beforeFirst() throws SQLException { cursor = -1; }
    @Override public void afterLast() throws SQLException { cursor = rows.size(); }
    @Override public boolean first() throws SQLException { if (rows.isEmpty()) return false; cursor = 0; return true; }
    @Override public boolean last() throws SQLException { if (rows.isEmpty()) return false; cursor = rows.size() - 1; return true; }
    @Override public boolean absolute(int row) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean relative(int rows) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public boolean previous() throws SQLException { throw new SQLFeatureNotSupportedException(); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override public void close() { closed = true; }
    @Override public boolean isClosed() { return closed; }
    @Override public Statement getStatement() { return null; }
    @Override public SQLWarning getWarnings() { return null; }
    @Override public void clearWarnings() {}
    @Override public String getCursorName() throws SQLException { throw new SQLFeatureNotSupportedException(); }

    private void checkOpen() throws SQLException { if (closed) throw new SQLException("ResultSet is closed"); }
    private void checkRow()  throws SQLException { if (cursor < 0 || cursor >= rows.size()) throw new SQLException("No current row"); }

    private int findColumnIndex(String label) throws SQLException {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equalsIgnoreCase(label)) return i + 1;
        }
        throw new SQLException("Column not found: " + label);
    }

    private Object value(int columnIndex) throws SQLException {
        checkRow();
        if (columnIndex < 1 || columnIndex > columns.length)
            throw new SQLException("Column index out of range: " + columnIndex);
        Object[] row = rows.get(cursor);
        if (columnIndex > row.length) { wasNull = true; return null; }
        Object v = row[columnIndex - 1];
        wasNull = (v == null);
        return v;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    @Override public boolean wasNull() { return wasNull; }
    @Override public String getString(int i)    throws SQLException { Object v = value(i); return wasNull ? null : String.valueOf(v); }
    @Override public String getString(String c) throws SQLException { return getString(findColumnIndex(c)); }
    @Override public int getInt(int i)          throws SQLException { Object v = value(i); return wasNull ? 0 : ((Number) v).intValue(); }
    @Override public int getInt(String c)       throws SQLException { return getInt(findColumnIndex(c)); }
    @Override public long getLong(int i)        throws SQLException { Object v = value(i); return wasNull ? 0L : ((Number) v).longValue(); }
    @Override public long getLong(String c)     throws SQLException { return getLong(findColumnIndex(c)); }
    @Override public short getShort(int i)      throws SQLException { Object v = value(i); return wasNull ? 0 : ((Number) v).shortValue(); }
    @Override public short getShort(String c)   throws SQLException { return getShort(findColumnIndex(c)); }
    @Override public boolean getBoolean(int i)  throws SQLException { Object v = value(i); return !wasNull && ((Boolean) v); }
    @Override public boolean getBoolean(String c) throws SQLException { return getBoolean(findColumnIndex(c)); }
    @Override public byte getByte(int i)        throws SQLException { Object v = value(i); return wasNull ? 0 : ((Number) v).byteValue(); }
    @Override public byte getByte(String c)     throws SQLException { return getByte(findColumnIndex(c)); }
    @Override public float getFloat(int i)      throws SQLException { Object v = value(i); return wasNull ? 0f : ((Number) v).floatValue(); }
    @Override public float getFloat(String c)   throws SQLException { return getFloat(findColumnIndex(c)); }
    @Override public double getDouble(int i)    throws SQLException { Object v = value(i); return wasNull ? 0d : ((Number) v).doubleValue(); }
    @Override public double getDouble(String c) throws SQLException { return getDouble(findColumnIndex(c)); }
    @Override public byte[] getBytes(int i)     throws SQLException { Object v = value(i); return wasNull ? null : (byte[]) v; }
    @Override public byte[] getBytes(String c)  throws SQLException { return getBytes(findColumnIndex(c)); }
    @Override public Object getObject(int i)    throws SQLException { return value(i); }
    @Override public Object getObject(String c) throws SQLException { return getObject(findColumnIndex(c)); }
    @Override public BigDecimal getBigDecimal(int i) throws SQLException { Object v = value(i); return wasNull ? null : new BigDecimal(String.valueOf(v)); }
    @Override public BigDecimal getBigDecimal(String c) throws SQLException { return getBigDecimal(findColumnIndex(c)); }
    @Override public BigDecimal getBigDecimal(int i, int scale) throws SQLException { BigDecimal bd = getBigDecimal(i); return bd == null ? null : bd.setScale(scale, java.math.RoundingMode.HALF_UP); }
    @Override public BigDecimal getBigDecimal(String c, int scale) throws SQLException { return getBigDecimal(findColumnIndex(c), scale); }
    @Override public int findColumn(String c) throws SQLException { return findColumnIndex(c); }

    // ── ResultSetMetaData ─────────────────────────────────────────────────────

    @Override
    public ResultSetMetaData getMetaData() {
        return new ResultSetMetaData() {
            @Override public int getColumnCount() { return columns.length; }
            @Override public String getColumnName(int i) throws SQLException { if (i < 1 || i > columns.length) throw new SQLException("Index out of range"); return columns[i-1]; }
            @Override public String getColumnLabel(int i) throws SQLException { return getColumnName(i); }
            @Override public int getColumnType(int i) { return Types.VARCHAR; }
            @Override public String getColumnTypeName(int i) { return "VARCHAR"; }
            @Override public String getColumnClassName(int i) { return "java.lang.String"; }
            @Override public int getColumnDisplaySize(int i) { return 255; }
            @Override public int getPrecision(int i) { return 255; }
            @Override public int getScale(int i) { return 0; }
            @Override public boolean isAutoIncrement(int i) { return false; }
            @Override public boolean isCaseSensitive(int i) { return false; }
            @Override public boolean isSearchable(int i) { return false; }
            @Override public boolean isCurrency(int i) { return false; }
            @Override public int isNullable(int i) { return columnNullable; }
            @Override public boolean isSigned(int i) { return false; }
            @Override public boolean isReadOnly(int i) { return true; }
            @Override public boolean isWritable(int i) { return false; }
            @Override public boolean isDefinitelyWritable(int i) { return false; }
            @Override public String getTableName(int i) { return ""; }
            @Override public String getSchemaName(int i) { return ""; }
            @Override public String getCatalogName(int i) { return ""; }
            @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("Not a wrapper"); }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }

    // ── Not supported / no-op ─────────────────────────────────────────────────

    @Override public int getType() { return TYPE_FORWARD_ONLY; }
    @Override public int getConcurrency() { return CONCUR_READ_ONLY; }
    @Override public int getHoldability() { return CLOSE_CURSORS_AT_COMMIT; }
    @Override public int getFetchDirection() { return FETCH_FORWARD; }
    @Override public void setFetchDirection(int d) {}
    @Override public int getFetchSize() { return rows.size(); }
    @Override public void setFetchSize(int r) {}
    @Override public boolean rowUpdated() { return false; }
    @Override public boolean rowInserted() { return false; }
    @Override public boolean rowDeleted() { return false; }

    @Override public InputStream getAsciiStream(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getUnicodeStream(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getBinaryStream(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getAsciiStream(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getUnicodeStream(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public InputStream getBinaryStream(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Reader getCharacterStream(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Reader getCharacterStream(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Date getDate(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Date getDate(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Date getDate(int i, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Date getDate(String c, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Time getTime(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Time getTime(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Time getTime(int i, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Time getTime(String c, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Timestamp getTimestamp(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Timestamp getTimestamp(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Timestamp getTimestamp(int i, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Timestamp getTimestamp(String c, Calendar cal) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Object getObject(int i, Map<String,Class<?>> m) throws SQLException { return getObject(i); }
    @Override public Object getObject(String c, Map<String,Class<?>> m) throws SQLException { return getObject(c); }
    @Override public <T> T getObject(int i, Class<T> type) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public <T> T getObject(String c, Class<T> type) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Ref getRef(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Ref getRef(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Blob getBlob(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Blob getBlob(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Clob getClob(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Clob getClob(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Array getArray(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Array getArray(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public URL getURL(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public URL getURL(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public RowId getRowId(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public RowId getRowId(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public NClob getNClob(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public NClob getNClob(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public SQLXML getSQLXML(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public SQLXML getSQLXML(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public String getNString(int i) throws SQLException { return getString(i); }
    @Override public String getNString(String c) throws SQLException { return getString(c); }
    @Override public Reader getNCharacterStream(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public Reader getNCharacterStream(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }

    @Override public void insertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void deleteRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void refreshRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void cancelRowUpdates() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void moveToInsertRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void moveToCurrentRow() throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNull(int i) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNull(String c) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBoolean(int i, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBoolean(String c, boolean x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateByte(int i, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateByte(String c, byte x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateShort(int i, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateShort(String c, short x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateInt(int i, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateInt(String c, int x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateLong(int i, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateLong(String c, long x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateFloat(int i, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateFloat(String c, float x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDouble(int i, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDouble(String c, double x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBigDecimal(int i, BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBigDecimal(String c, BigDecimal x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateString(int i, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateString(String c, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBytes(int i, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBytes(String c, byte[] x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDate(int i, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateDate(String c, Date x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTime(int i, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTime(String c, Time x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTimestamp(int i, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateTimestamp(String c, Timestamp x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int i, InputStream x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String c, InputStream x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int i, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String c, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(int i, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateAsciiStream(String c, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int i, InputStream x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String c, InputStream x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int i, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String c, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(int i, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBinaryStream(String c, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int i, Reader x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String c, Reader x, int l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int i, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String c, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(int i, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateCharacterStream(String c, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(int i, Object x, int s) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(int i, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(String c, Object x, int s) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateObject(String c, Object x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRef(int i, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRef(String c, Ref x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int i, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String c, Blob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int i, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String c, InputStream x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(int i, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateBlob(String c, InputStream x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int i, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String c, Clob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int i, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String c, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(int i, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateClob(String c, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateArray(int i, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateArray(String c, Array x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRowId(int i, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateRowId(String c, RowId x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNString(int i, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNString(String c, String x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int i, NClob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String c, NClob x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int i, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String c, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(int i, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNClob(String c, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateSQLXML(int i, SQLXML x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateSQLXML(String c, SQLXML x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(int i, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(String c, Reader x, long l) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(int i, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }
    @Override public void updateNCharacterStream(String c, Reader x) throws SQLException { throw new SQLFeatureNotSupportedException(); }

    @Override public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) return iface.cast(this);
        throw new SQLException("Not a wrapper for " + iface);
    }
    @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
}
