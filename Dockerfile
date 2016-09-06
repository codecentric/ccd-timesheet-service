FROM java:8

MAINTAINER Björn Jacobs <bjoern.jacobs@codecentric.de>

ADD target/universal/ccd-timesheet-service.zip app.zip

RUN bash -c 'touch /app.zip'

RUN bash -c 'unzip /app.zip -d /app'

RUN bash -c 'chmod +x /app/bin/ccd-timesheet-service'

ENTRYPOINT ["/app/bin/ccd-timesheet-service","-Djava.security.egd=file:/dev/urandom"]
