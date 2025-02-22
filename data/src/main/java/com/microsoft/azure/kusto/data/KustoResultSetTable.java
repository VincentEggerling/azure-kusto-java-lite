// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

package com.microsoft.azure.kusto.data;

import com.microsoft.azure.kusto.data.exceptions.KustoServiceQueryError;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

// This class does not keep an open connection with the cluster - the results are evaluated once and can be get by getData()
public class KustoResultSetTable implements ResultSet {
    private static final String TABLE_NAME_PROPERTY_NAME = "TableName";
    private static final String TABLE_ID_PROPERTY_NAME = "TableId";
    private static final String TABLE_KIND_PROPERTY_NAME = "TableKind";
    private static final String COLUMNS_PROPERTY_NAME = "Columns";
    private static final String COLUMN_NAME_PROPERTY_NAME = "ColumnName";
    private static final String COLUMN_TYPE_PROPERTY_NAME = "ColumnType";
    private static final String COLUMN_TYPE_SECOND_PROPERTY_NAME = "DataType";
    private static final String ROWS_PROPERTY_NAME = "Rows";
    private static final String EXCEPTIONS_PROPERTY_NAME = "Exceptions";
    private static final String EXCEPTIONS_MESSAGE = "Query execution failed with multiple inner exceptions";

    private final List<List<Object>> rows;
    private String tableName;
    private String tableId;
    private WellKnownDataSet tableKind;
    private final Map<String, KustoResultColumn> columns = new HashMap<>();
    private KustoResultColumn[] columnsAsArray = null;
    private Iterator<List<Object>> rowIterator;
    private List<Object> currentRow = null;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getTableId() {
        return tableId;
    }

    public KustoResultColumn[] getColumns() {
        return columnsAsArray;
    }

    void setTableId(String tableId) {
        this.tableId = tableId;
    }

    void setTableKind(WellKnownDataSet tableKind) {
        this.tableKind = tableKind;
    }

    WellKnownDataSet getTableKind() {
        return tableKind;
    }

    protected KustoResultSetTable(JSONObject jsonTable) throws KustoServiceQueryError {
        tableName = jsonTable.optString(TABLE_NAME_PROPERTY_NAME);
        tableId = jsonTable.optString(TABLE_ID_PROPERTY_NAME);
        String tableKindString = jsonTable.optString(TABLE_KIND_PROPERTY_NAME);
        tableKind = StringUtils.isBlank(tableKindString) ? null : WellKnownDataSet.valueOf(tableKindString);
        JSONArray columnsJson = jsonTable.optJSONArray(COLUMNS_PROPERTY_NAME);
        if (columnsJson != null) {
            columnsAsArray = new KustoResultColumn[columnsJson.length()];
            for (int i = 0; i < columnsJson.length(); i++) {
                JSONObject jsonCol = columnsJson.getJSONObject(i);
                String columnType = jsonCol.optString(COLUMN_TYPE_PROPERTY_NAME);
                if (columnType.equals("")) {
                    columnType = jsonCol.optString(COLUMN_TYPE_SECOND_PROPERTY_NAME);
                }
                KustoResultColumn col = new KustoResultColumn(jsonCol.getString(COLUMN_NAME_PROPERTY_NAME), columnType, i);
                columnsAsArray[i] = col;
                columns.put(jsonCol.getString(COLUMN_NAME_PROPERTY_NAME), col);
            }
        }

        JSONArray exceptions;
        JSONArray jsonRows = jsonTable.optJSONArray(ROWS_PROPERTY_NAME);
        if (jsonRows != null) {
            List<List<Object>> values = new ArrayList<>();
            for (int i = 0; i < jsonRows.length(); i++) {
                Object row = jsonRows.get(i);
                if (row instanceof JSONObject) {
                    exceptions = ((JSONObject) row).optJSONArray(EXCEPTIONS_PROPERTY_NAME);
                    if (exceptions != null) {
                        if (exceptions.length() == 1) {
                            String message = exceptions.getString(0);
                            throw new KustoServiceQueryError(message);
                        } else {
                            throw new KustoServiceQueryError(exceptions, false, EXCEPTIONS_MESSAGE);
                        }
                    } else {
                        throw new KustoServiceQueryError(((JSONObject) row).getJSONArray(
                                "OneApiErrors"),true, EXCEPTIONS_MESSAGE);
                    }
                }
                JSONArray rowAsJsonArray = jsonRows.getJSONArray(i);
                List<Object> rowVector = new ArrayList<>();
                for (int j = 0; j < rowAsJsonArray.length(); ++j) {
                    Object obj = rowAsJsonArray.get(j);
                    if (obj == JSONObject.NULL) {
                        rowVector.add(null);
                    } else {
                        rowVector.add(obj);
                    }
                }
                values.add(rowVector);
            }

            rows = values;
        } else {
            rows = new ArrayList<>();
        }

        rowIterator = rows.iterator();
    }

    public List<Object> getCurrentRow() {
        return currentRow;
    }

    @Override
    public boolean next() {
        boolean hasNext = rowIterator.hasNext();
        if (hasNext) {
            currentRow = rowIterator.next();
        }
        return hasNext;
    }

    public List<List<Object>> getData() {
        return rows;
    }

    @Override
    public void close() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Kusto resultSet is not closeable as there is no open connection");
    }

    @Override
    public boolean wasNull() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    private Object get(int columnIndex) {
        return currentRow.get(columnIndex);
    }

    private Object get(String colName) {
        return currentRow.get(findColumn(colName));
    }

    @Override
    public String getString(int columnIndex) {
        return get(columnIndex).toString();
    }

    @Override
    public boolean getBoolean(int columnIndex) {
        return (boolean) get(columnIndex);
    }

    public Boolean getBooleanObject(int columnIndex) {
        return (Boolean) get(columnIndex);
    }

    @Override
    public byte getByte(int columnIndex) {
        return (byte) get(columnIndex);
    }

    @Override
    public short getShort(int columnIndex) {
        Object obj = get(columnIndex);
        if (obj instanceof Integer) {
            return ((Integer) obj).shortValue();
        }
        return (short) get(columnIndex);
    }

    public Short getShortObject(int columnIndex) {
        Object obj = get(columnIndex);
        if (obj == null) {
            return null;
        }
        return getShort(columnIndex);
    }

    @Override
    public int getInt(int columnIndex) {
        return (int) get(columnIndex);
    }

    public Integer getIntegerObject(int columnIndex) {
        return (Integer) get(columnIndex);
    }

    @Override
    public long getLong(int columnIndex) {
        Object obj = get(columnIndex);
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        return (long) obj;
    }

    public Long getLongObject(int columnIndex) {
        Object obj = get(columnIndex);
        if (obj instanceof Integer) {
            return ((Integer) obj).longValue();
        }
        return (Long) obj;
    }


    @Override
    public float getFloat(int columnIndex) {
        return (float) get(columnIndex);
    }

    public Float getFloatObject(int columnIndex) {
        return (Float) get(columnIndex);
    }

    @Override
    public double getDouble(int columnIndex) {
        return (double) get(columnIndex);
    }

    public Double getDoubleObject(int columnIndex) {
        Object d = get(columnIndex);
        if (d instanceof BigDecimal) {
            return ((BigDecimal) d).doubleValue();
        }
        return (Double) get(columnIndex);
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(int columnIndex, int scale) {
        if (get(columnIndex) == null) {
            return null;
        }

        return (BigDecimal) get(columnIndex);
    }

    @Override
    public byte[] getBytes(int columnIndex) {
        return (byte[]) get(columnIndex);
    }

    @Override
    public Date getDate(int columnIndex) throws SQLException {
        return getDate(columnIndex, Calendar.getInstance());
    }

    @Override
    public Time getTime(int columnIndex) throws SQLException {
        LocalTime time = getLocalTime(columnIndex);
        if (time == null) {
            return null;
        }

        return Time.valueOf(getLocalTime(columnIndex));
    }

    @Override
    public Timestamp getTimestamp(int columnIndex) throws SQLException {
        switch (columnsAsArray[columnIndex].getColumnType()) {
            case "string":
            case "datetime":
                if (get(columnIndex) == null) {
                    return null;
                }
                return Timestamp.valueOf(StringUtils.chop(getString(columnIndex)).replace("T", " "));
            case "long":
            case "int":
                Long l = getLongObject(columnIndex);
                if (l == null) {
                    return null;
                }

                return new Timestamp(l);
        }
        throw new SQLException("Error parsing timestamp - expected string or long columns.");
    }

    @Override
    public InputStream getAsciiStream(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    @Deprecated
    public InputStream getUnicodeStream(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public InputStream getBinaryStream(int columnIndex) throws SQLFeatureNotSupportedException {
        if (columnsAsArray[columnIndex].getColumnType().equals("String")) {
            return new ByteArrayInputStream(getString(columnIndex).getBytes());
        }

        throw new SQLFeatureNotSupportedException("getBinaryStream is only available for strings");
    }

    @Override
    public String getString(String columnName) {
        return get(columnName).toString();
    }

    @Override
    public boolean getBoolean(String columnName) {
        return (boolean) get(columnName);
    }

    public Boolean getBooleanObject(String columnName) {
        return (Boolean) get(columnName);
    }

    @Override
    public byte getByte(String columnName) {
        return (byte) get(columnName);
    }

    @Override
    public short getShort(String columnName) {
        return (short) getShort(findColumn(columnName));
    }

    public Short getShortObject(String columnName) {
        return getShortObject(findColumn(columnName));
    }

    @Override
    public int getInt(String columnName) {
        return (int) get(columnName);
    }

    public int getIntegerObject(String columnName) {
        return getIntegerObject(findColumn(columnName));
    }

    @Override
    public long getLong(String columnName) {
        return (long) get(columnName);
    }

    public Long getLongObject(String columnName) {
        return getLongObject(findColumn(columnName));
    }

    @Override
    public float getFloat(String columnName) {
        return (float) get(columnName);
    }

    public Float getFloatObject(String columnName) {
        return getFloatObject(findColumn(columnName));
    }

    @Override
    public double getDouble(String columnName) {
        return (double) get(columnName);
    }

    public Double getDoubleObject(String columnName) {
        return (Double) get(columnName);
    }

    @Override
    @Deprecated
    public BigDecimal getBigDecimal(String columnName, int scale) {
        return getBigDecimal(findColumn(columnName), scale);
    }

    @Override
    public byte[] getBytes(String columnName) {
        return (byte[]) get(columnName);
    }

    @Override
    public Date getDate(String columnName) throws SQLException {
        return getDate(findColumn(columnName));
    }

    @Override
    public Time getTime(String columnName) throws SQLException {
        return getTime(findColumn(columnName));
    }

    @Override
    public Timestamp getTimestamp(String columnName) throws SQLException {
        return getTimestamp(findColumn(columnName));
    }

    @Override
    public InputStream getAsciiStream(String columnName) {
        return (InputStream) get(columnName);
    }

    @Override
    @Deprecated
    public InputStream getUnicodeStream(String columnName) {
        return (InputStream) get(columnName);
    }

    @Override
    public InputStream getBinaryStream(String columnName) throws SQLFeatureNotSupportedException {
        return getBinaryStream(findColumn(columnName));
    }

    @Override
    public SQLWarning getWarnings() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void clearWarnings() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public String getCursorName() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public ResultSetMetaData getMetaData() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Object getObject(int columnIndex) {
        return get(columnIndex);
    }

    @Override
    public Object getObject(String columnName) {
        return get(columnName);
    }

    public JSONObject getJSONObject(String colName) {
        return getJSONObject(findColumn(colName));
    }

    public JSONObject getJSONObject(int columnIndex) {
        return (JSONObject) get(columnIndex);
    }

    public UUID getUUID(int columnIndex) {
        Object u = get(columnIndex);
        if (u == null) {
            return null;
        }
        return UUID.fromString((String) u);
    }

    public UUID getUUID(String columnName) {
        return getUUID(findColumn(columnName));
    }

    @Override
    public int findColumn(String columnName) {
        return columns.get(columnName).getOrdinal();
    }

    @Override
    public Reader getCharacterStream(int columnIndex) {
        return new StringReader(getString(columnIndex));
    }

    @Override
    public Reader getCharacterStream(String columnName) {
        return new StringReader(getString(columnName));
    }

    @Override
    public BigDecimal getBigDecimal(int columnIndex) {
        if (get(columnIndex) == null) {
            return null;
        }

        return new BigDecimal(getString(columnIndex));
    }

    @Override
    public BigDecimal getBigDecimal(String columnName) {
        return getBigDecimal(findColumn(columnName));
    }

    @Override
    public boolean isBeforeFirst() {
        return currentRow == null;
    }

    @Override
    public boolean isAfterLast() {
        return currentRow == null && !rowIterator.hasNext();
    }

    @Override
    public boolean isFirst() throws SQLException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public boolean isLast() {
        return currentRow != null && !rowIterator.hasNext();
    }

    @Override
    public void beforeFirst() {
        rowIterator = rows.iterator();
    }

    @Override
    public void afterLast() {
        while (next()) ;
    }

    @Override
    public boolean first() {
        if (rows.isEmpty())
            return false;
        rowIterator = rows.iterator();
        currentRow = rowIterator.next();
        return true;
    }

    @Override
    public boolean last() {
        if (rows.isEmpty())
            return false;
        while (rowIterator.next() != null) ;
        return true;
    }

    // This means the row number in the Kusto database and therefore is irrelevant to Kusto
    @Override
    public int getRow() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public boolean absolute(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public boolean relative(int columnIndex) {
        return false;
    }

    @Override
    public boolean previous() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void setFetchDirection(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public int getFetchDirection() {
        return FETCH_FORWARD;
    }

    @Override
    public void setFetchSize(int columnIndex) {

    }

    @Override
    public int getFetchSize() {
        return 0;
    }

    @Override
    public int getType() {
        return TYPE_SCROLL_INSENSITIVE;
    }

    @Override
    public int getConcurrency() {
        return CONCUR_READ_ONLY;
    }

    @Override
    public boolean rowUpdated() {
        return false;
    }

    @Override
    public boolean rowInserted() {
        return false;
    }

    @Override
    public boolean rowDeleted() {
        return false;
    }

    @Override
    public void updateNull(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateBoolean(int columnIndex, boolean b) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateByte(int columnIndex, byte b) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateShort(int columnIndex, short i1) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateInt(int columnIndex, int columnIndex1) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateLong(int columnIndex, long l) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateFloat(int columnIndex, float v) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateDouble(int columnIndex, double v) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateBigDecimal(int columnIndex, BigDecimal bigDecimal) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateString(int columnIndex, String columnName) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateBytes(int columnIndex, byte[] bytes) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateDate(int columnIndex, Date date) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateTime(int columnIndex, Time time) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateTimestamp(int columnIndex, Timestamp timestamp) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream inputStream, int columnIndex1) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream inputStream, int columnIndex1) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader reader, int columnIndex1) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateObject(int columnIndex, Object o, int columnIndex1) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateObject(int columnIndex, Object o) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateNull(String columnName) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateBoolean(String columnName, boolean b) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateByte(String columnName, byte b) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateShort(String columnName, short i) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateInt(String columnName, int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateLong(String columnName, long l) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateFloat(String columnName, float v) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateDouble(String columnName, double v) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateBigDecimal(String columnName, BigDecimal bigDecimal) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateString(String columnName, String s1) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateBytes(String columnName, byte[] bytes) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateDate(String columnName, Date date) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateTime(String columnName, Time time) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateTimestamp(String columnName, Timestamp timestamp) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateAsciiStream(String columnName, InputStream inputStream, int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateBinaryStream(String columnName, InputStream inputStream, int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateCharacterStream(String columnName, Reader reader, int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateObject(String columnName, Object o, int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateObject(String columnName, Object o) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void insertRow() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateRow() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void deleteRow() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void refreshRow() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void cancelRowUpdates() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void moveToInsertRow() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void moveToCurrentRow() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Statement getStatement() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Object getObject(int columnIndex, Map<String, Class<?>> map) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Ref getRef(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Blob getBlob(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Clob getClob(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Array getArray(int columnIndex) {
        return (Array) get(columnIndex);
    }

    @Override
    public Object getObject(String columnName, Map<String, Class<?>> map) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Ref getRef(String columnName) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Blob getBlob(String columnName) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Clob getClob(String columnName) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public Array getArray(String columnName) {
        return getArray(findColumn(columnName));
    }

    /*
     * This will return the full dateTime from Kusto as sql.Date is less precise
     */
    public LocalDateTime getKustoDateTime(int columnIndex) {
        if (get(columnIndex) == null) {
            return null;
        }
        String dateString = getString(columnIndex);
        DateTimeFormatter dateTimeFormatter;
        if (dateString.length() < 21) {
            dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive().append(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")).toFormatter();
        } else {
            dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive().append(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS'Z'")).toFormatter();
        }
        return LocalDateTime.parse(getString(columnIndex), dateTimeFormatter);
    }

    public LocalDateTime getKustoDateTime(String columnName) {
        return getKustoDateTime(findColumn(columnName));
    }

    /**
     * This will cut the date up to yyyy-MM-dd'T'HH:mm:ss.SSS
     */
    @Override
    public Date getDate(int columnIndex, Calendar calendar) throws SQLException {
        if (calendar == null) {
            return getDate(columnIndex);
        }

        switch (columnsAsArray[columnIndex].getColumnType()) {
            case "string":
            case "datetime":
                try {
                    if (get(columnIndex) == null) {
                        return null;
                    }
                    String dateString = getString(columnIndex);
                    FastDateFormat dateFormat;
                    if (dateString.length() < 21) {
                        dateFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss", calendar.getTimeZone());
                    } else {
                        dateFormat = FastDateFormat.getInstance("yyyy-MM-dd'T'HH:mm:ss.SSS", calendar.getTimeZone());
                    }
                    return new java.sql.Date(dateFormat.parse(dateString.substring(0, Math.min(dateString.length() - 1, 23))).getTime());
                } catch (Exception e) {
                    throw new SQLException("Error parsing Date", e);
                }
            case "long":
            case "int":
                Long longVal = getLongObject(columnIndex);
                if (longVal == null) {
                    return null;
                }
                return new Date(longVal);
        }
        throw new SQLException("Error parsing Date - expected string, long or datetime data type.");
    }

    @Override
    public Date getDate(String columnName, Calendar calendar) throws SQLException {
        return getDate(findColumn(columnName));
    }

    @Override
    public Time getTime(int columnIndex, Calendar calendar) throws SQLException {
        return getTime(columnIndex);
    }

    @Override
    public Time getTime(String columnName, Calendar calendar) throws SQLException {
        return getTime(columnName);
    }

    public LocalTime getLocalTime(int columnIndex) {
        Object time = get(columnIndex);
        if (time == null) {
            return null;
        }
        return LocalTime.parse((String) time);
    }

    public LocalTime getLocalTime(String columnName) {
        return getLocalTime(findColumn(columnName));
    }

    @Override
    public Timestamp getTimestamp(int columnIndex, Calendar calendar) throws SQLException {
        return getTimestamp(columnIndex);
    }

    @Override
    public Timestamp getTimestamp(String columnName, Calendar calendar) throws SQLException {
        return getTimestamp(findColumn(columnName), calendar);
    }

    @Override
    public URL getURL(int columnIndex) throws SQLException {
        try {
            return new URL(getString(columnIndex));
        } catch (MalformedURLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public URL getURL(String columnName) throws SQLException {
        try {
            return new URL(getString(columnName));
        } catch (MalformedURLException e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void updateRef(int columnIndex, Ref ref) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");

    }

    @Override
    public void updateRef(String columnName, Ref ref) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");

    }

    @Override
    public void updateBlob(int columnIndex, Blob blob) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");

    }

    @Override
    public void updateBlob(String columnName, Blob blob) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");

    }

    @Override
    public void updateClob(int columnIndex, Clob clob) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");

    }

    @Override
    public void updateClob(String columnName, Clob clob) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");

    }

    @Override
    public void updateArray(int columnIndex, Array array) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");

    }

    @Override
    public void updateArray(String columnName, Array array) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public RowId getRowId(int columnIndex) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public RowId getRowId(String columnName) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateRowId(int columnIndex, RowId rowId) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public void updateRowId(String columnName, RowId rowId) throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public int getHoldability() throws SQLException {
        throw new SQLFeatureNotSupportedException("Method not supported");
    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void updateNString(int columnIndex, String s) {

    }

    @Override
    public void updateNString(String columnName, String s1) {

    }

    @Override
    public void updateNClob(int columnIndex, NClob nClob) {

    }

    @Override
    public void updateNClob(String columnName, NClob nClob) {

    }

    @Override
    public NClob getNClob(int columnIndex) {
        return null;
    }

    @Override
    public NClob getNClob(String columnName) {
        return null;
    }

    @Override
    public SQLXML getSQLXML(int columnIndex) {
        return null;
    }

    @Override
    public SQLXML getSQLXML(String columnName) {
        return null;
    }

    @Override
    public void updateSQLXML(int columnIndex, SQLXML sqlxml) {

    }

    @Override
    public void updateSQLXML(String columnName, SQLXML sqlxml) {

    }

    @Override
    public String getNString(int columnIndex) {
        return null;
    }

    @Override
    public String getNString(String columnName) {
        return null;
    }

    @Override
    public Reader getNCharacterStream(int columnIndex) {
        return null;
    }

    @Override
    public Reader getNCharacterStream(String columnName) {
        return null;
    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader reader, long l) {

    }

    @Override
    public void updateNCharacterStream(String columnName, Reader reader, long l) {

    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream inputStream, long l) {

    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream inputStream, long l) {

    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader reader, long l) {

    }

    @Override
    public void updateAsciiStream(String columnName, InputStream inputStream, long l) {

    }

    @Override
    public void updateBinaryStream(String columnName, InputStream inputStream, long l) {

    }

    @Override
    public void updateCharacterStream(String columnName, Reader reader, long l) {

    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream, long l) {

    }

    @Override
    public void updateBlob(String columnName, InputStream inputStream, long l) {

    }

    @Override
    public void updateClob(int columnIndex, Reader reader, long l) {

    }

    @Override
    public void updateClob(String columnName, Reader reader, long l) {

    }

    @Override
    public void updateNClob(int columnIndex, Reader reader, long l) {

    }

    @Override
    public void updateNClob(String columnName, Reader reader, long l) {

    }

    @Override
    public void updateNCharacterStream(int columnIndex, Reader reader) {

    }

    @Override
    public void updateNCharacterStream(String columnName, Reader reader) {

    }

    @Override
    public void updateAsciiStream(int columnIndex, InputStream inputStream) {

    }

    @Override
    public void updateBinaryStream(int columnIndex, InputStream inputStream) {

    }

    @Override
    public void updateCharacterStream(int columnIndex, Reader reader) {

    }

    @Override
    public void updateAsciiStream(String columnName, InputStream inputStream) {

    }

    @Override
    public void updateBinaryStream(String columnName, InputStream inputStream) {

    }

    @Override
    public void updateCharacterStream(String columnName, Reader reader) {

    }

    @Override
    public void updateBlob(int columnIndex, InputStream inputStream) {

    }

    @Override
    public void updateBlob(String columnName, InputStream inputStream) {

    }

    @Override
    public void updateClob(int columnIndex, Reader reader) {

    }

    @Override
    public void updateClob(String columnName, Reader reader) {

    }

    @Override
    public void updateNClob(int columnIndex, Reader reader) {

    }

    @Override
    public void updateNClob(String columnName, Reader reader) {

    }

    @Override
    public <T> T getObject(int columnIndex, Class<T> aClass) {
        return null;
    }

    @Override
    public <T> T getObject(String columnName, Class<T> aClass) {
        return null;
    }

    @Override
    public <T> T unwrap(Class<T> aClass) {
        return null;
    }

    @Override
    public boolean isWrapperFor(Class<?> aClass) {
        return false;
    }

    public int count() {
        return rows.size();
    }
}
