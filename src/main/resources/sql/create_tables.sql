-- noinspection SqlNoDataSourceInspectionForFile
-- noinspection SqlDialectInspectionForFile
# noinspection SqlNoDataSourceInspectionForFile
CREATE TABLE worklog (
  worklog_id         INT,
  issue_id           INT,
  issue_key          TEXT,
  hours              DOUBLE,
  work_date          TIMESTAMP,
  work_date_time     TIMESTAMP,
  username           TEXT,
  staff_id           TEXT,
  billing_key        TEXT,
  billing_attributes TEXT,
  activity_id        TEXT,
  activity_name      TEXT,
  work_description   TEXT,
  parent_key         TEXT,
  reporter_user_name TEXT,
  external_id        TEXT,
  external_timestamp TIMESTAMP,
  external_hours     DOUBLE,
  external_result    TEXT,
  custom_field10084  DOUBLE,
  custom_field10100  TEXT,
  custom_field10406  DOUBLE,
  custom_field10501  TIMESTAMP,
  hash_value         TEXT,
  PRIMARY KEY (username, work_date)
);

CREATE TABLE user (
  self          TEXT,
  userkey       TEXT,
  name          TEXT,
  email_address TEXT,
  avatar_url    TEXT,
  display_name  TEXT,
  active        BOOLEAN,
  time_zone     TEXT,
  locale        TEXT,
  PRIMARY KEY (userkey)
);

CREATE TABLE issue (
  id            TEXT,
  issue_key     TEXT,
  issue_url     TEXT,
  summary       TEXT,
  components    MAP<TEXT, TEXT>,
  custom_fields FROZEN<MAP<TEXT, MAP<TEXT, TEXT>>>,
  issue_type    MAP<TEXT, TEXT>,
  PRIMARY KEY (id)
);

CREATE TABLE team (
  id      INT,
  name    TEXT,
  members MAP<TEXT, TIMESTAMP>,
  PRIMARY KEY(id)
);

CREATE INDEX ON team (KEYS(members));