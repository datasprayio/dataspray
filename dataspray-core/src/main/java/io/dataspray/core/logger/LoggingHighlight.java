package io.dataspray.core.logger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.pattern.color.ANSIConstants;
import ch.qos.logback.core.pattern.color.ForegroundCompositeConverterBase;

public class LoggingHighlight extends ForegroundCompositeConverterBase<ILoggingEvent> {

    @Override
    protected String getForegroundColorCode(ILoggingEvent event) {
        Level level = event.getLevel();
        switch (level.toInt()) {
            case Level.ERROR_INT:
                return ANSIConstants.BOLD + ANSIConstants.RED_FG; // default
            case Level.WARN_INT:
                return ANSIConstants.BOLD + ANSIConstants.YELLOW_FG;
            case Level.INFO_INT:
            case Level.DEBUG_INT:
            case Level.TRACE_INT:
            default:
                return ANSIConstants.DEFAULT_FG;
        }
    }
}
