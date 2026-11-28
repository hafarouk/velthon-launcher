# Natural voice recordings for TV Bible Screensaver verses (same voices and settings as v1.56).
import json, hashlib, os, re, asyncio, subprocess
import edge_tts
VOICE={'en':'en-US-AvaMultilingualNeural','ar':'ar-SA-ZariyahNeural'}
AR_DIG=str.maketrans('0123456789','٠١٢٣٤٥٦٧٨٩')
def spoken_ref(ref, lang):
    m=re.match(r'^(.*?)\s*([\d٠-٩]+)\s*[:：]\s*([\d٠-٩]+)(?:\s*[-–]\s*([\d٠-٩]+))?$', ref.strip())
    if not m: return ref
    book,ch,v1,v2=m.groups()
    if lang=='en':
        if book.strip()=='Psalms': book='Psalm'
        return '%s %s, verse%s %s%s'%(book.strip(),ch,'s' if v2 else '',v1,(' to '+v2) if v2 else '')
    return '%s، الأصحاح %s، العدد %s%s'%(book.strip(),ch,v1,(' إلى '+v2) if v2 else '')
jobs=[(j['lang'],j['id'],j['text']+' ... '+spoken_ref(j['ref'],j['lang'])) for j in json.load(open(os.path.join(os.path.dirname(__file__),'needed.json')))]
os.makedirs('out/voice/en',exist_ok=True); os.makedirs('out/voice/ar',exist_ok=True)
sem=asyncio.Semaphore(6)
async def one(lang,key,say):
    dst='out/voice/%s/%s.webm'%(lang,key)
    if os.path.exists(dst): return
    async with sem:
        for attempt in range(5):
            try:
                tmp='tmp_%s_%s.mp3'%(lang,key)
                await edge_tts.Communicate(say, VOICE[lang], rate='-6%' if lang=='en' else '-4%').save(tmp)
                p=await asyncio.create_subprocess_exec('ffmpeg','-nostdin','-y','-loglevel','error','-i',tmp,'-af','silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse,apad=pad_dur=0.25','-ac','1','-ar','24000','-c:a','libopus','-b:a','22k','-application','voip',dst)
                await p.wait(); os.remove(tmp); return
            except Exception as e:
                print('retry',key,e,flush=True); await asyncio.sleep(3+attempt*4)
async def main():
    await asyncio.gather(*[one(*j) for j in jobs])
asyncio.run(main())
json.dump([j[:2] for j in jobs],open('out/index.json','w'))
