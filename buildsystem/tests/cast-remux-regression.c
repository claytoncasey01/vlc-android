/* Exercise the production initial-DTS helper against actual reordered HEVC
 * packets and libavformat's Matroska writer. Usage: test input.mkv output.mkv
 * "legacy" deliberately uses VLC's old MKV DTS estimate to expose frame loss.
 */
#ifdef NDEBUG
#undef NDEBUG
#endif
#include <assert.h>
#include <inttypes.h>
#include <stdio.h>
#include <string.h>
#include <libavformat/avformat.h>
#include <libavutil/mathematics.h>
#include "matroska_dts.h"

int main(int argc, char **argv)
{
    assert(argc == 3 || argc == 4);
    const int legacy = argc == 4 && !strcmp(argv[3], "legacy");
    const int seek = argc == 4 && !strcmp(argv[3], "seek");
    AVFormatContext *input = NULL, *output = NULL;
    assert(avformat_open_input(&input, argv[1], NULL, NULL) >= 0);
    assert(avformat_find_stream_info(input, NULL) >= 0);
    const int index = av_find_best_stream(input, AVMEDIA_TYPE_VIDEO, -1, -1, NULL, 0);
    assert(index >= 0);
    AVStream *source = input->streams[index];
    assert(source->codecpar->codec_id == AV_CODEC_ID_HEVC);
    assert(source->codecpar->video_delay > 0);
    if (seek)
        assert(av_seek_frame(input, index, av_rescale_q(3, (AVRational){1, 1},
                    source->time_base), AVSEEK_FLAG_BACKWARD) >= 0);
    assert(avformat_alloc_output_context2(&output, NULL, "matroska", argv[2]) >= 0);
    AVStream *target = avformat_new_stream(output, NULL);
    assert(target);
    assert(avcodec_parameters_copy(target->codecpar, source->codecpar) >= 0);
    /* VLC's avformat muxer does not copy the codec's reorder delay. Supply
     * actual decode times, rather than relying on its writer to infer them. */
    target->codecpar->video_delay = 0;
    target->time_base = source->time_base;
    output->avoid_negative_ts = AVFMT_AVOID_NEG_TS_MAKE_ZERO;
    assert(avio_open(&output->pb, argv[2], AVIO_FLAG_WRITE) >= 0);
    AVDictionary *options = NULL;
    av_dict_set(&options, "live", "1", 0);
    assert(avformat_write_header(output, &options) >= 0);
    av_dict_free(&options);
    AVPacket *packet = av_packet_alloc();
    assert(packet);
    int read = 0, written = 0, errors = 0, initial = 0, backward_pts = 0;
    int64_t previous = INT64_MIN, previous_pts = INT64_MIN;
    while (av_read_frame(input, packet) >= 0) {
        if (packet->stream_index != index) { av_packet_unref(packet); continue; }
        ++read;
        assert(packet->pts != AV_NOPTS_VALUE);
        if (previous_pts != INT64_MIN && packet->pts < previous_pts) ++backward_pts;
        previous_pts = packet->pts;
        if (legacy) {
            /* Native MKV BlockDecode uses PTS for keyframes and bounds later
             * decode timestamps by the previous DTS plus frame duration. */
            packet->dts = packet->pts;
            if (!(packet->flags & AV_PKT_FLAG_KEY) && previous != INT64_MIN)
                packet->dts = FFMIN(packet->pts, previous + packet->duration);
        } else {
            if (packet->dts == AV_NOPTS_VALUE) ++initial;
            const int64_t original = packet->dts;
            packet->dts = vlc_matroska_initial_dts(packet->pts, packet->dts,
                    previous, packet->duration, source->codecpar->video_delay);
            assert(packet->dts != AV_NOPTS_VALUE);
            if (original != AV_NOPTS_VALUE) assert(packet->dts == original);
            assert(previous == INT64_MIN || packet->dts >= previous);
            assert(packet->dts <= packet->pts);
        }
        previous = packet->dts;
        /* Production keeps the raw decode time above, then clips leading DTS
         * to VLC_TICK_0. This is zero in the Matroska writer's time base. */
        if (!legacy) packet->dts = FFMAX(packet->dts, 0);
        av_packet_rescale_ts(packet, source->time_base, target->time_base);
        packet->stream_index = 0;
        const int result = av_write_frame(output, packet);
        if (result < 0) ++errors; else ++written;
        av_packet_unref(packet);
    }
    assert(read > 0 && backward_pts > 0);
    if (legacy) assert(errors > 0);
    else assert(initial > 0 && errors == 0 && read == written);
    assert(av_write_trailer(output) >= 0);
    av_packet_free(&packet);
    avio_closep(&output->pb);
    avformat_free_context(output);
    avformat_close_input(&input);
    printf("%s: read=%d written=%d errors=%d initial-DTS=%d reordered-PTS=%d\n",
            legacy ? "legacy MKV estimate" : seek ? "avformat after seek" : "avformat with initial DTS", read,
            written, errors, initial, backward_pts);
    return 0;
}
