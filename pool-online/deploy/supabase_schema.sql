-- 8-BALL CLUB · Supabase (PostgreSQL) 建表脚本（可选）
-- 在 Supabase Dashboard → SQL Editor 里整段执行即可。
--
-- 注意：
--   1) publishable / anon key 不能建表，也不能绕过 RLS；建表必须在 SQL Editor 执行。
--   2) 服务端写入建议用 service_role key（仅服务器保存，不要放前端）或直连 Postgres。
--   3) 当前 Python 服务默认用 SQLite；切到 Supabase 需要改 server.py 的 DB 层（可选）。

create extension if not exists pgcrypto;

create table if not exists public.users (
    id          uuid primary key default gen_random_uuid(),
    username    text unique not null,
    pass_hash   text not null,               -- PBKDF2: salt$hash
    avatar      int  not null default 0,
    created_at  timestamptz not null default now()
);

create table if not exists public.sessions (
    token       text primary key,
    user_id     uuid not null references public.users(id) on delete cascade,
    created_at  timestamptz not null default now(),
    expires_at  timestamptz
);

create table if not exists public.user_stats (
    user_id     uuid primary key references public.users(id) on delete cascade,
    games       int not null default 0,
    wins        int not null default 0,
    streak      int not null default 0,
    best_streak int not null default 0,
    potted      int not null default 0,
    break_pots  int not null default 0,
    updated_at  timestamptz not null default now()
);

create index if not exists idx_sessions_user on public.sessions(user_id);
create index if not exists idx_users_username on public.users(username);

-- 默认开启 RLS：前端直接访问也拿不到数据；服务端用 service_role 或直连数据库。
alter table public.users       enable row level security;
alter table public.sessions    enable row level security;
alter table public.user_stats  enable row level security;
