#!/usr/bin/env bash
set -euo pipefail
: "${CAST_FFMPEG_ROOT:?Set CAST_FFMPEG_ROOT to a built FFmpeg 4.4.5 source directory}"
: "${CAST_VLC_ROOT:?Set CAST_VLC_ROOT to the patched VLC source directory}"
cast_test_dir=$(mktemp -d)
trap 'rm -rf "$cast_test_dir"' EXIT
python3 - "$cast_test_dir" <<'PY'
import pathlib, subprocess, sys
root = pathlib.Path(sys.argv[1])
(root / 'english.ass').write_text('''[Script Info]
ScriptType: v4.00+
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Default,Arial,20,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,10,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
Dialogue: 0,0:00:01.00,0:00:03.00,Default,,0,0,0,,{\\i1}Hello, world!{\\i0}\\N你好 👋
Dialogue: 0,0:00:04.00,0:00:05.50,Default,,0,0,0,,Later
''', encoding='utf-8')
(root / 'other.srt').write_text('1\n00:00:02,000 --> 00:00:03,500\nOther track\n\n', encoding='utf-8')
def run(*args):
    subprocess.run(['ffmpeg', '-v', 'error', '-y', *args], check=True)
run('-f', 'lavfi', '-i', 'color=size=64x64:rate=5:duration=6', '-i', str(root/'english.ass'), '-i', str(root/'other.srt'),
    '-map', '0:v', '-map', '1:s', '-map', '2:s', '-c:v', 'mpeg4', '-c:s', 'copy',
    '-metadata:s:s:0', 'title=English ASS', '-metadata:s:s:0', 'language=eng', str(root/'embedded.mkv'))
run('-f', 'lavfi', '-i', 'color=size=64x64:rate=5:duration=6', '-i', str(root/'other.srt'),
    '-map', '0:v', '-map', '1:s', '-c:v', 'mpeg4', '-c:s', 'mov_text', str(root/'embedded.mp4'))
PY
g++ -std=c++11 -Wall -Wextra -Werror -fsanitize=address,undefined -fno-omit-frame-pointer \
    -I"$CAST_VLC_ROOT/modules/misc" -I"$CAST_FFMPEG_ROOT" \
    "$CAST_VLC_ROOT/test/modules/stream_out/embedded_cast_subtitles.cpp" \
    "$CAST_FFMPEG_ROOT/libavformat/libavformat.a" \
    "$CAST_FFMPEG_ROOT/libavcodec/libavcodec.a" \
    "$CAST_FFMPEG_ROOT/libavutil/libavutil.a" \
    -pthread -lm -o "$cast_test_dir/check"
"$cast_test_dir/check" "$cast_test_dir/embedded.mkv" "$cast_test_dir/embedded.mp4"
