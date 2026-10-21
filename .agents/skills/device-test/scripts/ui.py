#!/usr/bin/env python3
"""Drive the Android device under test through adb and uiautomator.

Commands:
  device                      Exit 0 if one device is connected and unlocked. Else print why and exit 1.
  list                        Print every element with text or a content description, and its bounds.
  tap TEXT                    Tap the first element whose text or description is exactly TEXT.
  tap ~TEXT                   Same, but TEXT may be part of the text or description.
  scroll-tap TEXT [N]         Scroll down up to N times (default 6) until TEXT shows, then tap it.
  has TEXT                    Exit 0 if TEXT shows on the screen, 1 if not. Prints nothing.
  wait TEXT [SECONDS]         Wait until TEXT shows (default 15 s). Exit 1 on timeout.
  shot PATH [MAX]             Save a screenshot to PATH and a copy scaled to MAX px (default 1000) to
                              PATH with "-small" before the extension. Read the small one.
  pixel PATH X Y              Print the RGB hex of one pixel of a full-size screenshot.

The adb binary is $ADB, else ../platform-tools/adb next to the repository, else adb on PATH.
"""
import os
import re
import shutil
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))


def adb_path():
    if os.environ.get("ADB"):
        return os.environ["ADB"]
    local = os.path.join(os.path.dirname(REPO), "platform-tools", "adb")
    return local if os.path.exists(local) else (shutil.which("adb") or "adb")


ADB = adb_path()


def adb(*args, text=True):
    return subprocess.run([ADB, *args], capture_output=True, text=text)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml").stdout
    for match in re.finditer(r"<node [^>]*>", xml):
        node = match.group(0)

        def attr(key):
            found = re.search(key + r'="([^"]*)"', node)
            return found.group(1) if found else ""

        bounds = [int(x) for x in re.findall(r"\d+", attr("bounds"))]
        yield attr("text"), attr("content-desc"), attr("resource-id").split("/")[-1], bounds


def find(want):
    partial = want.startswith("~")
    needle = want[1:] if partial else want
    for text, desc, _, bounds in nodes():
        if (partial and (needle in text or needle in desc)) or (not partial and needle in (text, desc)):
            return text or desc, bounds
    return None


def tap(want):
    found = find(want)
    if not found:
        print("NOT FOUND", want)
        return False
    label, b = found
    x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    print("tapped", repr(label), x, y)
    return True


def screen_size():
    out = adb("shell", "wm", "size").stdout
    sizes = re.findall(r"(\d+)x(\d+)", out)
    width, height = map(int, sizes[-1])
    # wm size reports the portrait size. The rotation decides which side is the width now.
    rotation = re.search(r"mCurrentRotation=(?:ROTATION_)?(\d+)", adb("shell", "dumpsys", "input").stdout)
    if rotation and rotation.group(1) in ("1", "90", "3", "270"):
        width, height = height, width
    return width, height


def scroll_down():
    width, height = screen_size()
    x = width // 2
    adb("shell", "input", "swipe", str(x), str(height * 3 // 4), str(x), str(height // 3), "300")
    time.sleep(1)


def device():
    lines = [line for line in adb("devices").stdout.splitlines()[1:] if line.strip()]
    if not lines:
        print("No device. Ask the user to connect the phone (another cable if it keeps dropping).")
        return False
    if not lines[0].endswith("device"):
        print("Device not ready:", lines[0])
        return False
    adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb("shell", "wm", "dismiss-keyguard")
    time.sleep(1)
    if "isKeyguardShowing=true" in adb("shell", "dumpsys", "window").stdout:
        print("The phone is locked. Ask the user to unlock it; adb cannot enter a PIN.")
        return False
    return True


def shot(path, size=1000):
    data = adb("exec-out", "screencap", "-p", text=False).stdout
    if not data:
        print("No screenshot: is the device connected?")
        sys.exit(1)
    with open(path, "wb") as out:
        out.write(data)
    root, ext = os.path.splitext(path)
    small = root + "-small" + ext
    shutil.copyfile(path, small)
    subprocess.run(["sips", "-Z", str(size), small], capture_output=True)
    print(small)


def pixel(path, x, y):
    tmp = path + ".px.png"
    subprocess.run(["sips", "-c", "1", "1", "--cropOffset", str(y), str(x), path, "--out", tmp], capture_output=True)
    subprocess.run(["sips", "-s", "format", "bmp", tmp, "--out", tmp + ".bmp"], capture_output=True)
    with open(tmp + ".bmp", "rb") as bmp:
        b, g, r = bmp.read()[-4:-1]
    os.remove(tmp)
    os.remove(tmp + ".bmp")
    print("#%02x%02x%02x" % (r, g, b))


def main(argv):
    if not argv:
        print(__doc__)
        return 2
    command, rest = argv[0], argv[1:]
    if command == "device":
        return 0 if device() else 1
    if command == "list":
        for text, desc, rid, bounds in nodes():
            if text or desc:
                print(repr(text), repr(desc), rid, bounds)
    elif command == "tap":
        return 0 if tap(rest[0]) else 1
    elif command == "scroll-tap":
        for _ in range(int(rest[1]) if len(rest) > 1 else 6):
            if find(rest[0]):
                return 0 if tap(rest[0]) else 1
            scroll_down()
        print("NOT FOUND", rest[0])
        return 1
    elif command == "has":
        return 0 if find(rest[0]) else 1
    elif command == "wait":
        end = time.time() + (float(rest[1]) if len(rest) > 1 else 15)
        while time.time() < end:
            if find(rest[0]):
                return 0
            time.sleep(1)
        print("TIMEOUT", rest[0])
        return 1
    elif command == "shot":
        shot(rest[0], int(rest[1]) if len(rest) > 1 else 1000)
    elif command == "pixel":
        pixel(rest[0], int(rest[1]), int(rest[2]))
    else:
        print(__doc__)
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
