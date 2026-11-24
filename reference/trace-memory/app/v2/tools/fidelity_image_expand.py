#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""离线扩样：missing 引号 60 条 + 内容数字 27 条，单图 high，local viz/shots。"""
import base64, json, math, re, sqlite3, sys, urllib.request
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
ROOT = Path(r"<workspace>"); MEM=ROOT/"raw/v2/device/host_memory.db"; EVID=ROOT/"raw/v2/device/evidence.db"
SHOTS=ROOT/"viz/shots"; OUT=ROOT/"raw/v2/fidelity-image-expand.md"
KEY=(ROOT/".secrets/deepseek.key").read_text().strip()
Q=re.compile(r"[“\"「『]([^”\"」』\n]{2,40})[”\"」』]")
NUM=re.compile(r"\d+(?:[.,]\d+)*")

def norm(s): return re.sub(r"\s+","",s or "").replace(",","").replace("，","")
def local(ref):
    p=SHOTS/ref.replace("shots/","")
    return p if p.exists() and p.stat().st_size>0 else None
def shots(cid,ec,limit=3):
    ids=MC.execute("select block_ids from chains where id=?",(cid,)).fetchone(); out=[]
    if ids:
        for b in ids[0].split(","):
            try: bid=int(b.strip())
            except: continue
            r=ec.execute("select shot_ref from traces where id=?",(bid,)).fetchone()
            if r and r[0] and r[0] not in out: out.append(r[0])
            if len(out)>=limit: break
    return [p for p in (local(x) for x in out) if p]
def ask(path,text):
    b64=base64.b64encode(path.read_bytes()).decode()
    body={"model":"deepseek-chat","messages":[{"role":"user","content":[
        {"type":"text","text":"这张截图里有没有出现「%s」这几个字？只回答 有 / 没有 / 看不清 三者之一。"%text},
        {"type":"image_url","image_url":{"url":"data:image/jpeg;base64,"+b64,"detail":"high"}}]}],
        "max_tokens":16,"temperature":0}
    req=urllib.request.Request("https://api.deepseek.com/v1/chat/completions",data=json.dumps(body).encode(),
        headers={"Authorization":"Bearer "+KEY,"Content-Type":"application/json"})
    try:
        v=json.loads(urllib.request.urlopen(req,timeout=180).read().decode())["choices"][0]["message"]["content"]
    except Exception: return "看不清"
    if "没有" in v: return "没有"
    if "看不清" in v: return "看不清"
    if "有" in v: return "有"
    return "看不清"

def main():
    global MC
    MC=sqlite3.connect(MEM); ec=sqlite3.connect(EVID)
    quotes=[]; nums=[]
    for cid,body,ids in MC.execute("select id,body,block_ids from chains where level=0 and status='done' order by t0,id"):
        if not body: continue
        orig=[]
        for b in (ids or "").split(","):
            try: bid=int(b.strip())
            except: continue
            r=ec.execute("select text,page_title,activity,pkg from traces where id=?",(bid,)).fetchone()
            if r: orig += [x for x in r if x]
        no=norm("\n".join(orig))
        for m in Q.finditer(body):
            q=norm(m.group(1))
            if q and q not in no: quotes.append((cid,m.group(1),body))
        # content numbers
        for m in NUM.finditer(body):
            raw=m.group()
            if len(raw)<2: continue
            if ":" in body[max(0,m.start()-4):m.end()+2]: continue
            if raw.replace(",","") in no: continue
            nums.append((cid,raw,body))
    # eligible local shots
    qe=[x for x in quotes if shots(x[0],ec)]
    ne=[x for x in nums if shots(x[0],ec)]
    sample_q=qe[:60]; sample_n=ne[:27]
    out=["# 离线扩样 high detail 复核","", "来源图：viz/shots（本地 246 张），缺图跳过。",""]
    def run(items,kind):
        c={"有":0,"没有":0,"看不清":0,"无图":0}; det=[]
        for i,(cid,text,body) in enumerate(items,1):
            ps=shots(cid,ec); verdict="无图"; used=0
            for p in ps:
                used+=1; v=ask(p,text)
                if v=="有": verdict="有"; break
                if v=="没有": verdict="没有"
                if v=="看不清": verdict="看不清"
            c[verdict]+=1; det.append((cid,text,verdict,used,body))
            print(kind,i,verdict,used,text[:24],flush=True)
        return c,det
    cq,dq=run(sample_q,"Q"); cn,dn=run(sample_n,"N")
    def pct(x,n): return 0.0 if n==0 else x*100.0/n
    def wilson(k,n):
        if n==0: return (0,0)
        z=1.96; p=k/n; d=1+z*z/n
        mid=(p+z*z/(2*n))/d; half=z*math.sqrt(p*(1-p)/n+z*z/(4*n*n))/d
        return (max(0,mid-half)*100, min(1,mid+half)*100)
    nq=len(sample_q); nq_ver=cq["有"]+cq["没有"]+cq["看不清"]
    lo,hi=wilson(cq["没有"],max(1,nq_ver))
    out += ["## 汇总", "- 可验证引号样本: %d（本地有图）；无图跳过 %d"%(nq,len(qe)-nq if len(qe)>nq else 0),
            "- OCR 漏读: %d (%.0f%%)"%(cq["有"],pct(cq["有"],max(1,nq))),
            "- 仍无法溯源: %d (%.0f%%)"%(cq["没有"],pct(cq["没有"],max(1,nq))),
            "- 无法判断: %d"%cq["看不清"],
            "- 按 y=%.1f%% 外推 281 条：约 %.0f 条（95%% CI %.1f%%–%.1f%% → %.0f–%.0f 条）"%(
                pct(cq["没有"],max(1,nq_ver)), pct(cq["没有"],max(1,nq_ver))*2.81, lo,hi,lo*2.81,hi*2.81),
            "", "- 内容数字样本: %d，有 %d，没有 %d，看不清 %d"%(len(sample_n),cn["有"],cn["没有"],cn["看不清"]),""]
    out.append("## 引号逐条")
    for cid,t,v,u,b in dq:
        out.append("- chain#%d 「%s」 → **%s**（%d 张）"%(cid,t,v,u))
        if v=="没有": out.append("  - body: %s"%b[:220].replace("\n"," "))
    out.append("\n## 数字逐条")
    for cid,t,v,u,b in dn: out.append("- chain#%d 数字「%s」→ **%s**（%d 张）"%(cid,t,v,u))
    OUT.write_text("\n".join(out),encoding="utf-8")
    print("written",OUT,cq,cn,"eligible",len(qe),len(ne))

if __name__=="__main__": main()
