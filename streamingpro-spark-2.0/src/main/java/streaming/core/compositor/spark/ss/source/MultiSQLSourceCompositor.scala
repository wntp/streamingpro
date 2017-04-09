package streaming.core.compositor.spark.ss.source

import java.util

import net.sf.json.JSONArray
import org.apache.log4j.Logger
import org.apache.spark.sql.execution.streaming.MemoryStream
import serviceframework.dispatcher.{Compositor, Processor, Strategy}
import streaming.core.CompositorHelper

import scala.collection.JavaConversions._

/**
  * 11/21/16 WilliamZhu(allwefantasy@gmail.com)
  */
class MultiSQLSourceCompositor[T] extends Compositor[T] with CompositorHelper {
  private var _configParams: util.List[util.Map[Any, Any]] = _

  val logger = Logger.getLogger(classOf[MultiSQLSourceCompositor[T]].getName)

  override def initialize(typeFilters: util.List[String], configParams: util.List[util.Map[Any, Any]]): Unit = {
    this._configParams = configParams
  }

  override def result(alg: util.List[Processor[T]], ref: util.List[Strategy[T]], middleResult: util.List[T], params: util.Map[Any, Any]): util.List[T] = {
    val spark = sparkSession(params)

    _configParams.foreach { sourceConfig =>

      val name = sourceConfig.getOrElse("name", "").toString
      val _cfg = sourceConfig.map(f => (f._1.toString, f._2.toString)).map { f =>
        (f._1, params.getOrElse(s"streaming.sql.source.${name}.${f._1}", f._2).toString)
      }.toMap

      val sourcePath = _cfg("path")

      _cfg("format") match {
        case "kafka" | "socket" =>
          val df = spark.readStream.format(_cfg("format")).options(
            (_cfg - "format" - "path" - "outputTable").map(f => (f._1.toString, f._2.toString))).load(sourcePath)
          df.createOrReplaceTempView(_cfg("outputTable"))
        case "mock" =>
          import spark.implicits._
          implicit val sqlContext = spark.sqlContext
          val inputData = MemoryStream[String]
          inputData.addData(JSONArray.fromObject(_cfg("data")).map(f=>f.toString).seq)
          val df = inputData.toDS()
          df.createOrReplaceTempView(_configParams(0)("outputTable").toString)

        case _ =>
          val df = spark.read.format(_cfg("format")).options(
            (_cfg - "format" - "path" - "outputTable" - "data").map(f => (f._1.toString, f._2.toString))).load(sourcePath)
          df.createOrReplaceTempView(_cfg("outputTable"))
      }

    }
    List()
  }
}
