package com.kvitka.idejarexecutor.service;

import com.kvitka.idejarexecutor.enums.ProcessStatus;
import com.kvitka.idejarexecutor.model.ProcessInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class DockerService {

    private static final Supplier<String[]> CONTAINER_LIST_COLUMNS = () ->
            new String[]{"CONTAINER ID", "IMAGE", "COMMAND", "CREATED", "STATUS", "PORTS", "NAMES"};

    @Value("${path.docker.docker}")
    private String dockerExe;

    public final String containerNamePrefix = "ide-cont-";

    private final FileService fileService;

    private final Runtime runtime;

    public List<List<String>> getContainersTable() throws IOException, InterruptedException {
        Process process = runtime.exec("%s container ls -a".formatted(dockerExe));
        Scanner scanner = new Scanner(process.getInputStream()).useDelimiter("\n");
        List<String> output = new ArrayList<>();
        int maxLength = 0;
        while (scanner.hasNext()) {
            String line = scanner.next();
            int length = line.length();
            if (maxLength < length) maxLength = length;
            output.add(line);
        }
        int exitCode = process.waitFor();
        process.destroy();
        int size = output.size();
        if (exitCode != 0 || size == 0) return null;
        if (size == 1) return new ArrayList<>();
        String header = output.get(0);
        output.remove(0);
        List<List<String>> result = new ArrayList<>();
        String[] columns = CONTAINER_LIST_COLUMNS.get();
        int columnAmount = columns.length;
        result.add(List.of(columns));
        List<Integer> columnIndices = new ArrayList<>();
        for (String column : columns) columnIndices.add(header.indexOf(column));
        List<String> row;
        for (String line : output) {
            row = new ArrayList<>();
            for (int i = 0; i < columnAmount; i++) {
                int to = (i == columnAmount - 1) ? Math.min(line.length(), maxLength) : columnIndices.get(i + 1);
                row.add(line.substring(columnIndices.get(i), to).trim());
            }
            result.add(row);
        }
        return result;
    }

    public void deleteContainer(String containerName) throws IOException, InterruptedException {
        List<List<String>> runningContainersTable = getContainersTable();
        if (runningContainersTable == null || runningContainersTable.isEmpty()) return;

        int containerNameColumnIndex = runningContainersTable.get(0).indexOf("NAMES");
        runningContainersTable.remove(0);

        String fullContainerName = null;
        for (List<String> row : runningContainersTable) {
            String contName = row.get(containerNameColumnIndex);
            if (contName.startsWith(containerName)) fullContainerName = contName;
        }
        if (fullContainerName == null) return;

        Process process1 = runtime.exec("%s stop %s".formatted(dockerExe, fullContainerName));
        process1.waitFor();
        process1.destroy();
        Process process2 = runtime.exec("%s rm %s".formatted(dockerExe, fullContainerName));
        process2.waitFor();
        process2.destroy();
        Process process3 = runtime.exec("%s image prune -af".formatted(dockerExe));
        process3.waitFor();
        process3.destroy();
    }

    public void followContainerLog(
            ProcessInfo processInfo,
            String containerName,
            ProcessRegistryService processRegistryService
    ) {
        processInfo.setStatus(ProcessStatus.DOCKER_RUNNING);
        new Thread(() -> {
            Process followContainerProcess;
            try {
                followContainerProcess = runtime.exec("%s logs -f %s".formatted(dockerExe, containerName));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Scanner followContainerScanner = new Scanner(
                    followContainerProcess.getInputStream()).useDelimiter("\n");
            while (followContainerScanner.hasNext()) {
                processInfo.saveLogLine(fileService.removeCarriageReturnInStringEnd(followContainerScanner.next()));
            }
            processInfo.saveLogLine("Приложение было остановлено");
            try {
                followContainerProcess.waitFor();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            followContainerProcess.destroy();
            if (processInfo.getStatus() != ProcessStatus.USER_INTERRUPT)
                processRegistryService.killProcess(processInfo, ProcessStatus.PROGRAM_FINISHED);
        }).start();
    }
}
