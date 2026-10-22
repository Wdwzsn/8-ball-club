package com.pool8.tools;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

/**
 * 不依赖 JDK 自带 jar.exe 的打包工具（部分 JetBrains Runtime 不带 jar 命令）。
 * 用法：java -cp <classes> com.pool8.tools.JarBuilder <out.jar> <classesDir> <mainClass>
 */
public final class JarBuilder {
    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: JarBuilder <out.jar> <classesDir> <mainClass>");
            System.exit(2);
        }
        Path out = Path.of(args[0]);
        Path classes = Path.of(args[1]);
        String main = args[2];
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(out.toFile()))) {
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            String mf = "Manifest-Version: 1.0\r\nMain-Class: " + main + "\r\nCreated-By: 8-BALL CLUB build\r\n\r\n";
            jos.write(mf.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            try (Stream<Path> walk = Files.walk(classes)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    String rel = classes.relativize(p).toString().replace('\\', '/');
                    if (rel.startsWith("com/pool8/tools/")) return;
                    try {
                        jos.putNextEntry(new JarEntry(rel));
                        jos.write(Files.readAllBytes(p));
                        jos.closeEntry();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        System.out.println("jar: " + out.toAbsolutePath() + " main=" + main);
    }
}
