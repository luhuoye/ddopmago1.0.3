import zlib,re,sys,bisect
def be(b,o,w): return int.from_bytes(b[o:o+w],'big')
class M:
    def __init__(s,path,mdd=False):
        d=open(path,'rb').read(); s.d=d
        hl=be(d,0,4); head=d[4:4+hl].decode('utf-16le'); p=4+hl+4
        at=lambda n:(re.search(n+'="(.*?)"',head,re.S) or [None,''])[1]
        s.title=at('Title'); s.ver=float(at('GeneratedByEngineVersion') or 1.2)
        enc=at('Encoding').upper(); s.enc='UTF-16LE' if mdd else ('UTF-8' if not enc else ('GB18030' if enc in('GBK','GB2312') else ('UTF-16LE' if enc=='UTF-16' else at('Encoding'))))
        s.encrypt=at('Encrypted'); print(path,s.ver,s.enc,'enc',s.encrypt,'title',s.title[:40])
        v2=s.ver>=2; nw=8 if v2 else 4
        kh=d[p:p+(nw*5 if v2 else nw*4)]; p+=len(kh)
        nums=[be(kh,i*nw,nw) for i in range(len(kh)//nw)]
        if v2: nb,ne,idc,isz,bsz=nums
        else: nb,ne,isz,bsz=nums
        if v2: p+=4
        info=d[p:p+isz]; p+=isz; blocks=d[p:p+bsz]; p+=bsz
        if v2: info=zlib.decompress(info[8:])
        u16=s.enc.startswith('UTF-16'); bw=2 if v2 else 1; tt=1 if v2 else 0
        i=0;comp=[]
        while i<len(info):
            i+=nw; hs=be(info,i,bw); i+=bw; i+=(hs+tt)*(2 if u16 else 1)
            ts=be(info,i,bw); i+=bw; i+=(ts+tt)*(2 if u16 else 1)
            comp.append(be(info,i,nw)); i+=nw+nw
        s.keys=[];s.ids=[];off=0;types=set()
        for c in comp:
            t=blocks[off]; types.add(t); data=zlib.decompress(blocks[off+8:off+c]) if t==2 else blocks[off+8:off+c]; off+=c
            q=0
            while q<len(data):
                idv=be(data,q,nw); st=q+nw; e=st
                if u16:
                    while not(data[e]==0 and data[e+1]==0): e+=2
                else:
                    while data[e]!=0: e+=1
                s.keys.append(data[st:e].decode(s.enc,'replace')); s.ids.append(idv); q=e+(2 if u16 else 1)
        print('key block types',types,'keys',len(s.keys),'declared',ne)
        rh=d[p:p+nw*4]; p+=nw*4
        nrec=be(rh,0,nw); rsz=be(rh,nw*2,nw)
        ri=d[p:p+rsz]; p+=rsz
        s.blk=[];acc=0;pos=p;rt=set()
        for b in range(nrec):
            cs=be(ri,b*2*nw,nw); ds=be(ri,b*2*nw+nw,nw); rt.add(d[pos]); s.blk.append((pos,cs,acc,ds)); pos+=cs; acc+=ds
        s.total=acc; print('rec blocks',nrec,'types',rt,'total',acc)
        s.sorted=sorted(s.ids)
        s.cache={}
    def rec(s,i):
        st=s.ids[i]; u=bisect.bisect_right(s.sorted,st); end=s.sorted[u] if u<len(s.sorted) else s.total
        out=b''; pos=st
        starts=[b[2] for b in s.blk]; bi=bisect.bisect_right(starts,st)-1
        while pos<end and bi<len(s.blk):
            if bi not in s.cache:
                p0,cs,a0,ds=s.blk[bi]; s.cache[bi]=zlib.decompress(s.d[p0+8:p0+cs]) if s.d[p0]==2 else s.d[p0+8:p0+cs]
            dd=s.cache[bi]; a0=s.blk[bi][2]; f=pos-a0; t=min(len(dd),end-a0); out+=dd[f:t]; pos=a0+t; bi+=1
        return out
