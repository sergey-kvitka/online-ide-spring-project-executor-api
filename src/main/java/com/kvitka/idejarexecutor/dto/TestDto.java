package com.kvitka.idejarexecutor.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestDto {
    private List<FileDto> files;
    private String artifactId;
    private String groupId;
    private String port;
}
