#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
回收没有被任何记录正文引用的媒体文件（例如上传后未发布、编辑时删掉的图片）。

用法（项目根目录）：
    python scripts/media_gc.py           # 只列出，不删除
    python scripts/media_gc.py --apply   # 删除孤儿文件与 media 索引
"""
import argparse
import os
import re
import sys

import pymysql

DB = dict(host="localhost", port=3306, user="root", password="263656", database="studylog", charset="utf8mb4")
MEDIA_ROOT = os.path.join(os.getcwd(), "media")
SHA_FILE = re.compile(r"([0-9a-f]{40})(?:_thumb)?\.[A-Za-z0-9]+$")


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f}{unit}"
        n /= 1024.0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    args = ap.parse_args()

    conn = pymysql.connect(**DB, autocommit=True)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, sha1, path, thumb, file_size FROM media")
            rows = cur.fetchall()
            orphans = []
            for mid, sha, path, thumb, size in rows:
                cur.execute("SELECT COUNT(*) FROM records WHERE content LIKE %s", (f"%{sha}%",))
                if cur.fetchone()[0] == 0:
                    orphans.append((mid, sha, path, thumb, size))

            # 磁盘上没有索引的文件（中断的上传、手工拷贝等）
            indexed = {r[2] for r in rows} | {r[3] for r in rows if r[3]}
            stray = []
            if os.path.isdir(MEDIA_ROOT):
                for root, _, files in os.walk(MEDIA_ROOT):
                    for name in files:
                        if not SHA_FILE.search(name):
                            continue
                        rel = os.path.relpath(os.path.join(root, name), MEDIA_ROOT).replace("\\", "/")
                        if rel not in indexed:
                            stray.append(rel)
    finally:
        conn.close()

    total = sum(r[4] or 0 for r in orphans)
    print(f"未被引用的媒体索引：{len(orphans)} 个，{human(total)}")
    for r in orphans[:20]:
        print(f"  #{r[0]} {r[1]} {r[2]}")
    print(f"磁盘上无索引的文件：{len(stray)} 个")
    for s in stray[:20]:
        print("  " + s)

    if not args.apply:
        print("预演完成，未删除。确认无误后加 --apply。")
        return 0

    conn = pymysql.connect(**DB, autocommit=True)
    try:
        with conn.cursor() as cur:
            for mid, sha, path, thumb, _ in orphans:
                for rel in (path, thumb):
                    if rel:
                        p = os.path.join(MEDIA_ROOT, rel)
                        try:
                            if os.path.exists(p):
                                os.remove(p)
                        except OSError as e:
                            print(f"  ! 删除失败 {p}: {e}")
                cur.execute("DELETE FROM media WHERE id=%s", (mid,))
    finally:
        conn.close()
    for rel in stray:
        try:
            os.remove(os.path.join(MEDIA_ROOT, rel))
        except OSError as e:
            print(f"  ! 删除失败 {rel}: {e}")
    print(f"已删除 {len(orphans)} 条索引、{len(stray)} 个无索引文件。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
