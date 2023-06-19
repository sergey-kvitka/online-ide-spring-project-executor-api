package com.kvitka.idejarexecutor.service;

import com.kvitka.idejarexecutor.enums.ProcessStatus;
import com.kvitka.idejarexecutor.model.ProcessInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessRegistryService {

    private final DockerService dockerService;

    private final Map<String, ProcessInfo> registry = new ConcurrentHashMap<>();

    public synchronized ProcessInfo get(String projectUUID) {
        ProcessInfo processInfo = registry.get(projectUUID);
        if (processInfo != null && processInfo.getStatus().isFinished()) registry.remove(projectUUID);
        return processInfo;
    }

    public synchronized ProcessInfo register(String projectUUID) {
        ProcessInfo processInfo = registry.get(projectUUID);
        if (processInfo == null) {
            processInfo = new ProcessInfo(projectUUID);
            registry.put(projectUUID, processInfo);
        }
        log.info("Process {} registered", projectUUID);
        return processInfo;
    }

    public synchronized void killProcess(ProcessInfo processInfo, ProcessStatus cause) {
        if (processInfo == null) return;
        processInfo.setStatus(cause);
        try {
            dockerService.deleteContainer(
                    dockerService.containerNamePrefix + processInfo.getProjectUUID());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void listExistingProcesses() throws IOException, InterruptedException {
        List<List<String>> runningContainersTable = dockerService.getContainersTable();
        if (runningContainersTable == null || runningContainersTable.isEmpty()) return;

        int containerNameColumnIndex = runningContainersTable.get(0).indexOf("NAMES");
        int portColumnIndex = runningContainersTable.get(0).indexOf("PORTS");
        runningContainersTable.remove(0);
        for (List<String> row : runningContainersTable) {
            String containerName = row.get(containerNameColumnIndex);
            if (!containerName.startsWith(dockerService.containerNamePrefix)
                    || dockerService.containerNamePrefix.equals(containerName)) continue;
            String contNameWithoutPrefix = containerName.substring(dockerService.containerNamePrefix.length());

            String projectUUID = contNameWithoutPrefix.split("_")[0];
            String portsInfo = row.get(portColumnIndex);
            String deployPort = portsInfo.split("->")[0];
            ProcessInfo processInfo = register(projectUUID);
            processInfo.setStatus(ProcessStatus.DOCKER_DEPLOYING);
            processInfo.setDeployPort(deployPort);
            dockerService.followContainerLog(processInfo, containerName, this);
        }
    }
}
