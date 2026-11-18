import { DatabaseSync } from 'node:sqlite';

import type { D1Like, D1Prepared } from './api';

class Prepared implements D1Prepared {
  private params: unknown[] = [];

  constructor(
    private readonly db: DatabaseSync,
    private readonly sql: string,
  ) {}

  bind(...values: unknown[]) {
    this.params = values.map((value) => (value === undefined ? null : value));
    return this;
  }

  async first<T>() {
    const row = this.db.prepare(this.sql).get(...(this.params as never[]));
    return (row as T) ?? null;
  }

  async all<T>() {
    const rows = this.db.prepare(this.sql).all(...(this.params as never[]));
    return { results: rows as T[] };
  }

  async run() {
    const info = this.db.prepare(this.sql).run(...(this.params as never[]));
    return { success: true, meta: { changes: Number(info.changes ?? 0) } };
  }
}

export type TestDb = D1Like & { exec(sql: string): void; close(): void };

export function openDatabase(path = ':memory:'): TestDb {
  const db = new DatabaseSync(path);
  db.exec('PRAGMA foreign_keys = ON');
  return {
    prepare(sql: string) {
      return new Prepared(db, sql);
    },
    exec(sql: string) {
      db.exec(sql);
    },
    close() {
      db.close();
    },
  };
}
