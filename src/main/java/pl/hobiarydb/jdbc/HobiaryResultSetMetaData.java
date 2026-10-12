package pl.hobiarydb.jdbc;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

public class HobiaryResultSetMetaData implements ResultSetMetaData {

    private final String[] columns;

    HobiaryResultSetMetaData(String[] columns) {
        this.columns = columns != null ? columns : new String[0];
    }

    @Override
    public int getColumnCount() {
        return columns.length;
    }

    private String col(int column) throws SQLException {
        if (column < 1 || column > columns.length)
            throw new SQLException("Column index out of range: " + column);
        return columns[column - 1];
    }

    @Override public String getColumnName(int column)         throws SQLException { return col(column); }
    @Override public String getColumnLabel(int column)        throws SQLException { return col(column); }
    @Override public int    getColumnType(int column)         throws SQLException { col(column); return Types.VARCHAR; }
    @Override public String getColumnTypeName(int column)     throws SQLException { col(column); return "VARCHAR"; }
    @Override public String getColumnClassName(int column)    throws SQLException { col(column); return "java.lang.String"; }
    @Override public int    getColumnDisplaySize(int column)  throws SQLException { col(column); return 255; }
    @Override public int    getPrecision(int column)          throws SQLException { col(column); return 255; }
    @Override public int    getScale(int column)              throws SQLException { col(column); return 0; }
    @Override public String getTableName(int column)          throws SQLException { col(column); return ""; }
    @Override public String getCatalogName(int column)        throws SQLException { col(column); return ""; }
    @Override public String getSchemaName(int column)         throws SQLException { col(column); return ""; }
    @Override public boolean isAutoIncrement(int column)      throws SQLException { col(column); return false; }
    @Override public boolean isCaseSensitive(int column)      throws SQLException { col(column); return true; }
    @Override public boolean isSearchable(int column)         throws SQLException { col(column); return true; }
    @Override public boolean isCurrency(int column)           throws SQLException { col(column); return false; }
    @Override public int     isNullable(int column)           throws SQLException { col(column); return columnNullableUnknown; }
    @Override public boolean isSigned(int column)             throws SQLException { col(column); return false; }
    @Override public boolean isReadOnly(int column)           throws SQLException { col(column); return true; }
    @Override public boolean isWritable(int column)           throws SQLException { col(column); return false; }
    @Override public boolean isDefinitelyWritable(int column) throws SQLException { col(column); return false; }

    @Override public <T> T unwrap(Class<T> iface) throws SQLException { if (iface.isInstance(this)) return iface.cast(this); throw new SQLException("Not a wrapper for " + iface); }
    @Override public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
}