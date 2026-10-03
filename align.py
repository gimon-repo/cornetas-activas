"""Reescribe un zip alineando a 4 bytes las entradas sin comprimir (equivale a zipalign -p 4)."""
import sys, zipfile

src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zin, open(dst, "wb") as raw:
    with zipfile.ZipFile(raw, "w") as zout:
        for info in zin.infolist():
            data = zin.read(info)
            ni = zipfile.ZipInfo(info.filename, date_time=info.date_time)
            ni.compress_type = info.compress_type
            ni.external_attr = info.external_attr
            if ni.compress_type == zipfile.ZIP_STORED:
                offset = raw.tell() + 30 + len(ni.filename.encode())
                pad = (4 - offset % 4) % 4
                ni.extra = b"\0" * pad
            zout.writestr(ni, data)
