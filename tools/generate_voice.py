"""Generate short original character utterances; run with build/audio-tools Python.

Requires edge-tts, imageio-ffmpeg and soundfile in the isolated build environment.
The voice is a generic synthetic voice, not an imitation of an identifiable person.
"""
import asyncio
import json
from pathlib import Path
import subprocess
import edge_tts
import imageio_ffmpeg
import soundfile

ROOT=Path(__file__).resolve().parents[1]
VOICE='zh-CN-XiaoyiNeural'
LINES={
    'idle_1':'哼哼。',
    'idle_2':'嘿嘿。',
    'beg_1':'我的饭呢？',
    'beg_2':'该开饭啦！',
    'eat_1':'好吃！',
    'eat_2':'嗯，真香。',
    'hurt_1':'呀！',
    'hurt_2':'好痛！',
    'death_1':'呜……',
}

async def main():
    cache=ROOT/'build/voice';cache.mkdir(parents=True,exist_ok=True)
    target=ROOT/'src/main/resources/assets/bigfatfish/sounds/entity/big_fat_fish';target.mkdir(parents=True,exist_ok=True)
    report={}
    for name,text in LINES.items():
        source=cache/f'{name}.mp3'
        if not source.exists():
            await edge_tts.Communicate(text,VOICE,rate='+8%',pitch='+12Hz').save(str(source))
        output=target/f'{name}.ogg'
        subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-y','-loglevel','error','-i',str(source),
            '-af','silenceremove=start_periods=1:start_threshold=-45dB,loudnorm=I=-20:TP=-3:LRA=7',
            '-ac','1','-ar','24000','-c:a','libvorbis','-q:a','4',str(output)],check=True)
        data,rate=soundfile.read(output)
        assert data.ndim==1 and .1<len(data)/rate<5 and abs(data).max()>.02
        report[name]=dict(text=text,voice=VOICE,seconds=round(len(data)/rate,3),peak=round(float(abs(data).max()),3))
        print(name,report[name]['seconds'],flush=True)
    (cache/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')

if __name__=='__main__':asyncio.run(main())
