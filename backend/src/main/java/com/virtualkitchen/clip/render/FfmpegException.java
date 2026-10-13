package com.virtualkitchen.clip.render;

/** FFmpeg exited non-zero or timed out; the message carries the tail of its log. */
public class FfmpegException extends RuntimeException {

    public FfmpegException(String message) {
        super(message);
    }
}
