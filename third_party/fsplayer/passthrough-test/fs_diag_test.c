#include "fs_diag.h"

#include <stdio.h>
#include <string.h>

static int failed;

static void expect_int(const char *name, int got, int want)
{
    if (got != want) {
        fprintf(stderr, "FAIL %s got %d want %d\n", name, got, want);
        failed = 1;
    }
}

static void expect_has(const char *name, const char *line, const char *needle)
{
    if (!line || !needle || !strstr(line, needle)) {
        fprintf(stderr, "FAIL %s missing '%s' in '%s'\n", name, needle ? needle : "", line ? line : "");
        failed = 1;
    }
}

static void expect_lacks(const char *name, const char *line, const char *needle)
{
    if (line && needle && strstr(line, needle)) {
        fprintf(stderr, "FAIL %s unexpectedly has '%s'\n", name, needle);
        failed = 1;
    }
}

static void test_sleep_chain(void)
{
    FsDiagSleep st;
    char line[640];
    int produced;
    fs_diag_sleep_reset(&st);
    produced = fs_diag_sleep_observe(&st, 7, 1000000, FS_DIAG_SLEEP_BYTE_CAP,
                                     1000000, 0, 0, 40, 20000000, 28000000, 0,
                                     line, (int) sizeof(line));
    expect_int("first 10ms sleep is not a line", produced, 0);
    produced = fs_diag_sleep_observe(&st, 7, 1000000, FS_DIAG_SLEEP_BYTE_CAP,
                                     1010000, 0, 0, 30, 15000000, 28000000, 0,
                                     line, (int) sizeof(line));
    expect_int("chain does not log yet", produced, 0);
    produced = fs_diag_sleep_close(&st, 7, 1000000, 1020000, line, (int) sizeof(line));
    expect_int("20ms chain logs once", produced, 1);
    expect_has("reason", line, "reason=byte_cap");
    expect_has("sleeps", line, "sleeps=2");
    expect_has("span", line, "span_ms=20");
    expect_has("audio stayed empty", line, "audio_pkts=0->0");
    expect_has("video drained", line, "video_pkts=40->30");
    expect_has("session epoch", line, "epoch=session");
    expect_int("episode closed", st.active, 0);
}

static void test_short_sleep_dropped(void)
{
    FsDiagSleep st;
    char line[640];
    fs_diag_sleep_reset(&st);
    fs_diag_sleep_observe(&st, 1, 0, FS_DIAG_SLEEP_ENOUGH, 0,
                          1, 3080, 1, 1000, 5000000, 0, line, (int) sizeof(line));
    expect_int("under 20ms", fs_diag_sleep_close(&st, 1, 0, 10000, line, (int) sizeof(line)), 0);
}

static void test_reason_split(void)
{
    FsDiagSleep st;
    char line[640];
    int produced;
    fs_diag_sleep_reset(&st);
    fs_diag_sleep_observe(&st, 1, 0, FS_DIAG_SLEEP_BYTE_CAP, 0,
                          0, 0, 10, 100, 50, 0, line, (int) sizeof(line));
    fs_diag_sleep_observe(&st, 1, 0, FS_DIAG_SLEEP_BYTE_CAP, 30000,
                          0, 0, 10, 100, 50, 0, line, (int) sizeof(line));
    produced = fs_diag_sleep_observe(&st, 1, 0, FS_DIAG_SLEEP_ENOUGH, 40000,
                                     5, 1000, 10, 100, 50, 0, line, (int) sizeof(line));
    expect_int("reason change closes the chain", produced, 1);
    expect_has("closed reason", line, "reason=byte_cap");
    expect_has("both name", fs_diag_sleep_reason(FS_DIAG_SLEEP_BOTH), "both");
    expect_int("new episode is enough", st.reason, FS_DIAG_SLEEP_ENOUGH);
    expect_int("new episode one sleep", st.sleeps, 1);
}

static void test_gap_breaks_chain(void)
{
    FsDiagSleep st;
    char line[640];
    int produced;
    fs_diag_sleep_reset(&st);
    fs_diag_sleep_observe(&st, 1, 0, FS_DIAG_SLEEP_BYTE_CAP, 0,
                          0, 0, 1, 1, 1, 0, line, (int) sizeof(line));
    fs_diag_sleep_observe(&st, 1, 0, FS_DIAG_SLEEP_BYTE_CAP, 30000,
                          0, 0, 1, 1, 1, 0, line, (int) sizeof(line));
    produced = fs_diag_sleep_observe(&st, 1, 0, FS_DIAG_SLEEP_BYTE_CAP,
                                     30000 + FS_DIAG_SLEEP_CHAIN_GAP_US + 1,
                                     0, 0, 1, 1, 1, 0, line, (int) sizeof(line));
    expect_int("a gap is not one 10ms chain", produced, 1);
    expect_has("old sleeps", line, "sleeps=2");
}

static void test_counters(void)
{
    FsDiagAudio a;
    char line[1024];
    fs_diag_audio_reset(&a);
    fs_diag_audio_add_raw(&a, 3064);
    fs_diag_audio_add_frame(&a, 0, 1536);
    fs_diag_audio_add_frame(&a, 1, 1536);
    fs_diag_audio_add_orphan(&a, 800);
    fs_diag_audio_add_invalid(&a, 8);
    fs_diag_audio_add_seek_packet(&a, 3064);
    fs_diag_audio_add_seek_flush(&a, 3, 9000);
    fs_diag_audio_add_eof(&a);
    fs_diag_audio_add_burst(&a, 3072);
    fs_diag_audio_add_accepted(&a, 24576);
    fs_diag_audio_set_residual(&a, 16, 400);
    fs_diag_audio_set_pts(&a, 1234567890LL, 1, 4);
    expect_int("format", fs_diag_audio_format(&a, line, (int) sizeof(line), 2, 5000, "sec"), 1);
    expect_has("raw excludes the queue node", line, "raw_media_bytes=3064");
    expect_has("dequeue includes seek packet", line, "dequeued_bytes=6128");
    expect_has("pending gather", line, "residual_gather=16");
    expect_has("pending period", line, "residual_period=400");
    expect_has("orphan is its own reason", line, "skip_orphan_frames=1");
    expect_has("seek packet", line, "skip_seek_bytes=3064");
    expect_has("seek flush is not raw", line, "skip_seek_flush_bytes=9000");
    expect_has("seek reset counted", line, "seek_resets=1");
    expect_has("invalid", line, "skip_invalid_bytes=8");
    expect_has("eof", line, "skip_eof_packets=1");
    expect_has("pd", line, "pd_total=3072");
    expect_has("accepted is the whole write", line, "accepted_write_bytes=24576");
    expect_has("pts kept", line, "last_pts=1234567890");
    expect_lacks("does not claim raw equals pd", line, "raw_media_bytes=3072");

    fs_diag_audio_set_pts(&a, 0, 0, 4);
    fs_diag_audio_format(&a, line, (int) sizeof(line), 2, 5000, "stop");
    expect_has("nan pts", line, "last_pts=nan");
    expect_has("stop kind", line, "kind=stop");
    expect_has("totals survive a pts clear", line, "raw_media_bytes=3064");
}

static void test_clocks_and_read(void)
{
    char line[256];
    fs_diag_format_clocks(line, (int) sizeof(line), 0, 0, 1, 12000);
    expect_has("nan audio clock", line, "aud=nan");
    expect_has("video clock", line, "vid=12000");
    expect_has("nan diff", line, "diff=nan");
    fs_diag_format_clocks(line, (int) sizeof(line), 1, 1000, 1, 1800);
    expect_has("diff", line, "diff=800");
    expect_int("fast read is quiet",
               fs_diag_format_read(line, (int) sizeof(line), 1, 10, 49000, 0, "audio", 10), 0);
    expect_int("slow read logs",
               fs_diag_format_read(line, (int) sizeof(line), 1, 10, 50000, 0, "video", 180000), 1);
    expect_has("stream", line, "stream=video");
    expect_has("bytes", line, "bytes=180000");
    expect_lacks("no url", line, "http");
}

static void test_reader_sleep(void)
{
    const int64_t soft = 5 * 1024 * 1024;
    const int64_t hard = 50 * 1024 * 1024;
    const int64_t over = soft + 1024;
    expect_int("eight audio packets still sleep",
               fs_reader_should_sleep(0, 0, 0, 1, 1, 0, 8, over, soft, hard, 0), 1);
    expect_int("seven audio packets do not sleep under 50MB",
               fs_reader_should_sleep(0, 0, 0, 1, 1, 0, 7, over, soft, hard, 0), 0);
    expect_int("empty audio does not sleep on the soft cap",
               fs_reader_should_sleep(0, 0, 0, 1, 1, 0, 0, over, soft, hard, 0), 0);
    expect_int("50MB sleeps with an empty audio queue",
               fs_reader_should_sleep(0, 0, 0, 1, 1, 0, 0, hard, soft, hard, 0), 1);
    expect_int("pause keeps the soft cap",
               fs_reader_should_sleep(0, 0, 1, 1, 1, 0, 0, over, soft, hard, 0), 1);
    expect_int("finished audio keeps the soft cap",
               fs_reader_should_sleep(0, 0, 0, 1, 1, 1, 0, over, soft, hard, 0), 1);
    expect_int("no audio stream keeps the soft cap",
               fs_reader_should_sleep(0, 0, 0, 1, 0, 0, 0, over, soft, hard, 0), 1);
    expect_int("AAC soft cap still sleeps",
               fs_reader_should_sleep(0, 0, 0, 0, 1, 0, 0, over, soft, hard, 0), 1);
    expect_int("seek does not sleep",
               fs_reader_should_sleep(0, 1, 0, 1, 1, 0, 0, hard, soft, hard, 1), 0);
    expect_int("enough_packets still sleeps",
               fs_reader_should_sleep(0, 0, 0, 1, 1, 0, 0, soft, soft, hard, 1), 1);
    expect_int("under the soft cap does not sleep",
               fs_reader_should_sleep(0, 0, 0, 1, 1, 0, 0, soft, soft, hard, 0), 0);
    expect_int("infinite buffer does not sleep",
               fs_reader_should_sleep(1, 0, 0, 0, 1, 0, 0, over, soft, hard, 1), 0);
}

static void test_write_stamp(void)
{
    long long since = 0;
    fs_diag_publish_session(9, 1000000);
    expect_int("no write yet", fs_diag_since_write_end_ms(2000000, &since), 0);
    expect_int("sentinel", (int) since, -1);
    fs_diag_mark_write(1500000, 1600000, 24576);
    expect_int("have write", fs_diag_since_write_end_ms(2100000, &since), 1);
    expect_int("since end", (int) since, 500);
    expect_int("accepted global", (int) fs_diag_accepted_load(), 24576);
}

int main(void)
{
    test_sleep_chain();
    test_short_sleep_dropped();
    test_reason_split();
    test_gap_breaks_chain();
    test_counters();
    test_clocks_and_read();
    test_reader_sleep();
    test_write_stamp();
    if (failed) {
        fprintf(stderr, "fs_diag_test failed\n");
        return 1;
    }
    printf("fs_diag_test ok\n");
    return 0;
}
