"""Alice: a scripted hub client that befriends whoever asks, then chats, sends an image, types, and
announces a stream. Runs until the duration passes. Usage: python bot.py [seconds]"""
import sys, time, io, uuid
from hubproto import Client, varint, utf, u, boolean, long_, barr, NIL, A_ID
from PIL import Image, ImageDraw

def dmkey(a, b):
    sa, sb = str(a), str(b)
    return "dm:" + (sa + "_" + sb if sa <= sb else sb + "_" + sa)

DURATION = float(sys.argv[1]) if len(sys.argv) > 1 else 150
NAME = sys.argv[2] if len(sys.argv) > 2 else "Alice"
A_ID = uuid.UUID(sys.argv[3]) if len(sys.argv) > 3 else A_ID
A = Client(A_ID, NAME)
A.expect(["HubInfo"])
A.send(2, u(A_ID) + utf(NAME) + varint(1) + utf("bot"))
A.expect(["Welcome", "Snapshot"])
A.send(25, varint(2) + utf("play.example.com:25565") + utf("the_nether") + utf("mining ancient debris") + long_(0))

def png_bytes():
    im = Image.new("RGB", (320, 200), (40, 120, 200))
    d = ImageDraw.Draw(im)
    d.rectangle((20, 20, 300, 180), outline=(255, 255, 255), width=4)
    d.ellipse((110, 50, 210, 150), fill=(230, 140, 60))
    d.text((30, 30), "from Alice", fill=(255, 255, 255))
    b = io.BytesIO(); im.save(b, "PNG"); return b.getvalue()

def send_blob(bid, kind, data, target, meta):
    A.send(70, bytes([0]) + barr(utf(bid) + utf(kind) + varint(len(data)) + varint(0) + utf("") + utf(target) + utf(meta)))
    for i in range(0, len(data), 24000):
        A.send(70, bytes([1]) + barr(utf(bid) + varint(i // 24000) + barr(data[i:i+24000])))
    A.send(70, bytes([2]) + barr(utf(bid)))

def chat(key, text, kind="", attach="", meta=""):
    A.send(30, utf(key) + utf("") + u(NIL) + utf("") + utf(text) + utf(kind) + utf(attach) + utf(meta) + long_(0))

end = time.time() + DURATION
friends = set()
greeted = set()
stream_id = None
watched = set()
got_frames = 0
frame_seq = 0
next_frame = 0.0

def jpeg_frame(n):
    im = Image.new("RGB", (320, 180), (30, 30, 40))
    d = ImageDraw.Draw(im)
    x = (n * 9) % 300
    d.ellipse((x, 60, x + 40, 100), fill=(230, 140, 60))
    d.rectangle((0, 0, 319, 179), outline=(200, 200, 200))
    d.text((8, 8), "bot stream frame %d" % n, fill=(255, 255, 255))
    b = io.BytesIO(); im.save(b, "JPEG", quality=60); return b.getvalue()
print(NAME, "online; waiting for requests...", flush=True)
A.send(55, utf("Bot stream"))
while time.time() < end:
    if stream_id and time.time() >= next_frame:
        next_frame = time.time() + 0.3
        frame_seq += 1
        data = jpeg_frame(frame_seq)
        A.send(60, utf(stream_id) + varint(frame_seq) + varint(320) + varint(180) + long_(int(time.time() * 1000)) + varint(len(data)))
        send_blob("%08x" % ((0x0f000000 + frame_seq) & 0xFFFFFFFF), "frame", data, "s:" + stream_id, "seq=%d;w=320;h=180;t=%d" % (frame_seq, int(time.time() * 1000)))
    for kind, body in A.recv(0.25):
        if kind == "Ack" and body["ctx"] == "stream_start":
            stream_id = body["value"]; print("streaming as", stream_id, flush=True)
        elif kind == "StreamEnded":
            if body["id"] == stream_id: stream_id = None
        elif kind == "Ping":
            A.send(7, long_(body["t"]))
        elif kind == "FriendRequestIn":
            who = body["ref"][0]
            print("request from", body["ref"][1], "-> accepting", flush=True)
            A.send(12, u(who))
        elif kind == "FriendUpdate":
            who = body["ref"][0]
            if who in friends: continue
            friends.add(who)
            key = dmkey(A_ID, who)
            print("now friends with", body["ref"][1], "-> chatting on", key, flush=True)
            A.send(31, utf(key) + u(NIL) + boolean(True))
            time.sleep(1.5)
            chat(key, "hey " + body["ref"][1] + "! welcome to the hub :)")
            time.sleep(1.0)
            bid = "%08x" % (int(time.time()) & 0xFFFFFFFF)
            send_blob(bid, "image", png_bytes(), "p:" + str(who), "mime=image/png")
            time.sleep(0.5)
            chat(key, "here is a picture", "image", bid, "mime=image/png")
            time.sleep(0.5)
            A.send(50, u(who) + utf("server") + utf("play.example.com:25565") + utf("Alice's server"))
            A.send(55, utf("Alice mining"))
        elif kind == "Chat":
            m = body["msg"]
            if m["frm"][0] != A_ID and m["id"] not in greeted:
                greeted.add(m["id"])
                print("got message:", repr(m["text"]), "attach:", m["kind"], flush=True)
                if m["text"]:
                    A.send(31, utf(body["key"]) + u(NIL) + boolean(True))
                    time.sleep(1.2)
                    chat(body["key"], "you said: " + m["text"][:60])
        elif kind == "Error":
            print("error:", body, flush=True)
        elif kind == "PresenceUpdate":
            print("presence:", body["presence"]["state"], body["presence"]["server"], body["presence"]["dim"], flush=True)
        elif kind == "StreamUpdate":
            if body["owner"][0] != A_ID and body["id"] not in watched:
                watched.add(body["id"]); A.send(57, utf(body["id"]) + boolean(True)); print("watching", body["owner"][1], body["id"], flush=True)
            else: print("stream viewers:", body["viewers"], flush=True)
        elif kind == "Blob.End":
            got_frames += 1
            if got_frames % 10 == 1: print("received frame blobs:", got_frames, flush=True)
        elif kind in ("Typing", "Pong", "RequestsUpdate", "Ack", "Blob.Start", "Blob.Chunk", "StreamFrame", "StreamWatch"):
            pass
        else:
            print("event:", kind, flush=True)
print("Alice leaving", flush=True)
A.close()
