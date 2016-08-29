CREATE KEYSPACE ccd_timesheet
  WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };

-- For use in distributed environments make sure to increase the replication factor --
/*
CREATE KEYSPACE ccd_timesheet
  WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 3 };
*/
