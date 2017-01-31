FROM cassandra:3

MAINTAINER Björn Jacobs <bjoern.jacobs@codecentric.de>

RUN bash -c "sed -i -e 's/#-Xms.*/-Xms1G/g;s/#-Xmx.*/-Xmx1G/g' /etc/cassandra/jvm.options"
