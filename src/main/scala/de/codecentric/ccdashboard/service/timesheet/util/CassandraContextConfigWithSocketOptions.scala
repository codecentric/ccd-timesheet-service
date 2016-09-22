package de.codecentric.ccdashboard.service.timesheet.util

import com.datastax.driver.core.Cluster.Builder
import com.datastax.driver.core.SocketOptions
import com.typesafe.config.Config
import io.getquill.CassandraContextConfig

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class CassandraContextConfigWithSocketOptions(config: Config, so: SocketOptions) extends CassandraContextConfig(config) {
  override def builder: Builder = {
    super.builder.withSocketOptions(so)
  }
}
