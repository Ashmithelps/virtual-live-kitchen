package com.virtualkitchen.clip.render;

import com.virtualkitchen.clip.ClipProperties;
import com.virtualkitchen.common.jobs.PermanentJobFailure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The FFmpeg pipeline on two committed 30 s sample chunks (src/test/resources/clips): chunk 1 is
 * black, chunk 2 blue, so frames tell us which chunk (and which part of the window) we're seeing.
 * Needs ffmpeg/ffprobe on PATH (the backend Docker image has them); skipped otherwise.
 */
class ClipRendererTest {

    @TempDir Path work;
    ClipRenderer renderer;
    FfmpegRunner ffmpeg;
    List<Path> chunks;
    Path overlayPng;

    @BeforeAll
    static void requireFfmpeg() {
        assumeTrue(onPath("ffmpeg") && onPath("ffprobe"), "ffmpeg/ffprobe not installed");
    }

    @BeforeEach
    void setUp() throws Exception {
        ClipProperties properties = new ClipProperties(Duration.ofSeconds(10), Duration.ofSeconds(5), Duration.ofHours(2), 50_000_000,
                Duration.ofMinutes(10), Duration.ofDays(30), Duration.ofHours(24), "ffmpeg", "ffprobe", Duration.ofMinutes(2), "Virtual Live Kitchen",
                Duration.ofMinutes(10));
        ffmpeg = new FfmpegRunner(properties);
        renderer = new ClipRenderer(ffmpeg);
        chunks = List.of(resource("chunk-1.webm"), resource("chunk-2.webm"));
        overlayPng = work.resolve("overlay.png");
        new ClipOverlayRenderer().render(new ClipOverlayRenderer.Overlay("Spice Garden", null, 142, "Ravi", "11 Oct 2026",
                "Virtual Live Kitchen"), overlayPng);
    }

    @Test
    void shortWindowIsCutExactlyAtNormalSpeed() throws Exception {
        // 20 s starting 5 s into chunk 1: all black.
        ClipRenderer.Output out = renderer.render(new ClipRenderer.Input(chunks, 5, 20, overlayPng, work));

        assertThat(out.durationS()).isCloseTo(20.0, org.assertj.core.data.Offset.offset(0.2));
        assertMp4(out.video());
        assertThat(frameAt(out.video(), 10).getRGB(640, 360) & 0xFFFFFF).as("middle of chunk 1 is black").isLessThan(0x202020);
    }

    @Test
    void longWindowBecomesA45SecondTimelapseEndingInRealTimeAcrossBothChunks() throws Exception {
        // 58 s from 1 s into chunk 1 to 29 s into chunk 2 (> 50 s): sped up to 45 s, last 5 s at 1×.
        ClipRenderer.Output out = renderer.render(new ClipRenderer.Input(chunks, 1, 58, overlayPng, work));

        assertThat(out.durationS()).isCloseTo(45.0, org.assertj.core.data.Offset.offset(0.3));
        assertMp4(out.video());
        int first = frameAt(out.video(), 0.5).getRGB(640, 360);
        int last = frameAt(out.video(), 44.5).getRGB(640, 360);
        assertThat(blue(first)).as("starts in the black chunk").isLessThan(40);
        assertThat(blue(last)).as("ends in the blue chunk").isGreaterThan(120);
        // The final 5 s are untouched footage: 5 s before the end is still the blue chunk (it starts 29 s before the end).
        assertThat(blue(frameAt(out.video(), 40.5).getRGB(640, 360))).isGreaterThan(120);
    }

    @Test
    void overlayIsBurnedIn() throws Exception {
        ClipRenderer.Output out = renderer.render(new ClipRenderer.Input(chunks, 5, 10, overlayPng, work));
        BufferedImage frame = frameAt(out.video(), 5);
        // Black footage, so any bright pixels in the text areas come from the overlay.
        assertThat(brightPixels(frame, 40, 600, 600, 680)).as("restaurant name / order line").isGreaterThan(500);
        assertThat(brightPixels(frame, 1000, 20, 1260, 60)).as("watermark").isGreaterThan(100);
        assertThat(brightPixels(frame, 400, 200, 880, 500)).as("middle stays clear").isZero();

        BufferedImage thumb = ImageIO.read(out.thumbnail().toFile());
        assertThat(thumb.getWidth()).isEqualTo(640);
        assertThat(thumb.getHeight()).isEqualTo(360);
    }

    @Test
    void windowWithoutFootageIsAPermanentFailure() {
        assertThatThrownBy(() -> renderer.render(new ClipRenderer.Input(chunks, 120, 15, overlayPng, work)))
                .isInstanceOf(PermanentJobFailure.class)
                .hasMessageStartingWith("NO_FOOTAGE");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void assertMp4(Path video) throws IOException {
        Path probe = work.resolve("probe.txt");
        run(List.of("ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=codec_name,profile,width,height,pix_fmt",
                "-of", "default=noprint_wrappers=1", video.toString()), probe);
        String info = Files.readString(probe);
        assertThat(info).contains("codec_name=h264", "width=1280", "height=720", "pix_fmt=yuv420p", "profile=High");
        // +faststart: the index (moov) comes before the media data (mdat), so playback starts immediately.
        String head = new String(Files.readAllBytes(video), StandardCharsets.ISO_8859_1);
        assertThat(head.indexOf("moov")).isPositive().isLessThan(head.indexOf("mdat"));
    }

    private BufferedImage frameAt(Path video, double seconds) throws IOException {
        Path png = work.resolve("frame-" + seconds + ".png");
        run(List.of("ffmpeg", "-v", "error", "-y", "-ss", String.valueOf(seconds), "-i", video.toString(), "-frames:v", "1", "-update", "1", png.toString()), null);
        return ImageIO.read(png.toFile());
    }

    private static int brightPixels(BufferedImage image, int x0, int y0, int x1, int y1) {
        int count = 0;
        for (int y = y0; y < y1; y++)
            for (int x = x0; x < x1; x++) {
                int rgb = image.getRGB(x, y);
                if (((rgb >> 16) & 0xFF) > 200 && ((rgb >> 8) & 0xFF) > 200 && (rgb & 0xFF) > 200) count++;
            }
        return count;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }

    private static void run(List<String> command, Path output) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
            pb.redirectOutput(output == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(output.toFile()));
            assertThat(pb.start().waitFor()).isZero();
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(ClipRendererTest.class.getResource("/clips/" + name).toURI());
    }

    static boolean onPath(String binary) {
        try {
            return new ProcessBuilder(binary, "-version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
