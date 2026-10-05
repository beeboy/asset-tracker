"""인사이트 화면용 뉴스 수집 (파이썬 표준 라이브러리만).

  python server.py --news     # data/news.json 을 만든다 (GitHub Actions 가 한 시간마다 실행)

키가 필요 없는 공개 RSS(미국 언론, Google 뉴스 검색), Yahoo Finance 뉴스 검색을 모으고
data/config.json 의 "ai" 중계(Cloudflare Worker → Gemini 무료 등급)로 한글 번역·요약한다.
AI 가 안 되면 원문 제목을 그대로 둔다. 요청 수를 아끼려고 후보가 같으면 지난 번역을 다시 쓴다.
"""
from __future__ import annotations

import hashlib
import html
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta, timezone
from email.utils import parsedate_to_datetime
from pathlib import Path

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36",
      "Accept-Language": "en-US,en;q=0.9,ko;q=0.8"}
KST = timezone(timedelta(hours=9))


def gnews(q: str, when: str) -> str:
    return "https://news.google.com/rss/search?" + urllib.parse.urlencode(
        {"q": f"{q} when:{when}", "hl": "en-US", "gl": "US", "ceid": "US:en"})


MEDIA_Q = '"stock market" OR "Wall Street" OR "Federal Reserve" OR Nasdaq OR "S&P 500" OR economy'
BROKERS = ["Goldman Sachs", "Morgan Stanley", "JPMorgan", "Bank of America", "Citi", "Wells Fargo", "UBS", "Barclays",
           "Wedbush", "Jefferies", "Deutsche Bank", "Bernstein", "Evercore", "Piper Sandler"]
BROKER_Q = '"price target" OR upgrade OR downgrade OR "analyst" OR strategist'
# 대형 증권사·투자은행: 이들의 기사를 먼저 고른다
BIG_RE = re.compile(r"Goldman|Morgan Stanley|JPMorgan|JP Morgan|J\.P\. Morgan|Bank of America|BofA|Citi(group)?\b|Wells Fargo|UBS|Barclays|Deutsche Bank", re.I)
BROKER_RE = re.compile("|".join([re.escape(b) for b in BROKERS] + [r"price target", r"upgrad", r"downgrad", r"analyst", r"strategist", r"overweight", r"outperform"]), re.I)
MEDIA_FEEDS = [
    ("CNBC", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=100003114"),
    ("CNBC", "https://search.cnbc.com/rs/search/combinedcms/view.xml?partnerId=wrss01&id=20910258"),
    ("MarketWatch", "https://feeds.content.dowjones.io/public/rss/mw_topstories"),
    ("WSJ", "https://feeds.content.dowjones.io/public/rss/RSSMarketsMain"),
    ("Yahoo Finance", "https://finance.yahoo.com/news/rssindex"),
    ("New York Times", "https://rss.nytimes.com/services/xml/rss/nyt/Business.xml"),
    (None, gnews(MEDIA_Q, "1d")),
]
# 미래 가치 뉴스를 모으지 않을 종목 (지수·현금성·요인 대리 지표)
SKIP = {"QQQ", "SPY", "SGOV", "BIL", "SHV", "TLT", "DBC", "^TNX", "CL=F", "GC=F"}


# ---------------------------------------------------------------- 받기·읽기
LAST_ERR = {"msg": ""}


def fetch(url: str, timeout: int = 20, headers: dict | None = None) -> bytes | None:
    for k in range(2):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={**UA, **(headers or {})}), timeout=timeout) as r:
                return r.read()
        except Exception as e:  # noqa: BLE001 - 출처 하나가 안 돼도 계속
            LAST_ERR["msg"] = str(e)[:80]
            time.sleep(1.5 * (k + 1))
    return None


def _t(e) -> str:
    return (e.text or "").strip() if e is not None else ""


def _iso(s: str) -> str:
    if not s:
        return ""
    try:
        d = parsedate_to_datetime(s)
    except (TypeError, ValueError):
        try:
            d = datetime.fromisoformat(s.replace("Z", "+00:00"))
        except ValueError:
            return ""
    if d.tzinfo is None:
        d = d.replace(tzinfo=timezone.utc)
    return d.astimezone(timezone.utc).isoformat(timespec="seconds")


def _clean(s: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(re.sub(r"<[^>]+>", " ", s or ""))).strip()


def parse_feed(raw: bytes | None, src: str | None = None) -> list[dict]:
    if not raw:
        return []
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    out = []
    for it in root.iter("item"):  # RSS 2.0
        title, s = _clean(_t(it.find("title"))), it.find("source")
        source = _t(s) or src or ""
        if source and title.endswith(" - " + source):
            title = title[: -len(source) - 3]
        desc = _clean(_t(it.find("description")))
        if src is None or desc.startswith(title[:30]):  # Google 뉴스 설명은 제목 반복이라 버린다
            desc = ""
        out.append({"title": title, "link": _t(it.find("link")), "time": _iso(_t(it.find("pubDate"))), "source": source, "desc": desc[:220]})
    ns = {"a": "http://www.w3.org/2005/Atom", "media": "http://search.yahoo.com/mrss/", "yt": "http://www.youtube.com/xml/schemas/2015"}
    for e in root.findall("a:entry", ns):  # Atom (유튜브)
        link = e.find("a:link", ns)
        out.append({"title": _clean(_t(e.find("a:title", ns))), "link": link.get("href") if link is not None else "",
                    "time": _iso(_t(e.find("a:published", ns))), "source": src or _t(e.find("a:author/a:name", ns)),
                    "id": _t(e.find("yt:videoId", ns)), "desc": _clean(_t(e.find("media:group/media:description", ns)))[:160]})
    return [x for x in out if x["title"] and x["link"]]


def yahoo_news(q: str, n: int = 10) -> list[dict]:
    raw = fetch("https://query1.finance.yahoo.com/v1/finance/search?" + urllib.parse.urlencode({"q": q, "newsCount": n, "quotesCount": 0}))
    try:
        news = json.loads(raw or b"{}").get("news") or []
    except json.JSONDecodeError:
        return []
    return [{"title": x.get("title", ""), "link": x.get("link", ""), "source": x.get("publisher", ""), "desc": "",
             "time": datetime.fromtimestamp(x.get("providerPublishTime") or 0, timezone.utc).isoformat(timespec="seconds")} for x in news if x.get("title")]


def _key(t: str) -> str:
    return re.sub(r"[^a-z0-9가-힣]", "", t.lower())[:60]


def dedupe(items: list[dict], cap: int, per_source: int = 99) -> list[dict]:
    seen, cnt, out = set(), {}, []
    for x in items:
        k = _key(x["title"])
        if not k or k in seen or cnt.get(x["source"], 0) >= per_source:
            continue
        seen.add(k)
        cnt[x["source"]] = cnt.get(x["source"], 0) + 1
        out.append(x)
        if len(out) >= cap:
            break
    return out


def recent(items: list[dict], hours: float) -> list[dict]:
    lim = (datetime.now(timezone.utc) - timedelta(hours=hours)).isoformat()
    return sorted([x for x in items if not x["time"] or x["time"] >= lim], key=lambda x: x["time"] or "", reverse=True)


# ---------------------------------------------------------------- AI 중계
SYS = "너는 미국 증시 뉴스를 한국 개인 투자자에게 전하는 편집자다. 반드시 JSON 하나만 출력한다(설명·코드 블록 없이). 한국어는 짧고 자연스럽게, 회사·기관 이름은 한국에서 흔히 쓰는 표기로. 원문에 없는 직함·사실은 덧붙이지 않는다."


def ask_ai(url: str, prompt: str) -> dict:
    body = json.dumps({"system": SYS, "prompt": prompt[:11800]}).encode("utf-8")
    last = ""
    for k in range(2):
        try:
            req = urllib.request.Request(url, data=body, method="POST", headers={**UA, "Content-Type": "application/json"})
            with urllib.request.urlopen(req, timeout=150) as r:
                text = json.load(r).get("text", "")
            m = re.search(r"\{.*\}", re.sub(r"```(?:json)?", "", text), re.S)
            if not m:
                raise ValueError("JSON 없음")
            return json.loads(m.group(0))
        except urllib.error.HTTPError as e:
            last = f"{e.code} {e.read()[:200].decode('utf-8', 'replace')}"
            if e.code not in (429, 500, 502, 503, 504):
                break
        except Exception as e:  # noqa: BLE001
            last = str(e)[:200]
        time.sleep(6)
    raise RuntimeError(last or "AI 응답 없음")


def _line(i: int, x: dict) -> str:
    return f"{i}. [{x['source']}] {x['title']}" + (f" — {x['desc'][:140]}" if x.get("desc") else "")


def _pick(cands: list[dict], sel: list, n: int) -> list[dict]:
    """AI 가 고른 번호 → 화면용 항목. 번호가 틀리면 건너뛴다. 모자라면 최신 기사로 채운다"""
    out, used = [], set()
    for s in sel or []:
        try:
            i = int(s.get("i"))
        except (TypeError, ValueError, AttributeError):
            continue
        if 0 <= i < len(cands) and i not in used:
            used.add(i)
            x = cands[i]
            out.append({"title": (s.get("ko") or x["title"]).strip(), "orig": x["title"], "summary": (s.get("sum") or "").strip(),
                        "broker": (s.get("broker") or "").strip(), "source": x["source"], "link": x["link"], "time": x["time"]})
        if len(out) >= n:
            break
    return out


def _plain(cands: list[dict], n: int) -> list[dict]:
    return [{"title": x["title"], "orig": x["title"], "summary": "", "source": x["source"], "link": x["link"], "time": x["time"]} for x in cands[:n]]


def _hash(*parts) -> str:
    return hashlib.sha1(json.dumps(parts, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:16]


def _age_h(iso: str | None) -> float:
    try:
        return (datetime.now(timezone.utc) - datetime.fromisoformat(iso)).total_seconds() / 3600
    except (TypeError, ValueError):
        return 1e9


def pick_news(ai: str | None, media: list[dict], broker: list[dict], scope: str, prev: dict, errs: list) -> dict:
    broker = sorted(broker, key=lambda x: not BIG_RE.search(x["title"] + " " + x.get("desc", "")))  # 대형사 기사 먼저
    key = _hash("n3", [x["title"] for x in media], [x["title"] for x in broker])
    if prev.get("key") == key and prev.get("media"):
        return prev
    res = {"key": key, "updated": datetime.now(timezone.utc).isoformat(timespec="seconds")}
    if ai and (media or broker):
        when = "최근 24시간" if scope == "realtime" else "지난 한 주"
        prompt = (f"{when} 미국 경제·증시 뉴스 후보다.\n"
                  f"[미국 언론] 목록에서 투자자에게 가장 중요한 기사 3개, [증권사] 목록에서 증권사·투자은행의 의견·전망·목표가 기사 3개를 골라라. "
                  f"증권사 기사는 대형 증권사·투자은행(골드만삭스, 모건스탠리, JP모건, 뱅크오브아메리카, 씨티, 웰스파고, UBS, 바클레이즈, 도이치뱅크)의 기사를 우선한다. 같은 사건은 하나만.\n"
                  '출력 형식: {"media":[{"i":번호,"ko":"한국어 제목","sum":"핵심 한 문장"}],"broker":[{"i":번호,"ko":"한국어 제목","sum":"핵심 한 문장","broker":"증권사 이름(한국어)"}]} 중요도 순.\n\n'
                  "[미국 언론]\n" + "\n".join(_line(i, x) for i, x in enumerate(media)) +
                  "\n\n[증권사]\n" + "\n".join(_line(i, x) for i, x in enumerate(broker)))
        try:
            j = ask_ai(ai, prompt)
            res["media"], res["broker"] = _pick(media, j.get("media"), 3), _pick(broker, j.get("broker"), 3)
            if res["media"] or res["broker"]:
                return res
        except Exception as e:  # noqa: BLE001
            errs.append(f"{scope}: {e}")
        if prev.get("media") and _age_h(prev.get("updated")) < 6:  # 번역이 잠깐 안 되면 직전 번역을 유지
            return prev
    res["media"], res["broker"], res["key"] = _plain(media, 3), _plain(broker, 3), ""
    return res


# ---------------------------------------------------------------- 미래 가치 인사이트
# 분류: 성장 동력·기술 혁신 / 시장·산업 트렌드 / 펀더멘탈·리스크 / 무형 자산·ESG
CATS = {"growth": "성장 동력 및 기술 혁신 (신기술, 신제품, 신사업, R&D, AI, 특허 출원 등 미래 성장 엔진)",
        "market": "시장 및 산업 트렌드 (산업 수요, 시장 점유율, 경쟁 구도, 공급망, 업계 전반의 흐름)",
        "fund": "펀더멘탈 및 리스크 관리 (실적·매출·이익률 전망, 재무 건전성, 밸류에이션, 규제·소송·리스크)",
        "esg": "무형 자산 및 지속 가능성 (ESG, 지배구조, 경영진, 브랜드, 인재, 특허·지식재산, 친환경)"}
CAT_Q = {"growth": "innovation OR technology OR launch OR \"new product\" OR AI OR breakthrough",
         "market": "industry OR \"market share\" OR demand OR competition OR trend",
         "fund": "earnings OR revenue OR margin OR guidance OR valuation OR risk OR regulation",
         "esg": "ESG OR sustainability OR governance OR CEO OR brand OR patent OR emissions"}
# 보유 종목의 관련 업계 (모르는 종목은 회사 이름으로 업계 기사를 찾는다)
INDUSTRY = {"TSLA": ("전기차·자율주행·로봇", '"electric vehicle" OR EV OR "autonomous driving" OR robotaxi OR "humanoid robot" OR "battery"'),
            "NVDA": ("반도체·AI 인프라", 'semiconductor OR "AI chip" OR GPU OR "data center" OR "AI infrastructure"'),
            "SPCX": ("우주·위성 통신", '"space industry" OR "satellite internet" OR "rocket launch" OR "space economy" OR Starlink'),
            "AAPL": ("스마트폰·소비자 기기", 'smartphone OR "consumer electronics" OR wearables'),
            "MSFT": ("클라우드·소프트웨어", '"cloud computing" OR "enterprise software" OR "generative AI"'),
            "GOOGL": ("검색·클라우드·AI", '"online advertising" OR "cloud computing" OR "generative AI"'),
            "AMZN": ("이커머스·클라우드", 'e-commerce OR "cloud computing" OR logistics'),
            "META": ("소셜미디어·AI", '"social media" OR "digital advertising" OR "generative AI" OR "AR glasses"'),
            "AMD": ("반도체·AI 인프라", 'semiconductor OR "AI chip" OR GPU OR "data center"'),
            "AVGO": ("반도체·네트워크", 'semiconductor OR "AI chip" OR networking OR "custom silicon"'),
            "TSM": ("반도체 파운드리", 'semiconductor OR foundry OR "chip manufacturing"')}
INS_KEEP_D, INS_MAX = 14, 240


def _nm(t: str, names: dict) -> str:
    return re.sub(r",?\s*(Inc\.?|Corp\.?|Corporation|Co\.,? Ltd\.?|Ltd\.?|Holdings?)$", "", names.get(t) or t).strip()


def collect_insight(ai: str | None, tks: list[str], names: dict, prev: dict, pool: dict, now: datetime, errs: list, log=print) -> dict:
    """보유 종목(held)·관련 업계(industry) 기사 후보를 모아, 처음 보는 기사만 AI 에 보내 분류·번역한다.
    고른 기사는 14일 동안 쌓아 두고(최대 240건), 브라우저가 리로드·더 보기 때 아직 안 본 기사로 갈아 끼운다."""
    items = [x for x in prev.get("items") or [] if (x.get("time") or "") >= (now - timedelta(days=INS_KEEP_D)).isoformat()
             and not (ai and x.get("plain"))]  # AI 가 되면 번역 안 된 임시 기사는 다시 고른다
    have = {x["id"] for x in items}
    seen = {k: v for k, v in (pool.get("ins_seen") or {}).items() if v >= (now - timedelta(days=INS_KEEP_D)).isoformat()}
    cands = []  # (ticker, scope, 기사)
    for t in tks:
        nm = _nm(t, names)
        got = yahoo_news(t, 12)
        for c, q in CAT_Q.items():
            got += parse_feed(fetch(gnews(f'"{nm}" ({q})', "7d")))[:8]
        held = dedupe(recent(got, 10 * 24), 24)
        ind_lbl, ind_q = INDUSTRY.get(t.upper(), (f"{nm} 업계", f'"{nm}" (industry OR sector OR competitors OR rivals)'))
        ind = parse_feed(fetch(gnews(f"({ind_q}) (outlook OR future OR growth OR trend OR forecast)", "7d")))[:20]
        ind = [x for x in dedupe(recent(ind, 10 * 24), 16) if _key(x["title"]) not in {_key(y["title"]) for y in held}]
        log(f"미래 가치 {t}: 보유 {len(held)}건, 업계 {len(ind)}건")
        cands += [(t, "held", x) for x in held] + [(t, "industry", x) for x in ind]
    fresh, ks = [], set()
    for t, sc, x in cands:
        k = _key(x["title"])
        if k and k not in have and k not in seen and k not in ks:
            ks.add(k)
            fresh.append((t, sc, x))
    fresh = sorted(fresh, key=lambda c: c[2]["time"] or "", reverse=True)
    fresh = [c for c in fresh if c[1] == "held"][:45] + [c for c in fresh if c[1] == "industry"][:25]
    res = {"updated": prev.get("updated") or now.isoformat(timespec="seconds"), "v": 1,
           "industry": {t: INDUSTRY.get(t.upper(), (f"{_nm(t, names)} 업계",))[0] for t in tks}}
    if fresh and ai:
        fresh = sorted(fresh, key=lambda c: (c[1] != "held", tks.index(c[0])))
        lines, cur, size = [], None, 0
        for i, (t, sc, x) in enumerate(fresh):
            g = f"[{t} {'보유 종목' if sc == 'held' else '관련 업계: ' + res['industry'][t]}]"
            ln = f"{i}. [{x['source']}] {x['title']}" + (f" — {x['desc'][:80]}" if x.get("desc") else "")
            if size + len(ln) + len(g) > 9000:  # 중계 한도(약 11,800자) 안으로. 남은 후보는 다음 시간에
                break
            if g != cur:
                lines.append("\n" + g)
                cur = g
            lines.append(ln)
            size += len(ln) + 1
        prompt = ("아래는 보유 종목과 그 관련 업계의 최근 기사 후보다. 1~3년 뒤 기업 가치 판단에 도움이 되는 기사만 골라 다음 4개 분류 중 하나로 나눠라. "
                  "단기 주가 등락·광고성·무관한 기사는 빼라. 같은 사건은 하나만. 좋은 기사면 여러 개 골라도 되지만 최대 25개, 보유 종목 기사를 우선한다.\n"
                  + "\n".join(f"- {k}: {v}" for k, v in CATS.items()) +
                  '\n출력 형식: {"items":[{"i":번호,"cat":"growth|market|fund|esg","ko":"한국어 제목","sum":"핵심 요약 한 문장"}]}\n' + "\n".join(lines))
        try:
            j = ask_ai(ai, prompt)
            n0 = len(items)
            for s in j.get("items") or []:
                try:
                    i = int(s.get("i"))
                except (TypeError, ValueError, AttributeError):
                    continue
                if not 0 <= i < len(fresh) or s.get("cat") not in CATS:
                    continue
                t, sc, x = fresh[i]
                k = _key(x["title"])
                if k in have:
                    continue
                have.add(k)
                items.append({"id": k, "ticker": t, "scope": sc, "cat": s["cat"], "title": (s.get("ko") or x["title"]).strip(), "orig": x["title"],
                              "summary": (s.get("sum") or "").strip(), "source": x["source"], "link": x["link"], "time": x["time"],
                              "added": now.isoformat(timespec="seconds")})
            fresh = fresh[:sum(1 for ln in lines if ln[:1].isdigit())]
            for t, sc, x in fresh:  # 물어본 기사는 고르지 않았어도 다시 묻지 않는다
                seen[_key(x["title"])] = now.isoformat(timespec="seconds")
            res["updated"] = now.isoformat(timespec="seconds")
            log(f"미래 가치 새 기사 {len(items) - n0}건 (후보 {len(fresh)}건)")
        except Exception as e:  # noqa: BLE001
            errs.append(f"insight: {e}")
    if fresh and not items:  # AI 가 없거나 실패하고 보여 줄 기사도 없으면 원문 그대로 (분류는 기사 묶음으로 짐작)
        for t, sc, x in fresh[:40]:
            items.append({"id": _key(x["title"]), "plain": 1, "ticker": t, "scope": sc, "cat": "market" if sc == "industry" else "growth", "title": x["title"],
                          "orig": x["title"], "summary": "", "source": x["source"], "link": x["link"], "time": x["time"], "added": now.isoformat(timespec="seconds")})
        res["updated"] = now.isoformat(timespec="seconds")
    pool["ins_seen"] = seen
    res["items"] = sorted(items, key=lambda x: x.get("time") or "", reverse=True)[:INS_MAX]
    return res


# ---------------------------------------------------------------- 수집 본체
def collect_news(data: Path, ai: str | None, tickers: list[str], names: dict, log=print) -> dict:
    out_p, pool_p = data / "news.json", data / "news_pool.json"

    def rd(p, d):
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except (FileNotFoundError, json.JSONDecodeError):
            return d
    old, pool = rd(out_p, {}), rd(pool_p, {"items": []})
    now = datetime.now(timezone.utc)
    errs: list[str] = []
    out = {"updated": now.isoformat(timespec="seconds")}

    # 1) 실시간: 미국 언론 RSS + 증권사 의견 (Google 뉴스 검색)
    media_all = []
    for src, url in MEDIA_FEEDS:
        got = parse_feed(fetch(url), src)
        log(f"뉴스 {src or 'Google'}: {len(got)}건")
        media_all += got
    media = dedupe(recent(media_all, 24), 32, per_source=7)
    bk = parse_feed(fetch(gnews(BROKER_Q, "2d"))) + yahoo_news("analyst price target", 15) + yahoo_news("upgrade downgrade stock", 15)
    bk += [x for x in media_all if BROKER_RE.search(x["title"])]
    broker = dedupe(recent([x for x in bk if BROKER_RE.search(x["title"] + " " + x.get("desc", ""))], 36), 26, per_source=6)
    media = [x for x in media if x not in broker]
    log(f"실시간 후보: 언론 {len(media)}, 증권사 {len(broker)}")
    out["realtime"] = pick_news(ai, media, broker, "realtime", old.get("realtime") or {}, errs)

    # 지난 기사 모음 (주간 선정용, 8일 보관)
    have = {_key(x["title"]) for x in pool["items"]}
    for kind, xs in (("media", media), ("broker", broker)):
        for x in xs:
            if _key(x["title"]) not in have:
                pool["items"].append({**x, "kind": kind})
    lim = (now - timedelta(days=8)).isoformat()
    pool["items"] = sorted([x for x in pool["items"] if (x.get("time") or "") >= lim], key=lambda x: x.get("time") or "")[-700:]

    # 2) 주간: 6시간마다 다시 고른다
    wk = old.get("weekly") or {}
    if _age_h(wk.get("updated")) >= 6 or not wk.get("media") or wk.get("v") != 3:
        def spread(xs, n):
            return xs if len(xs) <= n else [xs[int(i * len(xs) / n)] for i in range(n)]
        lim7 = (now - timedelta(days=7)).isoformat()
        pm = [x for x in pool["items"] if x["kind"] == "media" and (x.get("time") or "") >= lim7]
        pb = [x for x in pool["items"] if x["kind"] == "broker" and (x.get("time") or "") >= lim7]
        wm = dedupe(recent(parse_feed(fetch(gnews(MEDIA_Q, "7d"))), 7 * 24)[:20] + spread(pm, 24), 38, per_source=6)
        wbg = [x for x in recent(parse_feed(fetch(gnews(BROKER_Q, "7d"))), 7 * 24) if BROKER_RE.search(x["title"])]
        wb = dedupe(wbg[:20] + spread(pb, 20), 32, per_source=5)
        wk = pick_news(ai, wm, wb, "weekly", {}, errs)
        wk["v"] = 3
    out["weekly"] = wk

    # 3) 미래 가치 인사이트: 보유 종목·관련 업계 기사를 4개 분류로 모아 쌓아 둔다 (매시간, 새 후보만 AI 에 보냄)
    tks = [t for t in tickers if t.upper() not in SKIP and "=" not in t]
    ins = collect_insight(ai, tks, names, old.get("insight") or {}, pool, now, errs, log)
    out["insight"] = ins
    out["future_meta"] = {"updated": ins.get("updated") or now.isoformat(timespec="seconds")}

    out["ai"] = "ok" if not errs else "; ".join(errs)[:300]
    if not ai:
        out["ai"] = "AI 중계 주소 없음 (data/config.json 의 ai)"
    for p, obj in ((out_p, out), (pool_p, pool)):
        tmp = p.with_suffix(".tmp")
        tmp.write_text(json.dumps(obj, ensure_ascii=False, indent=1), encoding="utf-8")
        tmp.replace(p)
    log("뉴스 저장" + ("" if not errs else " (AI 오류: " + out["ai"] + ")"))
    return out
