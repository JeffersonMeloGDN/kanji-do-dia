"""Escolhe até 2 palavras comuns do JMdict por kanji. Saída: examples_en.json"""
import gzip, json, re, sys
import xml.etree.ElementTree as ET

jm, kanji_json, out = sys.argv[1:4]
level = {e["k"]: int(lv[1]) for lv, es in json.load(open(kanji_json, encoding="utf-8")).items() for e in es}
kanji = set(level)
PEN = {5: 0, 4: 30, 3: 80, 2: 150, 1: 250}

def score(pri, word, k):
    s = 600
    for p in pri:
        m = re.match(r"nf(\d+)", p)
        if m:
            s = min(s, int(m.group(1)) * 5)
    if "ichi1" in pri:
        s -= 300
    if "news1" in pri or "spec1" in pri:
        s -= 100
    for ch in word:
        if ch != k and not ("぀" <= ch <= "ヿ"):
            s += PEN.get(level.get(ch), 400)
    return s

cands = {k: [] for k in kanji}
with gzip.open(jm) as f:
    for _, ent in ET.iterparse(f):
        if ent.tag != "entry":
            continue
        kebs = ent.findall("k_ele")
        if not kebs:
            ent.clear(); continue
        k0 = kebs[0]
        word = k0.findtext("keb")
        pri = [p.text for p in k0.findall("ke_pri")]
        if not 2 <= len(word) <= 4:
            ent.clear(); continue
        rebs = [r for r in ent.findall("r_ele") if r.find("re_nokanji") is None]
        reading = rebs[0].findtext("reb")
        sense = ent.find("sense")
        glosses = [g.text for g in sense.findall("gloss")][:3]
        misc = [m.text for m in sense.findall("misc")]
        bad = ("vulgar", "derogatory", "archaism", "obsolete", "rare", "obscure")
        if any(b in m for m in misc for b in bad) or glosses[0][:1].isupper():
            ent.clear(); continue
        for ch in set(word):
            if ch in cands:
                # Sem prioridade no JMdict = palavra incomum; só entra se faltar opção
                s = score(pri, word, ch) + (0 if pri else 2000)
                cands[ch].append((s, len(word), word, reading, glosses))
        ent.clear()

res = {}
for k, c in cands.items():
    c.sort()
    res[k] = [{"w": w, "r": r, "en": g} for _, _, w, r, g in c[:2]]
print("sem exemplos:", sum(1 for v in res.values() if not v))
json.dump(res, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=0)

