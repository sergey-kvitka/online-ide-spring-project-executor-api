package com.kvitka.idejarexecutor.configuration;

import com.kvitka.idejarexecutor.service.ProcessRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.io.IOException;

@Configuration
@RequiredArgsConstructor
public class MyConfiguration {

    private final ProcessRegistryService processRegistryService;

    @EventListener(ApplicationReadyEvent.class)
    public void runAfterStartup() throws IOException, InterruptedException {
        processRegistryService.listExistingProcesses();
    }
}
