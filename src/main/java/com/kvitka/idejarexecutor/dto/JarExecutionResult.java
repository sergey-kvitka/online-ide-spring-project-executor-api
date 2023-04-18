package com.kvitka.idejarexecutor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JarExecutionResult {
    int exitCode;
    List<String> output;
}
