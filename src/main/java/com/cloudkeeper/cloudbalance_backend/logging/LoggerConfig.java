package com.cloudkeeper.cloudbalance_backend.logging;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoggerConfig {
    @Getter
    private static LogLevel logLevel = LogLevel.INFO;

    @Value("${logging.level.root:INFO}")
    public void setLogLevel(String level){
        try{
            LoggerConfig.logLevel = LogLevel.valueOf(level.toUpperCase());
        }catch (IllegalArgumentException e){
            LoggerConfig.logLevel = LogLevel.INFO;
        }
    }

}
