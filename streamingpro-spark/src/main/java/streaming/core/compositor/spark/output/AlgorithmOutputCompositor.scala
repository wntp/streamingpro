package streaming.core.compositor.spark.output

import java.util
import java.util.concurrent.atomic.AtomicReference

import org.apache.log4j.Logger
import org.apache.spark.ml.BaseAlgorithmEstimator
import org.apache.spark.sql.DataFrame
import serviceframework.dispatcher.{Compositor, Processor, Strategy}
import streaming.core.compositor.spark.streaming.CompositorHelper
import streaming.core.compositor.spark.transformation.SQLCompositor

import scala.collection.JavaConversions._

/**
 * 7/27/16 WilliamZhu(allwefantasy@gmail.com)
 */
class AlgorithmOutputCompositor[T] extends Compositor[T] with CompositorHelper {

  protected var _configParams: util.List[util.Map[Any, Any]] = _

  val logger = Logger.getLogger(classOf[SQLCompositor[T]].getName)

  val mapping = Map(
    "als" -> "org.apache.spark.ml.algs.ALSEstimator",
    "lr" -> "org.apache.spark.ml.algs.LinearRegressionEstimator",
    "lr2" -> "org.apache.spark.ml.algs.LogicRegressionEstimator"
  )

  val instance = new AtomicReference[Any]()

  def algorithm(training: DataFrame, params: Array[Map[String, Any]]) = {
    val clzzName = mapping(config[String]("algorithm", _configParams).get)
    if (instance.get() == null) {
      instance.compareAndSet(null, Class.forName(clzzName).
        getConstructors.head.
        newInstance(training, params))
    }
    instance.get()
  }

  def labelCol = {
    config[String]("label", _configParams).getOrElse("label")
  }

  def featuresCol = {
    config[String]("features", _configParams).getOrElse("features")
  }

  def path = {
    config[String]("path", _configParams).get
  }

  override def initialize(typeFilters: util.List[String], configParams: util.List[util.Map[Any, Any]]): Unit = {
    this._configParams = configParams
  }


  override def result(processors: util.List[Processor[T]], ref: util.List[Strategy[T]], middleResult: util.List[T], params: util.Map[Any, Any]): util.List[T] = {
    val oldDf = middleResult.get(0).asInstanceOf[DataFrame]
    val func = params.get("_func_").asInstanceOf[(DataFrame) => DataFrame]

    try {
      val df = func(oldDf)
      val newParams = _configParams.map(f => f.map(k => (k._1.asInstanceOf[String], k._2)).toMap).toArray
      val bae = algorithm(
        df,
        newParams).
        asInstanceOf[BaseAlgorithmEstimator]
      val model = bae.fit
      model.getClass.getMethod("save", classOf[String]).invoke(model, path)
    } catch {
      case e: Exception => e.printStackTrace()
    }


    params.remove("sql")
    new util.ArrayList[T]()
  }
}
