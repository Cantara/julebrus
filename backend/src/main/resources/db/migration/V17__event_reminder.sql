-- Push reminder one hour before a planned tasting: sent once per event, to every attendee's browsers.
alter table events add column reminder_sent_at timestamp with time zone;
