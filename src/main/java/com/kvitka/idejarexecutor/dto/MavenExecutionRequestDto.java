package com.kvitka.idejarexecutor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MavenExecutionRequestDto {
    private List<FileDto> files;
    private String projectUUID;
    private String groupId;
    private String artifactId;
    private String applicationPort;
}
