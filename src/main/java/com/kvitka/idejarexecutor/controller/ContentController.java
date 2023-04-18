package com.kvitka.idejarexecutor.controller;

import com.kvitka.idejarexecutor.dto.ExecutionRequest;
import com.kvitka.idejarexecutor.dto.ExecutionResult;
import com.kvitka.idejarexecutor.service.JarExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/jar-exec/api")
public class ContentController {

    private final JarExecutionService executionService;

    @PostMapping("/exec")
    public ExecutionResult executeJavaProject(@RequestBody ExecutionRequest execRequest)
            throws IOException, InterruptedException {
        log.info(execRequest.toString());
        long time = System.nanoTime();
        log.info("[executeJavaProject] method started");
        ExecutionResult executionResultDto = executionService.exec(execRequest);
        log.info("[executeJavaProject] method finished ({} ms)", ((double) (System.nanoTime() - time)) / 1000000);
        return executionResultDto;
    }
}
