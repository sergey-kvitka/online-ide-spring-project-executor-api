package com.kvitka.idejarexecutor.enums;

import lombok.Getter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
public class LogGroup {
    private final ProcessStage processStage;
    private final List<String> logs = new CopyOnWriteArrayList<>();

    public LogGroup(ProcessStage processStage) {
        this.processStage = processStage;
    }
}
