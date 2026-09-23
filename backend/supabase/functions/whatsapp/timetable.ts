/**
 * Reading a week out of a forwarded group message.
 *
 * Pure: no network, no clock of its own, no Deno. That is what lets it be
 * tested with node and read on its own, and it is the same shape as
 * `CuePolicy` on the phone — the decision separated from the plumbing.
 *
 * What comes in is a real message from a real class group, which is to say
 * badly punctuated, half in shorthand, and often three announcements in one:
 *
 *   "Guys, Marketing 101 shifted to Thursday 2-4 pm. Also OB tomorrow
 *    9:30-11 in LT2"
 *
 * What goes out is times, and only times. The subject, the room, the sender
 * and the group name are read past and dropped on the floor — nothing here
 * returns them and nothing downstream has anywhere to put them.
 */

export type Kind = "busy" | "free";

export interface Block {
  /** ISO-8601, Monday is 1. Matches week_blocks.day and DayOfWeek.getValue(). */
  day: number;
  /** "HH:MM". */
  start: string;
  end: string;
  kind: Kind;
}

/**
 * The study is at BITSoM, and "tomorrow" in a group chat means tomorrow
 * there. The alternative is asking every participant for a timezone during
 * onboarding to resolve one word, which is not a trade worth making until
 * somebody is forwarding messages from another country.
 */
const ZONE_OFFSET_MINUTES = 330;

/** More than this in one message and it is a syllabus, not an announcement. */
const MOST_BLOCKS = 12;

/** Longer than this and the parse is wrong, whatever the text said. */
const LONGEST_MINUTES = 8 * 60;

const DAYS: Array<[RegExp, number]> = [
  [/\bmon(day)?\b/, 1],
  [/\btue(s|sday)?\b/, 2],
  [/\bwed(nes)?(day)?\b/, 3],
  [/\bthu(r|rs|rsday)?\b/, 4],
  [/\bfri(day)?\b/, 5],
  [/\bsat(urday)?\b/, 6],
  [/\bsun(day)?\b/, 7],
];

/**
 * A class that is off is not a class. Without this, "no class on Monday
 * 9-11" books the very hour it is announcing you are free for — the worst
 * possible direction for this feature to be wrong in, because a busy block
 * is what stops a cue.
 *
 * Unless the sentence also says where it moved to, in which case the time in
 * it is the new time and belongs on the week.
 */
const CALLED_OFF = /\b(cancel+ed|cancell?ed|cancel|no class|holiday|off today)\b/;
const MOVED = /\b(reschedul|resched|moved|shifted|instead|now at|preponed|postponed to)\w*\b/;

const RANGE =
  /(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?\s*(?:-|–|—|to|till|until|until\s+)\s*(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?/;
const SINGLE = /(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)/;

/**
 * Every busy stretch this message announces, in the order it announced them.
 *
 * [now] decides what "today" and "tomorrow" mean and nothing else; pass the
 * instant the message arrived.
 */
export function readTimetable(text: string, now: Date): Block[] {
  const out: Block[] = [];
  const seen = new Set<string>();

  // One announcement per segment, because everything below reads a segment as
  // being about a single thing: the day it names and the time it gives. Two
  // announcements left in one segment is the failure that matters — "OB
  // tomorrow 9.30-11. Stats monday 9-11 cancelled" takes its day from the
  // wrong half and is then thrown away by the cancellation in the other.
  //
  // A full stop divides them, but only when it is not the one inside "9.30",
  // so a stop with a digit after it is left alone and every other one ends a
  // sentence.
  const segments = text
    .toLowerCase()
    // Horizontal whitespace only: the newline is a separator below, and
    // collapsing it here would run a pasted list into one line.
    .replace(/[ \t\r\f\v ]+/g, " ")
    .split(/[\n;,!?]+|\.(?!\d)|\band\b|\balso\b/);

  // Carried forward on purpose. A timetable pasted as a list names its day
  // once at the top and then gives six times under it.
  let day: number | null = null;

  for (const raw of segments) {
    const segment = raw.trim();
    if (!segment) continue;

    const named = dayIn(segment, now);
    if (named !== null) day = named;
    if (day === null) continue;

    if (CALLED_OFF.test(segment) && !MOVED.test(segment)) continue;

    const span = timesIn(segment);
    if (!span) continue;

    const block: Block = { day, start: span[0], end: span[1], kind: "busy" };
    const key = `${block.day}|${block.start}|${block.end}`;
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(block);
    if (out.length >= MOST_BLOCKS) break;
  }

  return out;
}

/** Which day this segment names, or null. */
function dayIn(segment: string, now: Date): number | null {
  for (const [pattern, value] of DAYS) {
    if (pattern.test(segment)) return value;
  }
  if (/\btomorrow\b|\btmrw?\b|\btmr\b/.test(segment)) return weekdayThere(now, 1);
  if (/\btoday\b|\btdy\b/.test(segment)) return weekdayThere(now, 0);
  return null;
}

/** The ISO weekday [plusDays] from now, where the participants are. */
function weekdayThere(now: Date, plusDays: number): number {
  const local = new Date(now.getTime() + (ZONE_OFFSET_MINUTES + plusDays * 1440) * 60_000);
  const sundayFirst = local.getUTCDay();
  return sundayFirst === 0 ? 7 : sundayFirst;
}

/** The stretch this segment covers as ["HH:MM", "HH:MM"], or null. */
function timesIn(segment: string): [string, string] | null {
  const range = RANGE.exec(segment);
  if (range) {
    const [, h1, m1, mer1, h2, m2, mer2] = range;
    // "2-4 pm" puts the meridiem on the end only, and means it about both.
    const start = clock(h1, m1, mer1 ?? mer2);
    let end = clock(h2, m2, mer2 ?? mer1);
    if (start === null || end === null) return null;
    // "11-1" crosses noon: the end is in the afternoon even though nobody
    // wrote pm. Only when neither side was spelled out — if somebody did
    // write it, they are the authority, and the range is simply wrong.
    if (end <= start && !mer1 && !mer2 && end + 720 > start) end += 720;
    return span(start, end);
  }

  const single = SINGLE.exec(segment);
  if (single) {
    const [, h, m, mer] = single;
    const start = clock(h, m, mer);
    if (start === null) return null;
    // One time is a start time. An hour is the shape of a class and the
    // wrong hour costs a dismissible cue.
    return span(start, start + 60);
  }

  return null;
}

/** Minutes from midnight, or null if the text was not a time at all. */
function clock(hour: string, minute: string | undefined, meridiem: string | undefined): number | null {
  let h = Number(hour);
  const m = minute ? Number(minute) : 0;
  if (!Number.isInteger(h) || h < 0 || h > 23 || m < 0 || m > 59) return null;

  if (meridiem === "pm" && h < 12) h += 12;
  else if (meridiem === "am" && h === 12) h = 0;
  else if (!meridiem && h >= 1 && h <= 7) h += 12; // "2-4" on a campus is the afternoon

  if (h > 23) return null;
  return h * 60 + m;
}

function span(start: number, end: number): [string, string] | null {
  if (end <= start) return null;
  if (end > 24 * 60) return null;
  if (end - start > LONGEST_MINUTES) return null;
  return [hhmm(start), hhmm(end)];
}

function hhmm(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`;
}

const DAY_NAMES = ["", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

/** What the bot says back, so somebody can see it read them right. */
export function describe(blocks: Block[]): string {
  if (blocks.length === 0) {
    return "I could not find a day and a time in that. Forward a message that " +
      "says something like \"Marketing 101 moved to Thursday 2-4 pm\".";
  }
  const lines = blocks.map((b) => `• ${DAY_NAMES[b.day]} ${b.start}–${b.end}`);
  return `Marked busy on your week:\n${lines.join("\n")}\n\n` +
    "It will show on Harbor's schedule next time you open the app. " +
    "Drag it off the grid there if I got it wrong.";
}
