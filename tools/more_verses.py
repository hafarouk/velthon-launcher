import json, re, sys
import add_verses as A
A.B.update({
 'Gen': ('Genesis','Genesis','GEN','التكوين','Genèse','Génesis'),
 'Exod': ('Exodus','Exodus','EXO','الخروج','Exode','Éxodo'),
 'Num': ('Numbers','Numbers','NUM','العدد','Nombres','Números'),
 '1Sam': ('I Samuel','1 Samuel','1SA','صموئيل الأول','1 Samuel','1 Samuel'),
 '2Sam': ('II Samuel','2 Samuel','2SA','صموئيل الثاني','2 Samuel','2 Samuel'),
 '1Chr': ('I Chronicles','1 Chronicles','1CH','أخبار الأيام الأول','1 Chroniques','1 Crónicas'),
 '2Chr': ('II Chronicles','2 Chronicles','2CH','أخبار الأيام الثاني','2 Chroniques','2 Crónicas'),
 'Job': ('Job','Job','JOB','أيوب','Job','Job'),
 'Eccl': ('Ecclesiastes','Ecclesiastes','ECC','الجامعة','Ecclésiaste','Eclesiastés'),
 'Song': ('Song of Solomon','Song of Solomon','SOL','نشيد الأنشاد','Cantique des cantiques','Cantares'),
 'Ezek': ('Ezekiel','Ezekiel','EZE','حزقيال','Ézéchiel','Ezequiel'),
 'Joel': ('Joel','Joel','JOE','يوئيل','Joël','Joel'),
 'Nah': ('Nahum','Nahum','NAH','ناحوم','Nahum','Nahúm'),
 'Zech': ('Zechariah','Zechariah','ZEC','زكريا','Zacharie','Zacarías'),
 'Acts': ('Acts','Acts','ACT','أعمال الرسل','Actes','Hechos'),
 'Eph': ('Ephesians','Ephesians','EPH','أفسس','Éphésiens','Efesios'),
 '1Thess': ('I Thessalonians','1 Thessalonians','1TH','تسالونيكي الأولى','1 Thessaloniciens','1 Tesalonicenses'),
 '2Thess': ('II Thessalonians','2 Thessalonians','2TH','تسالونيكي الثانية','2 Thessaloniciens','2 Tesalonicenses'),
 '1Tim': ('I Timothy','1 Timothy','1TI','تيموثاوس الأولى','1 Timothée','1 Timoteo'),
 '2Pet': ('II Peter','2 Peter','2PE','بطرس الثانية','2 Pierre','2 Pedro'),
 'Jude': ('Jude','Jude','JUD','يهوذا','Jude','Judas'),
})
MORE = """Gen 28:15|Gen 50:20|Exod 14:14|Exod 15:2|Exod 33:14|Num 6:24-26|Deut 6:5|Deut 31:8|Deut 33:27|1Sam 16:7|2Sam 22:31|
1Chr 16:11|1Chr 16:34|2Chr 7:14|Job 19:25|Job 23:10|Ps 5:3|Ps 9:10|Ps 16:8|Ps 16:11|Ps 18:2|Ps 19:14|Ps 25:4|Ps 25:5|Ps 27:4|
Ps 28:7|Ps 31:24|Ps 34:4|Ps 36:7|Ps 37:4|Ps 37:7|Ps 39:7|Ps 42:11|Ps 51:10|Ps 51:12|Ps 59:16|Ps 61:2|Ps 63:3|Ps 73:26|Ps 86:5|
Ps 90:12|Ps 91:2|Ps 91:11|Ps 100:5|Ps 103:2|Ps 103:8|Ps 119:105|Ps 119:114|Ps 121:2|Ps 121:8|Ps 127:1|Ps 130:5|Ps 139:14|
Ps 143:8|Ps 143:10|Ps 145:18|Ps 147:11|Prov 19:17|Prov 23:18|Eccl 3:1|Eccl 3:11|Eccl 4:9|Eccl 12:13|Song 8:7|Isa 12:2|Isa 26:4|
Isa 30:15|Isa 30:21|Isa 40:28|Isa 41:13|Isa 43:1|Isa 46:4|Isa 49:15|Isa 53:5|Isa 54:10|Isa 55:6|Isa 55:8|Isa 55:11|Isa 58:11|
Isa 64:8|Isa 65:24|Jer 17:7|Jer 31:3|Jer 32:17|Jer 32:27|Lam 3:25|Lam 3:26|Ezek 36:26|Joel 2:13|Nah 1:7|Zech 4:6|Mic 7:7|
Matt 5:14|Matt 5:16|Matt 5:44|Matt 6:6|Matt 6:21|Matt 6:26|Matt 7:12|Matt 11:29|Matt 16:24|Matt 18:20|Matt 21:22|Matt 26:41|
Mark 10:45|Mark 11:24|Mark 12:31|Luke 1:37|Luke 6:31|Luke 6:38|Luke 9:23|Luke 15:7|Luke 18:27|Luke 19:10|John 1:5|John 1:12|
John 6:35|John 8:32|John 8:36|John 10:10|John 10:27|John 10:28|John 14:15|John 15:5|John 15:7|John 15:11|John 15:16|John 20:29|
Acts 1:8|Acts 4:12|Acts 16:31|Rom 1:16|Rom 3:23|Rom 5:1|Rom 6:23|Rom 8:1|Rom 8:18|Rom 10:9|Rom 10:13|Rom 10:17|Rom 12:10|
Rom 12:18|Rom 15:7|1Cor 2:9|1Cor 3:16|1Cor 13:13|1Cor 15:57|1Cor 16:13|2Cor 3:17|2Cor 5:7|2Cor 9:7|Gal 5:1|Gal 5:22-23|Gal 6:2|
Eph 2:8|Eph 2:10|Eph 4:26|Eph 4:29|Eph 4:32|Eph 5:2|Phil 2:3|Phil 2:5|Phil 2:14|Phil 4:8|Phil 4:11|Col 3:2|Col 3:12|Col 3:13|
Col 3:14|Col 3:15|Col 3:17|Col 4:2|1Thess 5:11|1Thess 5:16-18|2Thess 3:3|2Thess 3:16|1Tim 4:12|1Tim 6:6|1Tim 6:12|2Tim 2:15|
2Tim 3:16|2Tim 4:7|Heb 10:23|Heb 10:24|Heb 11:6|Heb 12:14|Heb 13:2|Jas 1:2-3|Jas 1:12|Jas 1:17|Jas 1:19|Jas 1:22|Jas 4:7|
Jas 4:10|Jas 5:16|1Pet 2:9|1Pet 4:8|1Pet 5:6|2Pet 3:9|1John 3:1|1John 4:4|1John 4:7|1John 4:16|1John 5:4|1John 5:14|
Jude 1:24-25|Rev 1:8|Rev 2:10""".replace('\n', '').split('|')
d = json.load(open(sys.argv[1], encoding='utf-8'))
have = {e['ref'] for e in d}
DROP = set('Gen 28:15|Ps 127:1|Deut 33:27|Ps 37:7|Isa 30:15|2Cor 5:7|Col 3:13|Matt 16:24|Luke 6:31|1Chr 16:34|Matt 21:22|Luke 18:27|Zech 4:6|Isa 40:28|Acts 1:8|Rom 1:16|Isa 43:1|Luke 15:7|Matt 6:6|Rom 10:9|2Pet 3:9|Jude 1:24-25|Rev 2:10|Song 8:7|Eph 5:2|1Tim 6:12|Heb 13:2|1Tim 4:12|Joel 2:13'.split('|'))
new = [r for r in MORE if r not in have and r not in DROP]
print('already there', len(MORE) - len(new))
for r in new:
    raw = A.kjv.get((A.B[r.split()[0]][0], int(r.split()[1].split(':')[0]), int(r.split(':')[1].split('-')[-1])))
    e = A.make(r); d.append(e)
    flag = '  <<END' if raw and raw.rstrip()[-1] in ',;:' else ''
    print(r, '|', e['en'][:150], flag)
for e in d:
    for k in ('en', 'ar', 'fr', 'es'): e[k] = A.tidy(e[k])
json.dump(d, open(sys.argv[1], 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print('total', len(d))
