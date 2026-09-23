import socket, struct, uuid, time, sys, os

HOST, PORT = "127.0.0.1", 25580
NIL = uuid.UUID(int=0)

# ---------------- encoders (FriendlyByteBuf compatible)
def varint(n):
    out = bytearray()
    n &= 0xFFFFFFFF
    while True:
        b = n & 0x7F; n >>= 7
        if n: out.append(b | 0x80)
        else: out.append(b); return bytes(out)
def utf(s): b = s.encode("utf-8"); return varint(len(b)) + b
def u(uid): return struct.pack(">QQ", uid.int >> 64, uid.int & ((1 << 64) - 1))
def boolean(b): return b"\x01" if b else b"\x00"
def long_(n): return struct.pack(">q", n)
def barr(b): return varint(len(b)) + b

# ---------------- decoders
class R:
    def __init__(self, b): self.b = b; self.i = 0
    def byte(self): v = self.b[self.i]; self.i += 1; return v
    def varint(self):
        n = 0; sh = 0
        while True:
            x = self.byte(); n |= (x & 0x7F) << sh; sh += 7
            if not (x & 0x80): return n if n < (1 << 31) else n - (1 << 32)
    def utf(self): n = self.varint(); s = self.b[self.i:self.i+n].decode("utf-8"); self.i += n; return s
    def uuid(self): hi, lo = struct.unpack(">QQ", self.b[self.i:self.i+16]); self.i += 16; return uuid.UUID(int=(hi << 64) | lo)
    def bool(self): return self.byte() == 1
    def long(self): v = struct.unpack(">q", self.b[self.i:self.i+8])[0]; self.i += 8; return v
    def barr(self): n = self.varint(); v = self.b[self.i:self.i+n]; self.i += n; return v
    def list(self, fn): n = self.varint(); return [fn() for _ in range(n)]
    def ref(self): return (self.uuid(), self.utf())
    def presence(self): return dict(state=["OFFLINE","MENU","IN_GAME","AWAY"][self.varint()], server=self.utf(), dim=self.utf(), status=self.utf(), since=self.long())
    def friend(self): return dict(ref=self.ref(), nick=self.utf(), note=self.utf(), presence=self.presence(), since=self.long())
    def request(self): return dict(ref=self.ref(), at=self.long())
    def group(self): return dict(id=self.utf(), name=self.utf(), owner=self.uuid(), members=self.list(self.ref), voice=self.utf(), created=self.long())
    def ginvite(self): return dict(group=self.group(), frm=self.ref(), at=self.long())
    def message(self): return dict(id=self.utf(), frm=self.ref(), text=self.utf(), kind=self.utf(), attach=self.utf(), meta=self.utf(), at=self.long())
    def thread(self):
        key = self.utf(); title = self.utf(); last = self.message() if self.bool() else None; return dict(key=key, title=title, last=last, unread=self.varint())
    def stream(self): return dict(id=self.utf(), owner=self.ref(), title=self.utf(), viewers=self.varint(), started=self.long())

def decode(b):
    r = R(b); k = r.byte()
    if k == 1: return ("HubInfo", dict(name=r.utf(), protocol=r.varint(), online=r.bool()))
    if k == 3: return ("Welcome", dict(hub=r.utf(), you=r.ref(), online=r.bool(), time=r.long()))
    if k == 4: return ("Error", dict(code=r.varint(), msg=r.utf(), ctx=r.utf()))
    if k == 5: return ("Ack", dict(ctx=r.utf(), value=r.utf()))
    if k == 6: return ("Ping", dict(t=r.long()))
    if k == 7: return ("Pong", dict(t=r.long()))
    if k == 8: return ("Snapshot", dict(self=r.ref(), friends=r.list(r.friend), rin=r.list(r.request), rout=r.list(r.request), blocked=r.list(r.ref), groups=r.list(r.group), ginv=r.list(r.ginvite), threads=r.list(r.thread), streams=r.list(r.stream)))
    if k == 11: return ("FriendRequestIn", r.request())
    if k == 17: return ("FriendUpdate", r.friend())
    if k == 18: return ("FriendRemoved", dict(uuid=r.uuid(), reason=r.utf()))
    if k == 19: return ("RequestsUpdate", dict(rin=r.list(r.request), rout=r.list(r.request)))
    if k == 20: return ("BlockedUpdate", r.list(r.ref))
    if k == 26: return ("PresenceUpdate", dict(uuid=r.uuid(), presence=r.presence()))
    if k == 30: return ("Chat", dict(key=r.utf(), msg=r.message()))
    if k == 31: return ("Typing", dict(key=r.utf(), who=r.uuid(), typing=r.bool()))
    if k == 33: return ("History", dict(key=r.utf(), msgs=r.list(r.message), more=r.bool()))
    if k == 42: return ("GroupInviteIn", r.ginvite())
    if k == 48: return ("GroupUpdate", r.group())
    if k == 49: return ("GroupRemoved", dict(id=r.utf()))
    if k == 51: return ("InviteIn", dict(frm=r.ref(), kind=r.utf(), address=r.utf(), label=r.utf(), at=r.long()))
    if k == 58: return ("StreamUpdate", r.stream())
    if k == 59: return ("StreamEnded", dict(id=r.utf()))
    if k == 60: return ("StreamFrame", dict(id=r.utf(), seq=r.varint(), w=r.varint(), h=r.varint(), sent=r.long(), bytes=r.varint()))
    if k == 70:
        kind = r.byte(); data = r.barr(); rr = R(data)
        if kind == 0: return ("Blob.Start", dict(id=rr.utf(), kind=rr.utf(), total=rr.varint(), dur=rr.varint(), sender=rr.utf(), target=rr.utf(), meta=rr.utf()))
        if kind == 1: return ("Blob.Chunk", dict(id=rr.utf(), index=rr.varint(), n=len(rr.barr())))
        return ("Blob.End", dict(id=rr.utf()))
    return ("kind%d" % k, {})

# ---------------- client
class Client:
    def __init__(self, uid, name):
        self.uid, self.name = uid, name
        self.s = socket.create_connection((HOST, PORT), timeout=10)
        self.buf = b""
    def send(self, kind, payload=b""):
        f = bytes([kind]) + payload
        self.s.sendall(struct.pack(">I", len(f)) + f)
    def recv(self, wait=0.6):
        out = []; end = time.time() + wait
        self.s.settimeout(0.1)
        while time.time() < end:
            try:
                d = self.s.recv(65536)
                if not d: break
                self.buf += d
            except socket.timeout:
                pass
            while len(self.buf) >= 4:
                n = struct.unpack(">I", self.buf[:4])[0]
                if len(self.buf) < 4 + n: break
                out.append(decode(self.buf[4:4+n])); self.buf = self.buf[4+n:]
        return out
    def expect(self, kinds, wait=0.8):
        got = self.recv(wait); names = [g[0] for g in got]
        ok = all(k in names for k in kinds)
        print(("PASS" if ok else "FAIL"), self.name, "expected", kinds, "got", names)
        if not ok: FAILS.append((self.name, kinds, names))
        return got
    def close(self): self.s.close()

FAILS = []
A_ID = uuid.UUID("11111111-1111-1111-1111-111111111111")
B_ID = uuid.UUID("22222222-2222-2222-2222-222222222222")

