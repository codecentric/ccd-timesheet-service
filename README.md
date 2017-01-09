# ccd-timesheet-service
## Description #
This is the ccDashboard Timesheet service.

It will serve the timesheet data for other micro-services that together compose the ccDashboard tool.

Currently supported data sources:
*   Jira Tempo API

Currently supported databases:
*   Every database that is supported by the Slick framework

## Configuration ##
Start at application.conf and jiraclient.conf

## Access token generation ##
After configuring jiraclient.conf with the available information, execute JiraOAuthTokenRequest and follow the instructions

## Start ##

1. Create docker image from ./DockerCassandra
2. Create new docker container with `docker run --expose=9042 -p 9042:9042 --name cassandra <$imageName>` (Make sure container name is "cassandra")
3. Run sbt with `-Dtimesheet-service.database-config-key=cassandra.localconfig`
4. Service is now ready at `http://0.0.0.0:8080/`