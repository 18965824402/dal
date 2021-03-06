package com.ctrip.platform.dal.dao.datasource;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicReference;

import javax.sql.DataSource;

import com.ctrip.platform.dal.dao.configure.DataSourceConfigureChangeEvent;
import com.ctrip.platform.dal.dao.configure.DataSourceConfigure;
import com.ctrip.platform.dal.dao.configure.DataSourceConfigureChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RefreshableDataSource implements DataSource, DataSourceConfigureChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(RefreshableDataSource.class);
    private AtomicReference<SingleDataSource> dataSourceReference = new AtomicReference<>();

    public RefreshableDataSource(String name, DataSourceConfigure config) throws SQLException {
        SingleDataSource dataSource = new SingleDataSource(name, config);
        dataSourceReference.set(dataSource);
    }

    @Override
    public synchronized void configChanged(DataSourceConfigureChangeEvent event) throws SQLException {
        String name = event.getName();
        DataSourceConfigure newConfigure = event.getNewDataSourceConfigure();
        SingleDataSource newDataSource = new SingleDataSource(name, newConfigure);
        logger.debug(String.format("DAL debug:(configChanged)new datasource url:%s",
                newDataSource.getDataSourceConfigure().getConnectionUrl()));
        SingleDataSource oldDataSource = dataSourceReference.getAndSet(newDataSource);
        logger.debug(String.format("DAL debug:(configChanged)old datasource url:%s",
                oldDataSource.getDataSourceConfigure().getConnectionUrl()));

        close(oldDataSource);
        logger.debug(String.format("DAL debug:(configChanged)datasource %s added to destroy queue.", name));
    }

    private void close(SingleDataSource dataSource) {
        DataSourceTerminator.getInstance().close(dataSource);
    }

    private DataSource getDataSource() {
        DataSource dataSource = dataSourceReference.get().getDataSource();
        if (dataSource == null)
            throw new IllegalStateException("DataSource cannot be null.");
        return dataSource;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    @Override
    public Connection getConnection(String paramString1, String paramString2) throws SQLException {
        return getDataSource().getConnection(paramString1, paramString2);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return getDataSource().getLogWriter();
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return getDataSource().getLoginTimeout();
    }

    @Override
    public void setLogWriter(PrintWriter paramPrintWriter) throws SQLException {
        getDataSource().setLogWriter(paramPrintWriter);
    }

    @Override
    public void setLoginTimeout(int paramInt) throws SQLException {
        getDataSource().setLoginTimeout(paramInt);
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return getDataSource().getParentLogger();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return getDataSource().unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return getDataSource().isWrapperFor(iface);
    }
}
