-- The quiet gap between two reminders may now be nothing at all.
--
-- 0001 wrote `check (cooldown_minutes between 1 and 1440)` with a default of
-- two hours. Two hours is a long time to be unable to watch the feature work,
-- and the setting had no control on any screen, so the suggestion was in
-- practice a rule -- which "thresholds are user-set, never a locked default"
-- says it must not be. The app can now set it, and the value it can now set it
-- to includes zero, which this CHECK would have refused.
--
-- Zero means no enforced gap. The daily cap is the limit that remains.

alter table user_settings
  drop constraint if exists user_settings_cooldown_minutes_check;

alter table user_settings
  add constraint user_settings_cooldown_minutes_check
  check (cooldown_minutes between 0 and 1440);

alter table user_settings
  alter column cooldown_minutes set default 0;

-- Note for whoever reads the study export: `cooldown_delta` in 0002 is
-- measured against this column default, so rows written before this migration
-- were compared against 120 and rows after are compared against 0. The delta
-- is only meaningful alongside the absolute value, which the view also
-- carries.
