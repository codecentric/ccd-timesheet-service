FROM cassandra:3

MAINTAINER Björn Jacobs <bjoern.jacobs@codecentric.de>

RUN bash -c "sed -i -e 's/#-Xms.*/-Xms2G/g;s/#-Xmx.*/-Xmx2G/g' /etc/cassandra/jvm.options"
