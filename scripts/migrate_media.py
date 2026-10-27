#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
把记录正文里的 base64 图片/视频迁移成 media/ 目录下的独立文件，正文只保留 /media/... 地址。

用法（在项目根目录执行）：
    python scripts/migrate_media.py            # 预演（dry-run）：只统计，不写文件、不改库
    python scripts/migrate_media.py --apply    # 正式迁移：写文件 + 建索引 + 更新正文

依赖：pymysql、Pillow（pip install pymysql pillow）
迁移前请先停掉 8090 应用，避免迁移过程中有新记录写入。
"""
import argparse
import base64
import hashlib
import io
import os
import re
import sys
from datetime import datetime, timezone

import pymysql
from PIL import Image

DB = dict(host="localhost", port=3306, user="root", password="263656", database="studylog", charset="utf8mb4")
MEDIA_ROOT = os.path.join(os.getcwd(), "media")
THUMB_MAX = 1280
THUMB_QUALITY = 72

# data:image/png;base64,xxxx （兼容 src='...' 单引号）
DATA_URI = re.compile(r"""(src|href)\s*=\s*(["'])data:([\w/+.\-]+);base64,([^"']+)\2""", re.IGNORECASE)
# 匹配 <img ...> 标签，用于补 data-full / loading / decoding
IMG_TAG = re.compile(r"<img\b[^>]*>", re.IGNORECASE)
MEDIA_SHA = re.compile(r"/media/[0-9a-f]{2}/[0-9a-f]{2}/([0-9a-f]{40})(?:_thumb)?\.[A-Za-z0-9]+")

MIME_EXT = {
    "image/png": "png",
    "image/jpeg": "jpg",
    "image/jpg": "jpg",
    "image/gif": "gif",
    "image/webp": "webp",
    "video/mp4": "mp4",
    "video/webm": "webm",
    "video/quicktime": "mov",
}

CREATE_TABLE = """
CREATE TABLE IF NOT EXISTS media (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  sha1 VARCHAR(40) NOT NULL,
  path VARCHAR(128) NOT NULL,
  thumb VARCHAR(128) NULL,
  mime VARCHAR(64) NOT NULL,
  file_size BIGINT NOT NULL,
  width INT NULL,
  height INT NULL,
  uploader VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  UNIQUE KEY uk_media_sha1 (sha1),
  KEY idx_media_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
"""


def log(msg):
    print(msg, flush=True)


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f}{unit}"
        n /= 1024.0


def thumb_for(data):
    """返回 (宽, 高, JPEG bytes) 或 (None, None, None)。所有图片一视同仁，长边缩到 1280。"""
    try:
        img = Image.open(io.BytesIO(data))
        w, h = img.size
        img = img.convert("RGB")  # 透明 PNG 铺白底，与 Java 端缩略图一致
        scale = min(1.0, THUMB_MAX / float(max(w, h)))
        tw, th = max(1, int(round(w * scale))), max(1, int(round(h * scale)))
        if scale < 1.0:
            img = img.resize((tw, th), Image.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, "JPEG", quality=THUMB_QUALITY, optimize=True)
        return w, h, buf.getvalue()
    except Exception as e:
        log(f"  ! 缩略图生成失败（保留原图）：{e}")
        return None, None, None


def store_blob(data, mime, media_root):
    """按 sha1 去重落盘，返回 (sha1, path, thumb, width, height)。"""
    sha = hashlib.sha1(data).hexdigest()
    ext = MIME_EXT[mime]
    rel = f"{sha[:2]}/{sha[2:4]}/{sha}.{ext}"
    full = os.path.join(media_root, rel)
    thumb_rel = None
    width = height = None
    if not os.path.exists(full):
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, "wb") as f:
            f.write(data)
    if mime.startswith("image/"):
        w, h, tb = thumb_for(data)
        width, height = w, h
        if tb:
            thumb_rel = f"{sha[:2]}/{sha[2:4]}/{sha}_thumb.jpg"
            tpath = os.path.join(media_root, thumb_rel)
            if not os.path.exists(tpath):
                with open(tpath, "wb") as f:
                    f.write(tb)
    return sha, rel, thumb_rel, width, height


def rewrite_img_tags(html, sha2url):
    """给迁移后的 <img> 补 data-full/loading/decoding，点击预览时用原图、滚动时懒加载。"""

    def fix(tag):
        m = re.search(r"src\s*=\s*([\"'])(/media/[^\"']+)\1", tag, re.IGNORECASE)
        if not m or re.search(r"data-full\s*=", tag, re.IGNORECASE):
            return tag
        src = m.group(2)
        sm = MEDIA_SHA.search(src)
        if not sm:
            return tag
        full = sha2url.get(sm.group(1))
        if not full:
            return tag
        extra = f' data-full="{full}"'
        if not re.search(r"loading\s*=", tag, re.IGNORECASE):
            extra += ' loading="lazy"'
        if not re.search(r"decoding\s*=", tag, re.IGNORECASE):
            extra += ' decoding="async"'
        return tag[:-1] + extra + ">"

    return IMG_TAG.sub(lambda m: fix(m.group(0)), html)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true", help="正式写入（默认只预演）")
    ap.add_argument("--batch", type=int, default=50, help="每批处理记录数")
    args = ap.parse_args()

    conn = pymysql.connect(**DB, autocommit=False)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id, content FROM records ORDER BY id")
            rows = cur.fetchall()
    finally:
        conn.close()

    total_before = sum(len((r[1] or "").encode("utf-8")) for r in rows)
    log(f"扫描记录 {len(rows)} 条，正文合计 {human(total_before)}")

    stats = dict(records=0, occurrences=0, unique=0, bytes=0, images=0, videos=0,
                 unsupported=0, thumbs=0)
    seen_sha = {}
    unsupported_mimes = set()
    samples = []
    updated_before = 0
    updated_after = 0

    # 第一遍：解析、落盘（apply 时）、计算每个记录的新正文
    updates = []
    for rid, content in rows:
        content = content or ""
        if "data:" not in content:
            continue
        matches = list(DATA_URI.finditer(content))
        if not matches:
            continue
        stats["records"] += 1
        sha2url = {}
        new_content = content

        for m in matches:
            attr, q1, mime, b64 = m.group(1), m.group(2), m.group(3).lower(), m.group(4)
            if mime == "image/jpg":
                mime = "image/jpeg"
            if mime not in MIME_EXT:
                stats["unsupported"] += 1
                unsupported_mimes.add(mime)
                continue
            try:
                raw = base64.b64decode(re.sub(r"\s+", "", b64), validate=False)
            except Exception:
                stats["unsupported"] += 1
                unsupported_mimes.add(mime + " (解码失败)")
                continue
            if not raw:
                continue
            stats["occurrences"] += 1
            stats["bytes"] += len(raw)
            if mime.startswith("image/"):
                stats["images"] += 1
            else:
                stats["videos"] += 1

            if args.apply:
                sha, rel, thumb, w, h = store_blob(raw, mime, MEDIA_ROOT)
                if thumb:
                    stats["thumbs"] += 1
            else:
                sha = hashlib.sha1(raw).hexdigest()
                ext = MIME_EXT[mime]
                rel = f"{sha[:2]}/{sha[2:4]}/{sha}.{ext}"
                thumb = f"{sha[:2]}/{sha[2:4]}/{sha}_thumb.jpg" if mime.startswith("image/") else None
                w = h = None

            url = "/media/" + rel
            thumb_url = ("/media/" + thumb) if thumb else url
            sha2url[sha] = url
            seen_sha[sha] = dict(path=rel, thumb=thumb, mime=mime, size=len(raw), width=w, height=h)

            old = f'{attr}={q1}data:{m.group(3)};base64,{b64}{q1}'
            new = f'{attr}={q1}{thumb_url}{q1}'
            new_content = new_content.replace(old, new, 1)
            if len(samples) < 5:
                samples.append((rid, mime, len(raw), thumb_url, url))

        if new_content != content:
            new_content = rewrite_img_tags(new_content, sha2url)
            updated_before += len(content.encode("utf-8"))
            updated_after += len(new_content.encode("utf-8"))
            updates.append((rid, new_content))

    stats["unique"] = len(seen_sha)
    after = total_before - updated_before + updated_after
    log("")
    log("==== 统计 ====")
    log(f"含内嵌媒体的记录：{stats['records']} / {len(rows)}")
    log(f"媒体引用：{stats['occurrences']} 处（图片 {stats['images']}，视频 {stats['videos']}）")
    log(f"去重后文件：{stats['unique']} 个，原始字节 {human(stats['bytes'])}")
    log(f"缩略图：{stats['thumbs']} 个")
    log(f"无法处理：{stats['unsupported']} 处 {sorted(unsupported_mimes) if unsupported_mimes else ''}")
    log(f"正文体积：{human(total_before)} → 约 {human(max(0, after))}")
    for s in samples:
        log(f"  示例 记录#{s[0]} {s[1]} {human(s[2])} → {s[3]}（原图 {s[4]}）")
    log("")

    if not args.apply:
        log("预演完成，未写入任何文件或数据库。确认无误后加 --apply 正式迁移。")
        return 0

    if not updates:
        log("没有需要更新的记录。")
        return 0

    log(f"写入 {len(updates)} 条记录、{len(seen_sha)} 个媒体索引…")
    conn = pymysql.connect(**DB, autocommit=False)
    try:
        with conn.cursor() as cur:
            cur.execute(CREATE_TABLE)
            now = datetime.now(timezone.utc).replace(tzinfo=None)
            for sha, info in seen_sha.items():
                cur.execute(
                    "INSERT INTO media (sha1, path, thumb, mime, file_size, width, height, uploader, created_at) "
                    "VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s) "
                    "ON DUPLICATE KEY UPDATE path=VALUES(path), thumb=VALUES(thumb), mime=VALUES(mime), "
                    "file_size=VALUES(file_size), width=VALUES(width), height=VALUES(height)",
                    (sha, info["path"], info["thumb"], info["mime"], info["size"],
                     info["width"], info["height"], "migration", now),
                )
            for i, (rid, content) in enumerate(updates, 1):
                cur.execute("UPDATE records SET content=%s WHERE id=%s", (content, rid))
                if i % 20 == 0:
                    log(f"  已更新 {i}/{len(updates)}")
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    # 校验：正文里不应再残留 base64 媒体
    conn = pymysql.connect(**DB, autocommit=True)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM records WHERE content LIKE '%base64,%'")
            left = cur.fetchone()[0]
            cur.execute("SELECT COUNT(*) FROM media")
            media_rows = cur.fetchone()[0]
    finally:
        conn.close()
    log("")
    log(f"迁移完成：media 索引 {media_rows} 条，正文残留 base64 记录 {left} 条")
    if left:
        log("!! 仍有记录含 base64，请检查上面「无法处理」的类型或手工处理。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
