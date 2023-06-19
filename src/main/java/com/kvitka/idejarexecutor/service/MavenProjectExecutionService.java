package com.kvitka.idejarexecutor.service;

import com.kvitka.idejarexecutor.dto.FileDto;
import com.kvitka.idejarexecutor.dto.MavenExecutionRequestDto;
import com.kvitka.idejarexecutor.enums.ProcessStatus;
import com.kvitka.idejarexecutor.model.ProcessInfo;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class MavenProjectExecutionService {

    private static final String USER_DIR = System.getProperty("user.dir");

    @Value("${path.projects}")
    private String execProjectsDir;

    @Value("${path.maven.3-8-4}")
    private String maven3_8_4;

    @Value("${path.docker.docker-compose}")
    private String dockerComposeExe;

    private final FileService fileService;
    private final ProcessRegistryService processRegistryService;
    private final DockerService dockerService;

    private final Runtime runtime;

    public void startMavenProjectProcess(MavenExecutionRequestDto mavenExecutionRequestDto, ProcessInfo processInfo) {
        String artifactId = mavenExecutionRequestDto.getArtifactId();

        String projectPath;
        String mavenProjectPath;

        List<FileDto> files = mavenExecutionRequestDto.getFiles();
        try {
            processInfo.setStatus(ProcessStatus.PROJECT_FILES_CREATING);
            projectPath = createMavenProjectTemplate(processInfo);
            mavenProjectPath = fileService.join(projectPath, artifactId);
            for (FileDto file : files) {
                fileService.createFile(mavenProjectPath, file.getPath(), file.getContent());
            }
            processInfo.setStatus(ProcessStatus.PROJECT_FILES_CREATED);
        } catch (IOException | InterruptedException e) {
            processRegistryService.killProcess(processInfo, ProcessStatus.PROJECT_FILES_CREATING_FAILED);
            throw new RuntimeException(e);
        }

        try {
            mavenCleanPackage(mavenProjectPath, processInfo);
        } catch (IOException | InterruptedException e) {
            processRegistryService.killProcess(processInfo, ProcessStatus.MAVEN_JAR_CREATING_FAILED);
            throw new RuntimeException(e);
        }

        String deployPort = "127.%d.%d.%d:%d".formatted(
                ThreadLocalRandom.current().nextInt(1, 256),
                ThreadLocalRandom.current().nextInt(1, 255),
                ThreadLocalRandom.current().nextInt(2, 256),
                ThreadLocalRandom.current().nextInt(12000, 50001)
        );

        try {
            deployJarInDocker(mavenProjectPath, deployPort, mavenExecutionRequestDto.getApplicationPort(), processInfo);
        } catch (IOException | InterruptedException e) {
            processRegistryService.killProcess(processInfo, ProcessStatus.DOCKER_DEPLOYING_FAILED);
            throw new RuntimeException(e);
        }
    }

    private String createMavenProjectTemplate(ProcessInfo processInfo) throws IOException, InterruptedException {
//        processInfo.setStatus(ProcessStatus.MAVEN_TEMPLATE_CREATING);
        String projectPath = Files.createDirectories(Paths.get(
                fileService.join(USER_DIR, execProjectsDir, "project_" + processInfo.getProjectUUID())
        )).toAbsolutePath().toString();
        FileUtils.cleanDirectory(new File(projectPath));

        //noinspection ResultOfMethodCallIgnored
        new File(fileService.join(projectPath, "src")).mkdirs();

//        processInfo.setStatus(ProcessStatus.MAVEN_TEMPLATE_CREATED);
        return projectPath;
    }

    private void mavenCleanPackage(String mavenProjectPath, ProcessInfo processInfo)
            throws IOException, InterruptedException {
        processInfo.setStatus(ProcessStatus.MAVEN_JAR_CREATING);
        String mavenCleanPackageCommand = "%s\\%s --file \"%s\" clean package".formatted(
                USER_DIR, maven3_8_4, fileService.join(mavenProjectPath, "pom.xml"));
        Process process = runtime.exec(mavenCleanPackageCommand);
        Scanner scanner = new Scanner(process.getInputStream()).useDelimiter("\n");
        processInfo.saveLogLine("");
        processInfo.saveLogLine("-----------------------------");
        processInfo.saveLogLine("            MAVEN");
        processInfo.saveLogLine("-----------------------------");
        processInfo.saveLogLine("");
        while (scanner.hasNext()) {
            processInfo.saveLogLine(fileService.removeCarriageReturnInStringEnd(scanner.next()));
        }
        int exitCode = process.waitFor();
        process.destroy();
        if (exitCode != 0) throw new IOException("Ошибка выполнения команды mvn clean package");

        File targetFolder = new File(fileService.join(mavenProjectPath, "target"));
        if (!targetFolder.exists())
            throw new IOException("Ошибка выполнения команды mvn clean package: папка target не была создана");

        File[] jarFiles = targetFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
        if (jarFiles == null || jarFiles.length != 1)
            throw new IOException("Ошибка выполнения команды mvn clean package: JAR-файл в папке target отсутствует");
        processInfo.setStatus(ProcessStatus.MAVEN_JAR_CREATED);
    }

    private void deployJarInDocker(
            String mavenProjectPath,
            String deployPort,
            String applicationPort,
            ProcessInfo processInfo) throws IOException, InterruptedException {

        processInfo.setStatus(ProcessStatus.DOCKER_FILES_CREATING);

        File dockerfile = new File(fileService.join(mavenProjectPath, "Dockerfile"));
        try (
                FileWriter fileWriter = new FileWriter(dockerfile.getAbsolutePath());
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)
        ) {
            for (String line : new String[]{
                    "FROM eclipse-temurin:17-jdk-alpine",
                    "ADD target/*.jar app.jar",
                    "ENTRYPOINT [\"java\", \"-jar\", \"/app.jar\"]"
            }) {
                bufferedWriter.write(line);
                bufferedWriter.newLine();
            }
        }

        File dockerComposeFile = new File(fileService.join(mavenProjectPath, "docker-compose.yaml"));
        String dockerComposeAbsolutePath = dockerComposeFile.getAbsolutePath();
        String containerName = "%s%s_%s".formatted(
                dockerService.containerNamePrefix,
                processInfo.getProjectUUID(),
                UUID.randomUUID().toString());
        try (
                FileWriter fileWriter = new FileWriter(dockerComposeAbsolutePath);
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)
        ) {
            for (String line : new String[]{
                    "services:",
                    "  %s:".formatted(containerName),
                    "    build: .",
                    "    container_name: %s".formatted(containerName),
                    "    ports:",
                    "      - \"%s:%s\"".formatted(deployPort, applicationPort)
            }) {
                bufferedWriter.write(line);
                bufferedWriter.newLine();
            }
        }

        processInfo.setStatus(ProcessStatus.DOCKER_DEPLOYING);
        String dockerComposeCommand = "%s -f %s up -d".formatted(dockerComposeExe, dockerComposeAbsolutePath);
        Process process = runtime.exec(dockerComposeCommand);
        Scanner scanner = new Scanner(process.getInputStream()).useDelimiter("\n");
        processInfo.saveLogLine("");
        processInfo.saveLogLine("-----------------------------");
        processInfo.saveLogLine("           DOCKER");
        processInfo.saveLogLine("-----------------------------");
        processInfo.saveLogLine("");
        while (scanner.hasNext()) {
            processInfo.saveLogLine(fileService.removeCarriageReturnInStringEnd(scanner.next()));
        }
        int exitCode = process.waitFor();
        process.destroy();
        if (exitCode != 0) throw new IOException("Ошибка выполнения команды docker compose up -d");

        if (processInfo.getStatus().isFinished()) {
            dockerService.deleteContainer(containerName);
        }
        processInfo.setDeployPort(deployPort);

        dockerService.followContainerLog(processInfo, containerName, processRegistryService);
    }
}
