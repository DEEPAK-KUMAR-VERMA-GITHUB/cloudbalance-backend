package com.cloudkeeper.cloudbalance_backend.logging;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MyLogger implements Logger {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String LOG_FILE_PATH = "logs/cloudbalance.log";
    // ANSI COLOR CODES
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String GREEN = "\u001B[32m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private final String className;
    private final LogLevel logLevel;

    public MyLogger(Class<?> classs) {
        this.className = classs.getSimpleName();
        this.logLevel = LoggerConfig.getLogLevel();
    }


    @Override
    public void debug(String message, Object... args) {
        if (logLevel.isLoggable(LogLevel.DEBUG)) {
            log(LogLevel.DEBUG, formatMessage(message, args), null, CYAN);
        }
    }

    @Override
    public void info(String message, Object... args) {
        if (logLevel.isLoggable(LogLevel.INFO)) {
            log(LogLevel.INFO, formatMessage(message, args), null, GREEN);
        }
    }

    @Override
    public void warn(String message, Object... args) {
        if (logLevel.isLoggable(LogLevel.WARN)) {
            log(LogLevel.WARN, formatMessage(message, args), null, YELLOW);
        }
    }

    @Override
    public void error(String message, Object... args) {
        if (logLevel.isLoggable(LogLevel.ERROR)) {
            log(LogLevel.ERROR, formatMessage(message, args), null, RED);
        }
    }

    @Override
    public void error(String message, Throwable throwable, Object... args) {
        if (logLevel.isLoggable(LogLevel.ERROR)) {
            log(LogLevel.ERROR, formatMessage(message, args), throwable, RED);
        }
    }

    @Override
    public void trace(String message, Object... args) {
        if (logLevel.isLoggable(LogLevel.TRACE)) {
            log(LogLevel.TRACE, formatMessage(message, args), null, BLUE);
        }
    }


    private void log(LogLevel level, String message, Throwable throwable, String color) {
        String timestamp = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String threadName = Thread.currentThread().getName();

        String logMessage = String.format("[%s] [%s] [%s] [%s] - %s",
                timestamp, threadName, level.name(), className, message
        );

        // console output with colors
        System.out.println(color + logMessage + RESET);

        // file output
        writeToFile(logMessage);

        // print stack trace if throwable present
        if (throwable != null) {
            throwable.printStackTrace();
            writeThrowableToFile(throwable);
        }

    }

    //    Handles {} placeholders and replaces them with arguments
    private String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        // Use StringBuilder for efficient string manipulation
        StringBuilder result = new StringBuilder();
        int argIndex = 0;
        int length = message.length();

        for (int i = 0; i < length; i++) {
            char current = message.charAt(i);

            // Check for placeholder {}
            if (current == '{' && i + 1 < length && message.charAt(i + 1) == '}') {
                // Replace with argument if available
                if (argIndex < args.length) {
                    result.append(args[argIndex]);
                    argIndex++;
                } else {
                    // No more arguments, keep placeholder
                    result.append("{}");
                }
                i++; // Skip the next '}'
            } else {
                result.append(current);
            }
        }

        return result.toString();
    }

    private void writeToFile(String message) {
        try (FileWriter fw = new FileWriter(LOG_FILE_PATH, true);
             PrintWriter pw = new PrintWriter(fw)
        ) {
            pw.println(message);
        } catch (IOException e) {
            System.err.println("Failed to write in log file : " + e.getMessage());
        }
    }

    private void writeThrowableToFile(Throwable throwable) {
        try (
                FileWriter fw = new FileWriter(LOG_FILE_PATH, true);
                PrintWriter pw = new PrintWriter(fw)
        ) {
            throwable.printStackTrace(pw);
        } catch (IOException e) {
            System.err.println("Failed to write throwable to log file : " + e.getMessage());
        }
    }
}
