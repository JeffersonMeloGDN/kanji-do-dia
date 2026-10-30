"""Gera kanji.json (asset do app) a partir do KANJIDIC2 + níveis JLPT novos."""
import gzip, json, sys
import xml.etree.ElementTree as ET

dl, out, extra_path, examples_path = sys.argv[1:5]

# Traduções manuais para kanjis sem significado em português no KANJIDIC2
extra = {}
with open(extra_path, encoding="utf-8") as f:
    for line in f:
        if line.strip():
            k, m = line.rstrip("\n").split("\t")
            extra[k] = [s.strip() for s in m.split(";")]

# Palavras de exemplo (kanji, palavra, leitura, tradução), escolhidas por pick_examples.py
examples = {}
with open(examples_path, encoding="utf-8") as f:
    for line in f:
        if line.strip():
            k, w, r, m = line.rstrip("\n").split("\t")
            examples.setdefault(k, []).append([w, r, m])

PROPER = ("Japão", "África", "Itália", "Holanda", "Nara", "Yamato", "China", "Qin",
          "Plêiades", "Buda", "Índia", "Europa", "América", "Coreia", "Inglaterra",
          "França", "Alemanha", "Rússia", "Espanha", "Portugal", "Ásia", "Tóquio", "Edo")


def fix_pt(m):
    m = m.strip().replace("vis-um-vis", "vis-à-vis")
    if m and m[0].isupper() and not m.startswith(PROPER):
        m = m[0].lower() + m[1:]
    return m


def fix_kun(r):
    # "なが.い" -> "なが(い)" ; prefixos/sufixos "-" são mantidos
    if "." in r:
        stem, oku = r.split(".", 1)
        tail = "-" if oku.endswith("-") else ""
        r = f"{stem}({oku.rstrip('-')}){tail}"
    return r


levels = {}
with open(f"{dl}/kanji.json", encoding="utf-8") as f:
    for k, v in json.load(f).items():
        if v.get("jlpt_new"):
            levels[k] = v["jlpt_new"]

data = {5: [], 4: [], 3: [], 2: [], 1: []}
missing_pt = []
with gzip.open(f"{dl}/kanjidic2.xml.gz") as f:
    root = ET.parse(f).getroot()

for ch in root.iter("character"):
    lit = ch.findtext("literal")
    if lit not in levels:
        continue
    on, kun, pt, en = [], [], [], []
    for rm in ch.iter("rmgroup"):
        for r in rm.findall("reading"):
            t = r.get("r_type")
            if t == "ja_on":
                on.append(r.text)
            elif t == "ja_kun":
                kun.append(r.text)
        for m in rm.findall("meaning"):
            lang = m.get("m_lang")
            if lang == "pt":
                pt.append(m.text)
            elif lang is None:
                en.append(m.text)
    freq = ch.findtext("misc/freq")
    if not pt:
        pt = extra.get(lit, [])
    if not pt:
        missing_pt.append((lit, levels[lit], en))
    pt = [fix_pt(m) for m in pt]
    kun = [fix_kun(r) for r in kun]
    data[levels[lit]].append({
        "k": lit,
        "pt": list(dict.fromkeys(pt))[:4],
        "on": on[:4],
        "kun": kun[:5],
        "ex": examples.get(lit, []),
        "f": int(freq) if freq else 9999,
    })

for lv in data:
    data[lv].sort(key=lambda e: e["f"])
    for e in data[lv]:
        del e["f"]

print({lv: len(v) for lv, v in data.items()})
print("sem pt:", len(missing_pt))
with open(f"{dl}/missing_pt.json", "w", encoding="utf-8") as f:
    json.dump(missing_pt, f, ensure_ascii=False, indent=0)
with open(out, "w", encoding="utf-8") as f:
    json.dump({f"N{lv}": v for lv, v in data.items()}, f, ensure_ascii=False, separators=(",", ":"))
