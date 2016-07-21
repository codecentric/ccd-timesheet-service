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
execute Boot.scala
Note: more info to come