package com.virtualkitchen.clip.render;

import com.virtualkitchen.common.jobs.PermanentJobFailure;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns raw station chunks into the customer clip (two FFmpeg passes):
 * <ol>
 *   <li>Concatenate the chunks, cut exactly the order's window, normalise to 1280×720 @ 30 fps.
 *       This intermediate file has a reliable duration (MediaRecorder WebM often has none).</li>
 *   <li>If longer than {@link #SPEEDUP_ABOVE_S}s: timelapse everything but the last
 *       {@link #NORMAL_TAIL_S}s (plating) to fit {@link #TARGET_S}s in total. Then burn the overlay
 *       and encode H.264 (High, yuv420p, CRF 23) with +faststart for instant playback.</li>
 * </ol>
 * Then a JPG thumbnail.
 */
@Component
public class ClipRenderer {

    static final double SPEEDUP_ABOVE_S = 50;
    static final double TARGET_S = 45;
    static final double NORMAL_TAIL_S = 5;
    private static final double MIN_FOOTAGE_S = 1;

    private final FfmpegRunner ffmpeg;

    public ClipRenderer(FfmpegRunner ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    /**
     * @param chunks       raw chunk files in recording order
     * @param offsetS      where the window starts, in seconds from the start of the first chunk
     * @param windowS      window length in seconds (cut short if the footage ends earlier)
     */
    public record Input(List<Path> chunks, double offsetS, double windowS, Path overlayPng, Path workDir) {
    }

    public record Output(Path video, Path thumbnail, double durationS) {
    }

    public Output render(Input in) {
        Path list = in.workDir().resolve("chunks.txt");
        Path cut = in.workDir().resolve("cut.mp4");
        Path clip = in.workDir().resolve("clip.mp4");
        Path thumb = in.workDir().resolve("thumb.jpg");
        writeConcatList(list, in.chunks());

        // Pass 1: concatenate + exact cut + normalise. trim is frame-accurate (decodes, no keyframe snapping).
        ffmpeg.ffmpeg(in.workDir(), List.of(
                "-fflags", "+genpts", "-f", "concat", "-safe", "0", "-i", list.toString(),
                "-filter_complex", "[0:v]trim=start=" + num(in.offsetS()) + ":duration=" + num(in.windowS())
                        + ",setpts=PTS-STARTPTS,fps=30,"
                        + "scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=black,"
                        + "setsar=1,format=yuv420p[v]",
                "-map", "[v]", "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "18", cut.toString()));
        double footage = ffmpeg.probeDuration(in.workDir(), cut);
        if (footage < MIN_FOOTAGE_S) {
            throw new PermanentJobFailure("NO_FOOTAGE: the uploaded chunks don't cover the order's window");
        }

        // Pass 2: optional timelapse + overlay + final encode.
        String graph;
        if (footage > SPEEDUP_ABOVE_S) {
            double main = footage - NORMAL_TAIL_S;
            double speed = main / (TARGET_S - NORMAL_TAIL_S);
            graph = "[0:v]split=2[a][b];"
                    + "[a]trim=end=" + num(main) + ",setpts=(PTS-STARTPTS)/" + num(speed) + "[fast];"
                    + "[b]trim=start=" + num(main) + ",setpts=PTS-STARTPTS[tail];"
                    + "[fast][tail]concat=n=2:v=1:a=0,fps=30[t];"
                    + "[t][1:v]overlay=0:0,format=yuv420p[out]";
        } else {
            graph = "[0:v][1:v]overlay=0:0,format=yuv420p[out]";
        }
        ffmpeg.ffmpeg(in.workDir(), List.of(
                "-i", cut.toString(), "-i", in.overlayPng().toString(),
                "-filter_complex", graph, "-map", "[out]", "-an",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-profile:v", "high", "-pix_fmt", "yuv420p",
                "-r", "30", "-movflags", "+faststart", clip.toString()));
        double duration = ffmpeg.probeDuration(in.workDir(), clip);

        // Thumbnail from ~40 % in: usually mid-cooking rather than an empty station.
        ffmpeg.ffmpeg(in.workDir(), List.of(
                "-ss", num(Math.max(0, Math.min(duration * 0.4, duration - 0.5))), "-i", clip.toString(),
                "-frames:v", "1", "-update", "1", "-vf", "scale=640:-2", "-q:v", "3", thumb.toString()));
        return new Output(clip, thumb, duration);
    }

    /** Converts any logo the browser could upload (PNG/JPEG/WebP) into a small PNG Java can read. */
    public Path normaliseLogo(Path source, Path workDir) {
        Path png = workDir.resolve("logo.png");
        ffmpeg.ffmpeg(workDir, List.of("-i", source.toString(), "-vf", "scale=192:192:force_original_aspect_ratio=decrease",
                "-frames:v", "1", "-update", "1", png.toString()));
        return png;
    }

    private static void writeConcatList(Path list, List<Path> chunks) {
        List<String> lines = new ArrayList<>();
        for (Path chunk : chunks) {
            lines.add("file '" + chunk.toAbsolutePath().toString().replace("'", "'\\''") + "'");
        }
        try {
            Files.write(list, lines);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String num(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
