-- Two sensed triggers, so the day is split rather than raced for.
--
-- The scrolling trigger shipped on 2026-09-19: a reminder after a long
-- stretch in one app, alongside the one after a walk. Everything below
-- follows from there being two of them where there was one.
--
-- ## daily_cap 2 -> 4, and a source cap of 2
--
-- A walking stop happens once or twice a day. A long app stretch happens
-- several times. Against a single ceiling of two the frequent one takes both
-- slots most mornings and the rare one is never seen -- a week of running
-- both would end with a great many of one, four of the other, and no
-- comparison at all, which is the thing the study is for.
--
-- Four is not a loosening. Each trigger's share is the two the cap used to
-- be, and a participant who leaves scrolling switched off is unchanged in
-- practice: one source cannot out-compete itself. `Thresholds.SUGGESTED`
-- moved in the same commit, and these two are supposed to agree -- a
-- participant who never opens the setting gets the column default, and the
-- study measures drift from the suggestion, so a suggestion that differs
-- between the phone and the database is two different studies. 0014 makes
-- the same argument about walking_minutes.
--
-- ## The two new columns
--
-- source_cap is nullable and means "the same as daily_cap" when it is null,
-- exactly as `Thresholds.sourceCap` does. Null rather than a default that
-- reads daily_cap, so that an install which never sets it cannot end up
-- carrying a stale number larger than the day it belongs to.
--
-- scroll_cues records whether the trigger was switched on at all. Without it
-- a week with no scrolling reminders cannot be read: it looks identical
-- whether the person declined the option, or took it and never scrolled, and
-- those are opposite findings. Default false, because it is off until
-- somebody takes it on the onboarding screen -- and taking it is the consent,
-- so a default of true would be a claim nobody made.

-- ## A note on 0012 and 0014, which this one had to fix to be applicable
--
-- Both of them say `alter table settings`. There is no table called
-- `settings` -- 0001 created `user_settings` -- so both would fail on the
-- first statement, and `supabase db push` runs them in order before it
-- reaches this file. Neither had ever been applied: the remote's migration
-- list on 2026-09-19 ran 0001-0010 and then three timestamped ones, with
-- 0012 and 0014 simply absent, which is what a push that stopped looks like.
-- 0012's constraint name was wrong for the same reason -- 0001 named it
-- `user_settings_cooldown_minutes_check`. Both are corrected in place rather
-- than superseded here, because they had never run anywhere and a migration
-- that has never been applied is still a draft.
--
-- What that means for the database as it stands: cooldown_minutes still
-- refuses zero and still defaults to 120, and walking_minutes still defaults
-- to 10. Three migrations' worth of the app's suggestions have been sitting
-- unapplied, so the schema currently disagrees with `Thresholds.SUGGESTED`
-- on every number but one. Pushing this file fixes all of it at once; check
-- what a push does before running one.
--
-- `study_threshold_drift` in 0002 is left alone. It selects columns by name
-- and does not carry the two added below; a replacement would have to restate
-- the whole view, and the columns are on the table for anybody who needs
-- them.

alter table user_settings
  alter column daily_cap set default 4;

alter table user_settings
  add column if not exists source_cap integer
    check (source_cap is null or source_cap between 1 and daily_cap);

alter table user_settings
  add column if not exists scroll_cues boolean not null default false;

comment on column user_settings.source_cap is
  'Most reminders one trigger may produce in a day. Null means the same as daily_cap.';

comment on column user_settings.scroll_cues is
  'Whether the long-app-stretch trigger was switched on. Off until opted into.';
