package de.codecentric.ccdashboard.service.timesheet.util

import com.datastax.driver.core.Cluster.Builder
import com.datastax.driver.core.{PoolingOptions, SocketOptions}
import com.typesafe.config.Config
import io.getquill.CassandraContextConfig

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class CassandraContextConfigWithOptions(config: Config, socketOptions: Option[SocketOptions] = None, poolingOptions: Option[PoolingOptions] = None) extends CassandraContextConfig(config) {
  override def builder: Builder = {
    val b = super.builder

    if (socketOptions.isDefined) b.withSocketOptions(socketOptions.get)

    if (poolingOptions.isDefined) b.withPoolingOptions(poolingOptions.get)

    b
  }
}
