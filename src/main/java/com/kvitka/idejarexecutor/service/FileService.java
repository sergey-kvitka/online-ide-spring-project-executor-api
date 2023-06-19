package com.kvitka.idejarexecutor.service;

import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileService {

    public final String backSlash = "\\";

    public void createFile(String projectPath, String contentPath, List<String> content) throws IOException {
        ArrayList<String> packages = new ArrayList<>(List.of(contentPath.split("\\\\")));
        int size = packages.size();
        packages.remove(size - 1);
        if (size != 1) Files.createDirectories(Paths.get(join(projectPath, String.join(backSlash, packages))));

        File file = new File(join(projectPath, contentPath));
        try (
                FileWriter fileWriter = new FileWriter(file.getAbsolutePath());
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)
        ) {
            for (String line : content) {
                bufferedWriter.write(line);
                bufferedWriter.newLine();
            }
        }
    }

    public String join(String... strings) {
        return String.join(backSlash, strings);
    }

    public String removeCarriageReturnInStringEnd(String string) {
        StringBuilder line = new StringBuilder(string);
        int length = line.length() - 1;
        if (length > -1 && line.charAt(length) == '\r') line.deleteCharAt(length);
        return line.toString();
    }
}
