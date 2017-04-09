package streaming.core.compositor.spark.streaming.output

import java.util

import org.apache.log4j.Logger
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{Row, DataFrame}
import org.apache.spark.streaming.dstream.DStream
import serviceframework.dispatcher.{Compositor, Processor, Strategy}

import scala.collection.mutable.ArrayBuffer


class SQLUnitTestCompositor[T] extends Compositor[T] {

  private var _configParams: util.List[util.Map[Any, Any]] = _
  val logger = Logger.getLogger(classOf[SQLUnitTestCompositor[T]].getName)

  override def initialize(typeFilters: util.List[String], configParams: util.List[util.Map[Any, Any]]): Unit = {
    this._configParams = configParams
  }

  val result: ArrayBuffer[Array[org.apache.spark.sql.Row]] = new ArrayBuffer[Array[Row]]()

  override def result(alg: util.List[Processor[T]], ref: util.List[Strategy[T]], middleResult: util.List[T], params: util.Map[Any, Any]): util.List[T] = {
    val dstream = middleResult.get(0).asInstanceOf[DStream[String]]
    val func = params.get("_func_").asInstanceOf[(RDD[String]) => DataFrame]
    dstream.foreachRDD { rdd =>
      try {
        val df = func(rdd)
        result += df.collect()
      } catch {
        case e: Exception => e.printStackTrace()
      }

    }
    params.remove("sql")
    new util.ArrayList[T]()
  }
}
