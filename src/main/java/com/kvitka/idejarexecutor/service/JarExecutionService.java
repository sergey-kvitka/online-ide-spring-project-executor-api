package com.kvitka.idejarexecutor.service;

import com.kvitka.idejarexecutor.dto.ExecutionRequest;
import com.kvitka.idejarexecutor.dto.ExecutionResult;
import com.kvitka.idejarexecutor.dto.FileDto;
import com.kvitka.idejarexecutor.dto.JarExecutionResult;
import com.kvitka.idejarexecutor.enums.ResultStatus;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JarExecutionService {

    private static final String PROJECT_PATH = System.getProperty("user.dir");
    private static final String EXEC_PROJECTS_DIR = "src\\execProjects";

    private static final String MANIFEST_NAME = "manifest.mf";

    private final FileService fileService;

    public ExecutionResult exec(ExecutionRequest execRequest) throws IOException, InterruptedException {
        String projectId = execRequest.getProjectId();
        Path projectDir = createProjectDir(projectId);
        clearProjectDir(projectDir.toFile());
        String projectPath = projectDir.toString();

        List<FileDto> files = execRequest.getFiles();
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("No java files");
        String projectRootPackageName = null;
        String[] packages;
        String path;
        for (FileDto file : files) {
            path = file.getPath();
            packages = path.split("\\\\");
            int length = packages.length;
            if (length < 2) throw new IllegalArgumentException(
                    "file.path must contain java file name and at least 1 package name " +
                            "(example: \"com\\Test.java\"), got " + path);
            if (projectRootPackageName == null) projectRootPackageName = packages[0];
            else if (!projectRootPackageName.equals(packages[0])) throw new IllegalArgumentException(
                    "All java files must be located in one top-level package (e.g. \"com\")");

            fileService.createFile(projectPath, path, file.getContent());
        }

        List<String> compilationErrors = compileJavaFiles(projectPath);
        if (!compilationErrors.isEmpty()) return new ExecutionResult(0, false,
                ResultStatus.COMPILE_ERROR, compilationErrors, 0L);

        createManifest(projectPath, execRequest.getMainClass());
        createJar(projectPath, projectRootPackageName, projectId);
        JarExecutionResult result = executeJar(projectPath, projectId, execRequest.getArgs());

        try {
            clearProjectDir(projectDir.toFile());
        } catch (IOException ignored) {
        }
        int exitCode = result.getExitCode();
        return new ExecutionResult(
                exitCode,
                true,
                exitCode == 0 ? ResultStatus.OK : ResultStatus.EXECUTION_ERROR,
                result.getOutput(),
                result.getExecutionTime());
    }

    private void clearProjectDir(File projectDir) throws IOException {
        FileUtils.cleanDirectory(projectDir);
    }

    private Path createProjectDir(String projectId) throws IOException {
        return Files.createDirectories(Paths.get(
                fileService.join(PROJECT_PATH, EXEC_PROJECTS_DIR, "project_" + projectId
                        + '_' + UUID.randomUUID())));
    }

    private List<String> compileJavaFiles(String projectPath) throws IOException, InterruptedException {
        File projectPathDirFile = new File(projectPath);
        Runtime runtime = Runtime.getRuntime();
        Process javaFilesPathCollectingProcess = runtime.exec(
                "cmd /c dir /s /B *.java > javaFiles.txt", null, projectPathDirFile);
        javaFilesPathCollectingProcess.waitFor();
        javaFilesPathCollectingProcess.destroy();
        Process javaFilesCompilationProcess = runtime.exec(
                "javac @javaFiles.txt", null, projectPathDirFile);
        InputStream errorStream = javaFilesCompilationProcess.getErrorStream();
        Scanner scanner = new Scanner(errorStream).useDelimiter("\n");
        List<String> result = new ArrayList<>();
        while (scanner.hasNext()) result.add(removeCarriageReturnInStringEnd(scanner.next()));
        javaFilesCompilationProcess.waitFor();
        javaFilesCompilationProcess.destroy();
        return result;
    }

    private void createManifest(String projectPath, String mainClass) throws IOException {
        File manifest = new File(fileService.join(projectPath, MANIFEST_NAME));
        String manifestPath = manifest.getAbsolutePath();
        BufferedWriter writer = new BufferedWriter(new FileWriter(manifestPath));
        for (String line : new String[]{"Manifest-Version: 1.0", "Main-Class: %s".formatted(mainClass)}) {
            writer.write(line);
            writer.newLine();
        }
        writer.close();
    }

    private void createJar(String projectPath, String projectRootPackageName,
                           String projectId) throws IOException, InterruptedException {
        Process process = Runtime.getRuntime().exec(
                "jar cfm project_%s.jar %s %s".formatted(projectId, MANIFEST_NAME, projectRootPackageName),
                null, new File(projectPath));
        process.waitFor();
        process.destroy();
    }

    private JarExecutionResult executeJar(String projectPath, String projectId,
                                          List<String> arguments) throws IOException, InterruptedException {
        if (arguments == null) arguments = new ArrayList<>();
        String[] args = arguments.toArray(new String[0]);
        int argSize = arguments.size();
        String[] command = new String[3 + argSize];
        int n = 0;
        command[n++] = "java";
        command[n++] = "-jar";
        command[n++] = "project_%s.jar".formatted(projectId);
        System.arraycopy(args, 0, command, n, argSize);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(projectPath));
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        long start = System.nanoTime();

        InputStream inputStream = process.getInputStream();
        Scanner scanner = new Scanner(inputStream).useDelimiter("\n");
        List<String> output = new ArrayList<>();
        while (scanner.hasNext()) output.add(removeCarriageReturnInStringEnd(scanner.next()));
        scanner.close();
        int status = process.waitFor();
        long end = System.nanoTime();
        process.destroy();
        return new JarExecutionResult(status, output, (end - start) / 1_000_000);
    }

    private String removeCarriageReturnInStringEnd(String string) {
        StringBuilder line = new StringBuilder(string);
        int length = line.length() - 1;
        if (length > -1 && line.charAt(length) == '\r') line.deleteCharAt(length);
        return line.toString();
    }
}
