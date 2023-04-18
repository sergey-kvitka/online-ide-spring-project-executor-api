package com.kvitka.idejarexecutor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionRequest {
    private String projectId;
    private String mainClass;
    private List<FileDto> files;
    private List<String> args;
    private List<String> consoleInput;
}
