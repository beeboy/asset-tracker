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

    # 3) 미래 가치: 종목별 장기 전망 기사 (3시간마다)
    fut_old = {f["ticker"]: f for f in old.get("future") or []}
    tks = [t for t in tickers if t.upper() not in SKIP and "=" not in t]
    if _age_h((old.get("future_meta") or {}).get("updated")) >= 3 or set(tks) - set(fut_old):
        cands = {}
        for t in tks:
            nm = re.sub(r",?\s*(Inc\.?|Corp\.?|Corporation|Co\.,? Ltd\.?|Ltd\.?|Holdings?)$", "", names.get(t) or t).strip()
            q = f'"{nm}" (outlook OR forecast OR "price target" OR "long-term" OR analyst OR growth)'
            xs = dedupe(recent(yahoo_news(t, 10) + parse_feed(fetch(gnews(q, "7d")))[:12], 10 * 24), 10)
            cands[t] = xs
            log(f"미래 가치 {t}: {len(xs)}건")
        flat, lines = [], []
        for t, xs in cands.items():
            lines.append(f"\n[{t} {names.get(t) or ''}]")
            for x in xs:
                lines.append(_line(len(flat), x))
                flat.append(x)
        fut = None
        if ai and flat:
            prompt = ("아래는 종목별 최근 기사 후보다. 종목마다 장기(1~3년) 기업 가치 판단에 도움이 되는 기사(실적 전망, 신사업, 경쟁, 규제, 증권사 목표가 등)를 최대 3개 골라 "
                      "한국어 제목과 핵심 요약 한두 문장을 쓰고, 기사들에서 읽히는 미래 가치 요지를 한 문장으로 써라. 단기 주가 등락만 다룬 기사는 빼라.\n"
                      '출력 형식: {"종목":{"view":"한 문장 요지","items":[{"i":번호,"ko":"한국어 제목","sum":"요약"}]}}\n' + "\n".join(lines))
            try:
                j = ask_ai(ai, prompt)
                fut = []
                for t in cands:
                    v = j.get(t) or {}
                    fut.append({"ticker": t, "name": names.get(t) or "", "view": (v.get("view") or "").strip(), "items": _pick(flat, v.get("items"), 3)})
            except Exception as e:  # noqa: BLE001
                errs.append(f"future: {e}")
        if fut is None:
            fut = [fut_old[t] if t in fut_old and _age_h((old.get("future_meta") or {}).get("updated")) < 12
                   else {"ticker": t, "name": names.get(t) or "", "view": "", "items": _plain(cands[t], 3)} for t in cands]
        out["future"], out["future_meta"] = fut, {"updated": now.isoformat(timespec="seconds")}
    else:
        out["future"], out["future_meta"] = old.get("future") or [], old.get("future_meta") or {}

    out["ai"] = "ok" if not errs else "; ".join(errs)[:300]
    if not ai:
        out["ai"] = "AI 중계 주소 없음 (data/config.json 의 ai)"
    for p, obj in ((out_p, out), (pool_p, pool)):
        tmp = p.with_suffix(".tmp")
        tmp.write_text(json.dumps(obj, ensure_ascii=False, indent=1), encoding="utf-8")
        tmp.replace(p)
    log("뉴스 저장" + ("" if not errs else " (AI 오류: " + out["ai"] + ")"))
    return out
