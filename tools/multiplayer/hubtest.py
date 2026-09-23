from hubproto import *

def hello(c):
    c.expect(["HubInfo"])
    c.send(2, u(c.uid) + utf(c.name) + varint(1) + utf("deadbeef"))
    return c.expect(["Welcome", "Snapshot"])

def dmkey(a, b):
    sa, sb = str(a), str(b)
    return "dm:" + (sa + "_" + sb if sa <= sb else sb + "_" + sa)

A = Client(A_ID, "Alice"); B = Client(B_ID, "Bob")
hello(A); hello(B)

# presence
A.send(25, varint(2) + utf("play.test:25565") + utf("overworld") + utf("building") + long_(0))
B.send(25, varint(1) + utf("") + utf("") + utf("") + long_(0))
A.recv(0.3); B.recv(0.3)

# friend request by name, accept
A.send(10, u(NIL) + utf("Bob"))
A.expect(["RequestsUpdate", "Ack"])
B.expect(["FriendRequestIn", "RequestsUpdate"])
B.send(12, u(A_ID))
gotA = A.expect(["RequestsUpdate", "FriendUpdate"])
gotB = B.expect(["RequestsUpdate", "FriendUpdate"])
fu = [g for g in gotB if g[0] == "FriendUpdate"][0][1]
print("  Bob sees Alice presence:", fu["presence"]["state"], fu["presence"]["server"], fu["presence"]["dim"], fu["presence"]["status"])
if fu["presence"]["state"] != "IN_GAME": FAILS.append(("presence in FriendUpdate", fu))

# presence change fan-out
A.send(25, varint(2) + utf("play.test:25565") + utf("the_nether") + utf("building") + long_(0))
B.expect(["PresenceUpdate"])
# nickname
B.send(21, u(A_ID) + utf("Al") + utf("the builder"))
B.expect(["FriendUpdate"])

# DM
key = dmkey(A_ID, B_ID)
A.send(30, utf(key) + utf("") + u(NIL) + utf("") + utf("hello bob") + utf("") + utf("") + utf("") + long_(0))
A.expect(["Chat"]); gb = B.expect(["Chat"])
msg = [g for g in gb if g[0] == "Chat"][0][1]["msg"]
print("  chat stamped id=%s from=%s at=%d" % (msg["id"], msg["frm"][1], msg["at"]))
if msg["frm"][0] != A_ID or not msg["id"]: FAILS.append(("chat stamping", msg))
# typing
A.send(31, utf(key) + u(NIL) + boolean(True))
B.expect(["Typing"])
# history
B.send(32, utf(key) + long_(0) + varint(50))
gh = B.expect(["History"])
h = [g for g in gh if g[0] == "History"][0][1]
print("  history msgs=%d more=%s" % (len(h["msgs"]), h["more"]))
if len(h["msgs"]) != 1: FAILS.append(("history count", h))
# mark read
B.send(34, utf(key) + long_(int(time.time()*1000)))

# groups
A.send(40, utf("Builders"))
gg = A.expect(["Ack", "GroupUpdate"])
gid = [g for g in gg if g[0] == "Ack"][0][1]["value"]
A.send(41, utf(gid) + u(B_ID))
A.expect(["Ack"]); B.expect(["GroupInviteIn"])
B.send(43, utf(gid) + boolean(True))
A.expect(["GroupUpdate"]); gu = B.expect(["GroupUpdate"])
grp = [g for g in gu if g[0] == "GroupUpdate"][0][1]
print("  group members:", [m[1] for m in grp["members"]])
if len(grp["members"]) != 2: FAILS.append(("group members", grp))
# group chat
B.send(30, utf("g:" + gid) + utf("") + u(NIL) + utf("") + utf("hi group") + utf("") + utf("") + utf("") + long_(0))
A.expect(["Chat"]); B.expect(["Chat"])
# rename by owner, voice set
A.send(45, utf(gid) + utf("Builders 2"))
A.expect(["GroupUpdate"]); B.expect(["GroupUpdate"])
B.send(47, utf(gid) + utf("33333333-3333-3333-3333-333333333333"))
A.expect(["GroupUpdate"]); B.expect(["GroupUpdate"])

# invite to server
A.send(50, u(B_ID) + utf("server") + utf("play.test:25565") + utf("Test server"))
A.expect(["Ack"]); B.expect(["InviteIn"])

# streams + frame blob
A.send(55, utf("Alice build"))
gs = A.expect(["Ack", "StreamUpdate"]); sid = [g for g in gs if g[0] == "Ack"][0][1]["value"]
B.expect(["StreamUpdate"])
B.send(57, utf(sid) + boolean(True))
A.expect(["StreamUpdate"]); B.expect(["StreamUpdate"])
A.send(60, utf(sid) + varint(1) + varint(640) + varint(360) + long_(int(time.time()*1000)) + varint(3000))
frame = bytes(range(256)) * 12   # 3072 bytes
start = utf("abcdef01") + utf("frame") + varint(len(frame)) + varint(0) + utf("") + utf("s:" + sid) + utf("seq=1")
A.send(70, bytes([0]) + barr(start))
A.send(70, bytes([1]) + barr(utf("abcdef01") + varint(0) + barr(frame)))
A.send(70, bytes([2]) + barr(utf("abcdef01")))
gb = B.expect(["StreamFrame", "Blob.Start", "Blob.Chunk", "Blob.End"])
bs = [g for g in gb if g[0] == "Blob.Start"]
if bs: print("  frame blob sender stamped as:", bs[0][1]["sender"], "target", bs[0][1]["target"])
if bs and bs[0][1]["sender"] != "Alice": FAILS.append(("blob sender stamp", bs))
A.send(56, utf(sid)); A.expect(["StreamEnded"]); B.expect(["StreamEnded"])

# DM image blob (stored on hub) + media request from Bob after it completes
img = b"\x89PNG" + bytes(5000)
start = utf("0badf00d") + utf("image") + varint(len(img)) + varint(0) + utf("") + utf("p:" + str(B_ID)) + utf("mime=image/png")
A.send(70, bytes([0]) + barr(start))
A.send(70, bytes([1]) + barr(utf("0badf00d") + varint(0) + barr(img)))
A.send(70, bytes([2]) + barr(utf("0badf00d")))
B.expect(["Blob.Start", "Blob.Chunk", "Blob.End"])
A.send(30, utf(key) + utf("") + u(NIL) + utf("") + utf("") + utf("image") + utf("0badf00d") + utf("mime=image/png") + long_(0))
A.expect(["Chat"]); B.expect(["Chat"])
time.sleep(0.3)
B.send(35, utf("0badf00d"))
gm = B.expect(["Blob.Start", "Blob.Chunk", "Blob.End"])
st = [g for g in gm if g[0] == "Blob.Start"]
if st: print("  stored media re-sent: kind=%s total=%d target=%s" % (st[0][1]["kind"], st[0][1]["total"], st[0][1]["target"]))

# blocked / not friends path
B.send(16, u(NIL) + utf("Alice") + boolean(True))
B.expect(["FriendRemoved", "RequestsUpdate", "BlockedUpdate"])
A.expect(["FriendRemoved", "RequestsUpdate"])
A.send(10, u(B_ID) + utf("Bob"))
A.expect(["Error"])
B.send(16, u(A_ID) + utf("") + boolean(False)); B.expect(["BlockedUpdate"])

# pings are exempt from the request bucket
for i in range(30): A.send(6, long_(i))
gp = A.recv(0.8); print("  pongs:", sum(1 for g in gp if g[0] == "Pong"))

# reconnect as Alice from a second socket -> first gets REPLACED error
A2 = Client(A_ID, "Alice"); hello(A2)
ga = A.recv(0.6); print("  old Alice link got:", [g[0] + ":" + str(g[1].get("code", "")) for g in ga])
if not any(g[0] == "Error" and g[1]["code"] == 440 for g in ga): FAILS.append(("replaced", ga))
A2.close(); A.close(); B.close()
print("")
print("RESULT:", "ALL PASS" if not FAILS else "FAILURES: %d" % len(FAILS))
for f in FAILS: print("  -", f)
