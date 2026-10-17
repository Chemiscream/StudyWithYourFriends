# -*- coding: utf-8 -*-
"""一次性迁移：SQLite(study.db) -> MySQL(studylog)。
保留原始 ID 和密码哈希（Java 版兼容同样的 PBKDF2 格式），老用户无需改密码。
可重复执行：目标表非空时拒绝迁移。
"""
import sqlite3
import sys
from datetime import datetime, timezone

import pymysql

SQLITE = r"C:\Files\StudyLog\study.db"

def to_naive_utc(iso):
    if not iso:
        return None
    dt = datetime.fromisoformat(iso)
    if dt.tzinfo is not None:
        dt = dt.astimezone(timezone.utc).replace(tzinfo=None)
    return dt

def main():
    my = pymysql.connect(host="127.0.0.1", port=3306, user="root",
                         password="263656", database="studylog", charset="utf8mb4")
    sl = sqlite3.connect(SQLITE)
    sl.row_factory = sqlite3.Row

    with my.cursor() as c:
        c.execute("SELECT COUNT(*) FROM users")
        if c.fetchone()[0] > 0:
            print("目标库 users 表非空，拒绝迁移（避免重复导入）")
            sys.exit(1)

        users = sl.execute("SELECT id,username,password_hash,color,created_at FROM users ORDER BY id").fetchall()
        for u in users:
            c.execute(
                "INSERT INTO users(id,username,password_hash,color,created_at) VALUES(%s,%s,%s,%s,%s)",
                (u["id"], u["username"], u["password_hash"],
                 u["color"] or "4a90d9", to_naive_utc(u["created_at"])))

        recs = sl.execute(
            "SELECT id,user_id,username,content,image,created_at,updated_at FROM records ORDER BY id").fetchall()
        for r in recs:
            c.execute(
                "INSERT INTO records(id,user_id,username,content,image,created_at,updated_at) "
                "VALUES(%s,%s,%s,%s,%s,%s,%s)",
                (r["id"], r["user_id"], r["username"], r["content"], r["image"],
                 to_naive_utc(r["created_at"]), to_naive_utc(r["updated_at"])))

        cs = sl.execute(
            "SELECT id,record_id,username,content,created_at FROM comments ORDER BY id").fetchall()
        for x in cs:
            c.execute(
                "INSERT INTO comments(id,record_id,username,content,created_at) VALUES(%s,%s,%s,%s,%s)",
                (x["id"], x["record_id"], x["username"], x["content"],
                 to_naive_utc(x["created_at"])))

    my.commit()
    print(f"迁移完成: users={len(users)} records={len(recs)} comments={len(cs)}")
    sl.close(); my.close()

if __name__ == "__main__":
    main()
