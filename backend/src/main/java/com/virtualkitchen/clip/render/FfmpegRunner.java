package com.virtualkitchen.clip.render;

import com.virtualkitchen.clip.ClipProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs ffmpeg/ffprobe as child processes with a hard timeout. Output goes to a log file in the
 * work directory (never a pipe, which could fill up and stall the process).
 */
@Component
public class FfmpegRunner {

    private final ClipProperties properties;
    private final AtomicInteger runs = new AtomicInteger();

    public FfmpegRunner(ClipProperties properties) {
        this.properties = properties;
    }

    public void ffmpeg(Path workDir, List<String> args) {
        List<String> command = new ArrayList<>(List.of(properties.ffmpegPath(), "-hide_banner", "-nostdin", "-y", "-loglevel", "warning"));
        command.addAll(args);
        run(workDir, command, properties.ffmpegTimeout());
    }

    /** Container duration in seconds; 0 when ffprobe reports none (e.g. "N/A" for an empty output). */
    public double probeDuration(Path workDir, Path media) {
        Path out = run(workDir, List.of(properties.ffprobePath(), "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", media.toString()), Duration.ofSeconds(60));
        String first;
        try {
            first = Files.readString(out).lines().map(String::strip).filter(l -> !l.isEmpty()).findFirst().orElse("");
        } catch (IOException e) {
            throw new FfmpegException("Could not read duration of " + media.getFileName() + ": " + e.getMessage());
        }
        try {
            return Double.parseDouble(first);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Path run(Path workDir, List<String> command, Duration timeout) {
        Path log = workDir.resolve("run-" + runs.incrementAndGet() + ".log");
        Process process;
        try {
            process = new ProcessBuilder(command).directory(workDir.toFile())
                    .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        } catch (IOException e) {
            throw new FfmpegException("Could not start " + command.getFirst() + " (is FFmpeg installed?): " + e.getMessage());
        }
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new FfmpegException(command.getFirst() + " timed out after " + timeout);
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new FfmpegException(command.getFirst() + " interrupted");
        }
        if (process.exitValue() != 0) {
            throw new FfmpegException(command.getFirst() + " exited with " + process.exitValue() + ": " + tail(log));
        }
        return log;
    }

    private static String tail(Path log) {
        try {
            List<String> lines = Files.readAllLines(log, StandardCharsets.UTF_8);
            return String.join(" | ", lines.subList(Math.max(0, lines.size() - 8), lines.size()));
        } catch (IOException e) {
            return "(no log)";
        }
    }
}
