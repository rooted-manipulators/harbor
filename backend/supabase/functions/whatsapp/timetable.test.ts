/**
 * Run with node (no Deno needed, and none of this touches Deno):
 *
 *   node --test backend/supabase/functions/whatsapp/timetable.test.ts
 *
 * The messages below are the shape class group chats actually send.
 */
import { strict as assert } from "node:assert";
import { test } from "node:test";
import { readTimetable, describe as say } from "./timetable.ts";

// A Wednesday, so "tomorrow" is Thursday and "today" is Wednesday.
const WED = new Date("2026-09-16T06:00:00Z");

test("a class moved to a named day and a pm range", () => {
  assert.deepEqual(
    readTimetable("Guys Marketing 101 shifted to Thursday 2-4 pm", WED),
    [{ day: 4, start: "14:00", end: "16:00", kind: "busy" }],
  );
});

test("half hours, written with a dot", () => {
  assert.deepEqual(
    readTimetable("OB lecture friday 9.30-11 am LT2", WED),
    [{ day: 5, start: "09:30", end: "11:00", kind: "busy" }],
  );
});

test("two announcements in one message", () => {
  assert.deepEqual(
    readTimetable("Stats Monday 9-11 and Ops Tuesday 2:30 to 4", WED),
    [
      { day: 1, start: "09:00", end: "11:00", kind: "busy" },
      { day: 2, start: "14:30", end: "16:00", kind: "busy" },
    ],
  );
});

test("a day named once, with times listed under it", () => {
  assert.deepEqual(
    readTimetable("Monday\n9-10 Marketing\n11-12 Stats\n2-4 Ops", WED),
    [
      { day: 1, start: "09:00", end: "10:00", kind: "busy" },
      { day: 1, start: "11:00", end: "12:00", kind: "busy" },
      { day: 1, start: "14:00", end: "16:00", kind: "busy" },
    ],
  );
});

test("tomorrow is resolved where the participants are", () => {
  assert.deepEqual(
    readTimetable("Reminder: guest session tomorrow 10-11:30 am", WED),
    [{ day: 4, start: "10:00", end: "11:30", kind: "busy" }],
  );
  assert.equal(readTimetable("today 3-5", WED)[0].day, 3);
});

test("a cancelled class books nothing", () => {
  assert.deepEqual(readTimetable("No class on Monday 9-11", WED), []);
  assert.deepEqual(readTimetable("Tuesday 2-4 lecture is cancelled", WED), []);
});

test("but a class that moved does book, cancellation word and all", () => {
  assert.deepEqual(
    readTimetable("Monday 9-11 cancelled, rescheduled to Saturday 10-12", WED),
    [{ day: 6, start: "10:00", end: "12:00", kind: "busy" }],
  );
});

test("a range crossing noon with no meridiem at all", () => {
  assert.deepEqual(
    readTimetable("wed 11-1", WED),
    [{ day: 3, start: "11:00", end: "13:00", kind: "busy" }],
  );
});

test("three announcements in one forwarded message, one of them cancelled", () => {
  // The whole point of segmenting on the full stop. Without it the second
  // sentence took its day from the third, and the third's "cancelled" then
  // threw both of them away — one right-looking block out of a message that
  // named two real classes.
  assert.deepEqual(
    readTimetable(
      "Guys — Marketing 101 is shifted to Thursday 2-4 pm. Also OB tomorrow " +
        "9.30-11 am in LT2. Stats monday 9-11 cancelled",
      WED,
    ),
    [
      { day: 4, start: "14:00", end: "16:00", kind: "busy" },
      { day: 4, start: "09:30", end: "11:00", kind: "busy" },
    ],
  );
});

test("one time is one hour", () => {
  assert.deepEqual(
    readTimetable("Quiz on friday at 5 pm", WED),
    [{ day: 5, start: "17:00", end: "18:00", kind: "busy" }],
  );
});

test("the same slot twice in one message lands once", () => {
  assert.equal(readTimetable("Monday 9-11. Repeat: monday 9-11", WED).length, 1);
});

test("a time with no day anywhere is not a block", () => {
  assert.deepEqual(readTimetable("lets meet at 5 pm", WED), []);
});

test("chat with no times at all is not a block", () => {
  assert.deepEqual(readTimetable("did anyone get the slides for monday", WED), []);
});

test("nothing absurd gets through", () => {
  assert.deepEqual(readTimetable("monday 9-99", WED), []);
  assert.deepEqual(readTimetable("monday 25:00-26:00", WED), []);
  assert.deepEqual(readTimetable("monday 8 am to 11 pm", WED), []); // 15 hours
});

test("a syllabus dump is capped", () => {
  const dump = Array.from({ length: 30 }, (_, i) => `monday ${i % 12}-${(i % 12) + 1}`).join("\n");
  assert.ok(readTimetable(dump, WED).length <= 12);
});

test("the reply names what it did, and nothing about the message", () => {
  const said = say(readTimetable("Marketing 101 thursday 2-4 pm in LT2", WED));
  assert.match(said, /Thursday 14:00–16:00/);
  assert.doesNotMatch(said, /Marketing|LT2/);
});

test("the reply says so when it found nothing", () => {
  assert.match(say([]), /could not find/);
});
