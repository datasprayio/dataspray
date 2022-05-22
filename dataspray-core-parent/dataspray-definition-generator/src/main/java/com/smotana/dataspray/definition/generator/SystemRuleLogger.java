package com.smotana.dataspray.definition.generator;

import org.jsonschema2pojo.AbstractRuleLogger;

public class SystemRuleLogger extends AbstractRuleLogger {

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    protected void doDebug(String msg) {
        System.err.println("DEBUG: " + msg);
    }

    @Override
    protected void doError(String msg, Throwable th) {
        System.err.println("ERROR: " + msg);
        th.printStackTrace(System.err);
    }

    @Override
    protected void doInfo(String msg) {
        System.err.println("INFO: " + msg);
    }

    @Override
    protected void doTrace(String msg) {
        System.err.println("TRACE: " + msg);
    }

    @Override
    protected void doWarn(String msg, Throwable e) {
        System.err.println("WARN: " + msg);
    }
}
