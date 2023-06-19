package com.kvitka.idejarexecutor.controller;

import com.kvitka.idejarexecutor.dto.ExecutionRequest;
import com.kvitka.idejarexecutor.dto.ExecutionResult;
import com.kvitka.idejarexecutor.dto.MavenExecutionRequestDto;
import com.kvitka.idejarexecutor.enums.ProcessStatus;
import com.kvitka.idejarexecutor.model.ProcessInfo;
import com.kvitka.idejarexecutor.service.JarExecutionService;
import com.kvitka.idejarexecutor.service.MavenProjectExecutionService;
import com.kvitka.idejarexecutor.service.ProcessRegistryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/jar-exec/api")
public class ContentController {

    private final JarExecutionService executionService;
    private final MavenProjectExecutionService mavenProjectExecutionService;
    private final ProcessRegistryService processRegistryService;

    @PostMapping("/exec")
    public ExecutionResult executeJavaProject(@RequestBody ExecutionRequest execRequest)
            throws IOException, InterruptedException {
        log.info("[executeJavaProject] method started");
        ExecutionResult executionResultDto = executionService.exec(execRequest);
        log.info("[executeJavaProject] method finished ({} ms)", executionResultDto.getExecutionTime());
        return executionResultDto;
    }

    @PutMapping("/mavenExec")
    public ResponseEntity<Void> executeMavenProject(@RequestBody MavenExecutionRequestDto mavenExecutionRequestDto) {
        ProcessInfo processInfo = processRegistryService.register(mavenExecutionRequestDto.getProjectUUID());
        //noinspection CodeBlock2Expr
        new Thread(() -> {
            mavenProjectExecutionService.startMavenProjectProcess(mavenExecutionRequestDto, processInfo);
        }).start();
        return new ResponseEntity<>(HttpStatusCode.valueOf(200));
    }

    @GetMapping("/mavenExecInfo/{projectUUID}")
    public ResponseEntity<?> getMavenExecInfo(@PathVariable("projectUUID") String projectUUID) {
        ProcessInfo processInfo = processRegistryService.get(projectUUID);
        if (processInfo == null) return ResponseEntity.noContent(/*? 204 */).build();
        boolean finished = processInfo.getStatus().isFinished();
        if (finished) processInfo.setDeployPort(null);
        processInfo.setFinished(finished);
        return ResponseEntity.ok(/*? 200 */ processInfo);
    }

    @PutMapping("/stopMavenExec/{projectUUID}")
    public void stopMavenProjectExecution(@PathVariable("projectUUID") String projectUUID) {
        System.out.println(projectUUID);
        processRegistryService.killProcess(processRegistryService.get(projectUUID), ProcessStatus.USER_INTERRUPT);
    }
}
