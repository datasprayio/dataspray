package io.dataspray.store;

public interface CustomerLogger {

    void error(String msg, String accountId);

    void warn(String msg, String accountId);

    void info(String msg, String accountId);
}
