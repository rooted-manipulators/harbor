-- The suggested walk is three minutes, not ten.
--
-- 0001 wrote `walking_minutes integer not null default 10`, matching the
-- prototype's calibration and `Thresholds.SUGGESTED`. Those two are supposed
-- to agree -- a participant who never opens the setting gets the column
-- default, and the study measures drift from the suggestion, so a suggestion
-- that differs between the phone and the database is two different studies.
--
-- The app moved to three on 2026-09-18, for a reason that is about watching
-- rather than about walking: ten minutes of continuous walking followed by a
-- stop is a threshold almost nobody crosses while somebody is sitting with
-- them using the app. Every test session therefore only ever saw the preview
-- reminder, and never the real one -- which is the single piece of behaviour
-- most worth watching a stranger meet.
--
-- Three is still a walk rather than a trip to the kettle, and it is reachable
-- inside a session. It is a suggestion either way: the sensing screen has a
-- stepper on it, and `docs/01-decisions.md` is explicit that thresholds are
-- user-set and never a locked default.
--
-- **If the study wants ten back, this is the file to reverse**, together with
-- `Thresholds.SUGGESTED.walkingMinutes` and the two tests that pin it.

alter table user_settings
  alter column walking_minutes set default 3;
