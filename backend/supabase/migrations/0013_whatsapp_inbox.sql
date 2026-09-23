-- Harbor — a timetable can arrive by WhatsApp
--
-- Participants live in class group chats. The week they would otherwise draw
-- by hand on the schedule screen is already being announced in those chats:
-- "Marketing 101 moved to Thursday 2-4". This lets them forward that message
-- to a Harbor number instead of retyping it. See ADR-014.
--
-- Three rules this file is built to enforce:
--
--   1. The bot never guesses who it is talking to. A phone number is bound to
--      an account only by a code the account holder read off their own screen
--      and typed into WhatsApp. Until then the bot knows a number and nothing
--      else, and can write nowhere.
--   2. The bot proposes; the phone decides. Parsed times land in
--      `schedule_inbox`, never in `week_blocks`. The device is still
--      authoritative for its own week (ADR-003): it drains the inbox, places
--      the blocks with the same `Windows.place` a finger would, and deletes
--      the rows. A server writing straight into `week_blocks` would be
--      overwritten by the next `putWeek` anyway.
--   3. The message itself is not kept. Only day, start, end. There is no
--      column here for the text, the sender, the group, or the subject — the
--      same reason `week_blocks` has no label column, and just as deliberate.

-- ------------------------------------------------------- whatsapp_pairings

create table whatsapp_pairings (
  user_id    uuid primary key references auth.users (id) on delete cascade,
  -- Read off the schedule screen and typed into a chat, so: no vowels to spell
  -- a word with, and none of the characters people mistake for each other.
  code       text not null unique check (code ~ '^[A-Z0-9]{6}$'),
  -- Null until a message carrying the code arrives. Unique so one number
  -- cannot feed two accounts.
  phone_e164 text unique check (phone_e164 ~ '^\+[1-9]\d{7,14}$'),
  paired_at  timestamptz,
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------- schedule_inbox
--
-- The id is generated here rather than on the phone, which is the one place
-- in this schema that happens. The rule against it (see 0001, note 2) exists
-- because a device cannot reference an id it has never read back — and here
-- it always has: the phone reads the row, places it, then deletes it by that
-- id. Nothing foreign-keys to these rows and none of them outlive a sync.

create table schedule_inbox (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid not null references auth.users (id) on delete cascade,
  -- ISO-8601, Monday is 1. Same convention as week_blocks.
  day        smallint not null check (day between 1 and 7),
  starts_at  time not null,
  ends_at    time not null,
  kind       block_kind not null default 'busy',
  arrived_at timestamptz not null default now(),

  constraint it_ends_after_it_starts check (ends_at > starts_at),
  -- Forwarding the same message twice is the normal case, not the odd one:
  -- the reminder goes round the group again and somebody forwards it again.
  constraint one_row_per_slot unique (user_id, day, starts_at, ends_at, kind)
);

create index schedule_inbox_user_idx on schedule_inbox (user_id);

-- --------------------------------------------------------------------- RLS

alter table whatsapp_pairings enable row level security;
alter table schedule_inbox    enable row level security;

-- Your own pairing, and nobody else's. Reading somebody else's code would be
-- enough to point their bot at your phone.
create policy "own pairing" on whatsapp_pairings
  for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

-- Your own inbox, read and empty. There is deliberately no insert policy:
-- only the edge function writes here, with the service role, and a client
-- that could insert could put a lecture in somebody else's week.
create policy "read own inbox" on schedule_inbox
  for select using (auth.uid() = user_id);

create policy "empty own inbox" on schedule_inbox
  for delete using (auth.uid() = user_id);

-- ----------------------------------------------------------- the code call
--
-- A function rather than an insert from the app, so the code is minted once,
-- server-side, and two devices signed into one account cannot race each other
-- into two codes. Idempotent: ask twice, get the same six characters.

create or replace function whatsapp_code() returns text
language plpgsql security definer set search_path = public as $$
declare
  existing text;
  minted   text;
begin
  if auth.uid() is null then
    raise exception 'not signed in';
  end if;

  select code into existing from whatsapp_pairings where user_id = auth.uid();
  if existing is not null then
    return existing;
  end if;

  -- No I, O, 0 or 1: a code is read off a screen and typed into a chat.
  -- `select ... into` rather than `:=`, which cannot take a from clause.
  loop
    select string_agg(
             substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', floor(random() * 32)::int + 1, 1),
             ''
           )
      into minted
      from generate_series(1, 6);
    exit when not exists (select 1 from whatsapp_pairings where code = minted);
  end loop;

  insert into whatsapp_pairings (user_id, code) values (auth.uid(), minted);
  return minted;
end;
$$;

revoke all on function whatsapp_code() from public;
grant execute on function whatsapp_code() to authenticated;
