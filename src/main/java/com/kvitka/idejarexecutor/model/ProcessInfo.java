package com.kvitka.idejarexecutor.model;

import com.kvitka.idejarexecutor.enums.ProcessStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Setter
@ToString
public class ProcessInfo {
    private String projectUUID;
    private String deployPort;
    private ProcessStatus status = ProcessStatus.INIT;
    private List<String> logs = new CopyOnWriteArrayList<>();

    public boolean isFinished;

    public ProcessInfo(String projectUUID) {
        this.projectUUID = projectUUID;
    }

    public synchronized void saveLogLine(String line) {
        logs.add(line);
    }
}
