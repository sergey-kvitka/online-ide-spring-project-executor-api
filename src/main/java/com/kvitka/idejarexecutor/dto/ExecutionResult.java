package com.kvitka.idejarexecutor.dto;

import com.kvitka.idejarexecutor.enums.ResultStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionResult {
    private Integer exitCode = 0;
    private Boolean hasExitCode;
    private ResultStatus status;
    private List<String> output;
    private Long executionTime;
}
