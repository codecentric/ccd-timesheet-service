package de.codecentric.ccdashboard.service.timesheet.data.cassandracontext

import com.datastax.driver.core.{BoundStatement, Cluster, Row}
import com.typesafe.config.Config
import io.getquill.context.cassandra.CassandraSessionContext
import io.getquill.context.cassandra.util.FutureConversions.toScalaFuture
import io.getquill.util.LoadConfig
import io.getquill.{CassandraContextConfig, NamingStrategy}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

/**
  * @author Björn Jacobs <bjoern.jacobs@codecentric.de>
  */
class CustomCassandraAsyncContext[N <: NamingStrategy](
                                                        cluster: Cluster,
                                                        keyspace: String,
                                                        preparedStatementCacheSize: Long
                                                      )
  extends CassandraSessionContext[N](cluster, keyspace, preparedStatementCacheSize) {

  def this(config: CassandraContextConfig) = this(config.cluster, config.keyspace, config.preparedStatementCacheSize)

  def this(config: Config) = this(CassandraContextConfig(config))

  def this(configPrefix: String) = this(LoadConfig(configPrefix))

  override type RunQueryResult[T] = Future[List[T]]
  override type RunQuerySingleResult[T] = Future[T]
  override type RunActionResult = Future[Unit]
  override type RunBatchActionResult = Future[Unit]

  def executeQuery[T](cql: String, prepare: BoundStatement => BoundStatement = identity, extractor: Row => T = identity[Row] _)(implicit ec: ExecutionContext): Future[List[T]] = {
    println(s"***: $cql")
    val prepared = super.prepare(cql)
    val bs = prepare(prepared)

    println(bs)

    session.executeAsync(prepare(super.prepare(cql)))
      .map(_.all.asScala.toList.map(extractor))
  }

  def executeQuerySingle[T](cql: String, prepare: BoundStatement => BoundStatement = identity, extractor: Row => T = identity[Row] _)(implicit ec: ExecutionContext): Future[T] =
    executeQuery(cql, prepare, extractor).map(handleSingleResult)

  def executeAction[T](cql: String, prepare: BoundStatement => BoundStatement = identity)(implicit ec: ExecutionContext): Future[Unit] = {
    println(s"***: $cql")
    session.executeAsync(prepare(super.prepare(cql))).map(_ => ())
  }

  def executeBatchAction(groups: List[BatchGroup])(implicit ec: ExecutionContext): Future[Unit] =
    Future.sequence {
      groups.map {
        case BatchGroup(cql, prepare) =>
          prepare.map(executeAction(cql, _))
      }.flatten
    }.map(_ => ())
}
