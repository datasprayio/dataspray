package io.dataspray.store;

import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;

@Slf4j
@ApplicationScoped
public class NoopCustomerLogger implements CustomerLogger {

    @Override
    public void error(String accountId, String msg) {
        log.error("{}{}", getPrefix(accountId), msg);
    }

    @Override
    public void warn(String accountId, String msg) {
        log.warn("{}{}", getPrefix(accountId), msg);
    }

    @Override
    public void info(String accountId, String msg) {
        log.info("{}{}", getPrefix(accountId), msg);
    }

    private String getPrefix(String accountId) {
        return "Customer log for " + accountId + ": ";
    }
}
