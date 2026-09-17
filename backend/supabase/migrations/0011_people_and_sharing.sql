-- 0011 — people, and letting one of them see your week
--
-- The change this migration serves is the largest one Harbor has made: the
-- other person now has the app too. Until today a contact was a label and a
-- phone number that belonged entirely to you, every policy in 0001 read
-- `auth.uid() = user_id`, and that file says in its own header that there is
-- "deliberately no parent-facing role, table, or policy". All three of those
-- statements stop being true here. See ADR-013 in docs/01-decisions.md for why
-- and for what it costs.
--
-- Two things are added:
--
--   week_blocks   the schedule, which has never been in this schema at all.
--                 It has lived only on the phone that typed it.
--   links         one account asking another if it may see that schedule,
--                 and the answer.
--
-- Three rules this file is built to enforce, rather than merely to permit:
--
--   1. Sharing is asked for and granted, never assumed. A link starts
--      'pending' and shows nothing until the other person makes it
--      'accepted'. There is no state in which adding somebody's number gets
--      you their week.
--   2. What is shared is the *shape* of a week, never what is in it.
--      `week_blocks` has no label column and will not be given one. On the
--      device that field is documented "Never leaves the device", and the
--      cheapest way to keep a promise like that is to have nowhere to put it.
--      "Busy 2-4pm Tuesday" crosses the wire; "Therapy" does not.
--   3. It is read-only and it is revocable. Nobody writes to anybody else's
--      week, and setting a link back to 'revoked' ends the view immediately,
--      because the policy is evaluated per query and not cached anywhere.

-- --------------------------------------------------------------- new types

create type block_kind as enum ('busy', 'free');

-- 'declined' and 'revoked' are kept apart on purpose. They are the same
-- permission -- none -- and they are not the same event: one is an answer to a
-- question and the other is taking something back. A study that cannot tell
-- them apart cannot say whether people change their minds.
create type link_state as enum ('pending', 'accepted', 'declined', 'revoked');

-- ------------------------------------------------------------- week_blocks

create table week_blocks (
  id         uuid primary key,
  user_id    uuid not null references auth.users (id) on delete cascade,
  -- ISO-8601: Monday is 1. Matches java.time.DayOfWeek.getValue() exactly, so
  -- the client never has to remember an off-by-one at the boundary.
  day        smallint not null check (day between 1 and 7),
  starts_at  time not null,
  ends_at    time not null,
  kind       block_kind not null,
  updated_at timestamptz not null default now(),

  -- No label column. Rule 2, above. This is load-bearing, not an oversight.

  constraint it_ends_after_it_starts check (ends_at > starts_at)
);

create index week_blocks_user_id_idx on week_blocks (user_id);
create index week_blocks_user_day_idx on week_blocks (user_id, day);

-- ------------------------------------------------------------------- links
--
-- Directed, not mutual. That my aunt lets me see her Tuesdays does not put my
-- Tuesdays in front of her, and a single row that meant "we are connected"
-- would have made it so. Two people who both want to see each other's weeks
-- have two rows and answered two questions.

create table links (
  id         uuid primary key,
  requester  uuid not null references auth.users (id) on delete cascade,
  addressee  uuid not null references auth.users (id) on delete cascade,
  state      link_state not null default 'pending',
  created_at timestamptz not null default now(),
  decided_at timestamptz,

  constraint one_ask_per_pair unique (requester, addressee),
  constraint not_yourself check (requester <> addressee)
);

create index links_addressee_idx on links (addressee, state);
create index links_requester_idx on links (requester, state);

-- --------------------------------------------------------------------- RLS

alter table week_blocks enable row level security;
alter table links       enable row level security;

-- Your own week: yours entirely.
create policy "own week"
  on week_blocks for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- Somebody else's week: readable only while they are still saying yes.
--
-- `for select` and nothing else. There is no update or delete policy pointing
-- at another user's rows, so the strongest thing an accepted link can ever do
-- is look.
create policy "the week of someone who said yes"
  on week_blocks for select
  using (
    exists (
      select 1 from links l
      where l.state = 'accepted'
        and l.addressee = week_blocks.user_id
        and l.requester = auth.uid()
    )
  );

-- Both ends of a link can see it: you can see what you asked for, and you can
-- see what you have been asked.
create policy "links i am part of"
  on links for select
  using (auth.uid() = requester or auth.uid() = addressee);

-- Only the requester opens one, and only ever as 'pending'. Inserting a row
-- that already said 'accepted' would be granting yourself access.
create policy "ask someone"
  on links for insert
  with check (auth.uid() = requester and state = 'pending');

-- Only the addressee answers. The requester's own way out is below.
create policy "answer what i was asked"
  on links for update
  using (auth.uid() = addressee)
  with check (auth.uid() = addressee);

-- Either end can end it. Deleting the row rather than revoking it loses the
-- fact that it happened, which the study wants, so the client revokes and this
-- exists for account cleanup.
create policy "walk away"
  on links for delete
  using (auth.uid() = requester or auth.uid() = addressee);

-- --------------------------------------------------------------- finding it
--
-- You cannot select another profile, so the client cannot look an address up.
-- This is the only way across, and it deliberately does almost nothing: it
-- takes an address you must already have, and if it belongs to an account it
-- opens a pending request. It returns the link, never the account -- no name,
-- no id, nothing that would turn a contact list into a directory of who is
-- here.
--
-- It does leak whether an address is registered, to somebody who already has
-- that address. Every invite-by-identifier system leaks exactly that, and the
-- honest thing is to write it down rather than imply otherwise.
--
-- Matched case-insensitively. Nobody types their own capitals the same way
-- twice, and an address that fails to match because somebody wrote Gmail with
-- a capital G is a bug that looks like "she has not signed up".

create function request_link(target_email text)
  returns uuid
  language plpgsql
  security definer
  set search_path = public
as $$
declare
  target uuid;
  existing links%rowtype;
  fresh uuid;
begin
  select u.id into target
  from auth.users u
  where lower(u.email) = lower(trim(target_email))
  limit 1;

  if target is null or target = auth.uid() then
    return null;
  end if;

  select * into existing from links
  where requester = auth.uid() and addressee = target;

  if found then
    -- Asking again after a no is allowed, and is a new question rather than a
    -- second one: the old answer is cleared so the other person is not shown a
    -- decision they already made.
    if existing.state in ('declined', 'revoked') then
      update links set state = 'pending', decided_at = null
      where id = existing.id;
    end if;
    return existing.id;
  end if;

  fresh := gen_random_uuid();
  insert into links (id, requester, addressee) values (fresh, auth.uid(), target);
  return fresh;
end;
$$;

revoke all on function request_link(text) from public;
grant execute on function request_link(text) to authenticated;

-- ----------------------------------------------------------- answering one
--
-- A function rather than an update through the table policy, for two reasons.
--
-- The small one is that Android's HttpURLConnection will not reliably send a
-- PATCH, and the client is built on it to avoid taking an HTTP dependency for
-- six endpoints. Every call it makes is a GET, a POST or a DELETE.
--
-- The larger one is that this is the only place the rule lives. "Only the
-- person who was asked may answer, and only pending asks can be answered" is
-- one statement here, rather than a policy plus whatever the client remembers
-- to send.

-- `decision` and not `next`: NEXT is a SQL keyword (FETCH NEXT), and while
-- Postgres would probably allow it as a parameter name, a parameter that has to
-- be *probably* fine is not worth the two letters it saves in a file nobody can
-- run locally.
create function answer_link(link_id uuid, decision link_state)
  returns boolean
  language plpgsql
  security definer
  set search_path = public
as $$
declare
  touched integer;
begin
  -- Nothing may be pushed back to 'pending' through here. Re-asking is
  -- request_link's job, and it is a different question with a different
  -- audience: this one is answered by the person who was asked.
  if decision = 'pending' then
    return false;
  end if;

  update links
  set state = decision,
      decided_at = now()
  where id = link_id
    and (
      -- The person asked answers it, once, while it is still a question.
      (addressee = auth.uid() and state = 'pending'
        and decision in ('accepted', 'declined'))
      -- Either end takes it back, whenever.
      or (decision = 'revoked' and (addressee = auth.uid() or requester = auth.uid()))
    );

  get diagnostics touched = row_count;
  return touched > 0;
end;
$$;

revoke all on function answer_link(uuid, link_state) from public;
grant execute on function answer_link(uuid, link_state) to authenticated;
