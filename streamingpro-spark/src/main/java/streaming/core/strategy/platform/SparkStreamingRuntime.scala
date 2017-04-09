package streaming.core.strategy.platform

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.{List => JList, Map => JMap}

import net.csdn.common.logging.Loggers
import org.apache.spark.sql.hive.thriftserver.HiveThriftServer3
import org.apache.spark.streaming._
import org.apache.spark.streaming.scheduler.{StreamingListener, StreamingListenerBatchCompleted}
import org.apache.spark.{SparkConf, SparkContext}
import streaming.common.{SQLContextHolder, SparkCompatibility}

import scala.collection.JavaConversions._

/**
 * 4/27/16 WilliamZhu(allwefantasy@gmail.com)
 */
class SparkStreamingRuntime(_params: JMap[Any, Any]) extends StreamingRuntime with SparkPlatformHelper with PlatformManagerListener {

  self =>

  private val logger = Loggers.getLogger(classOf[SparkStreamingRuntime])

  def name = "SPARK_STREAMING"

  var streamingContext: StreamingContext = createRuntime

  streamingContext.addStreamingListener(new BatchStreamingListener(this))


  private var _streamingRuntimeInfo: SparkStreamingRuntimeInfo = new SparkStreamingRuntimeInfo(this)

  override def streamingRuntimeInfo = _streamingRuntimeInfo

  override def resetRuntimeOperator(runtimeOperator: RuntimeOperator) = {
    _streamingRuntimeInfo.sparkStreamingOperator = new SparkStreamingOperator(this)
  }

  override def configureStreamingRuntimeInfo(streamingRuntimeInfo: StreamingRuntimeInfo) = {
    _streamingRuntimeInfo = streamingRuntimeInfo.asInstanceOf[SparkStreamingRuntimeInfo]
  }

  override def params = _params

  def createRuntime = {

    val conf = new SparkConf()
    params.filter(f => f._1.toString.startsWith("spark.")).foreach { f =>
      conf.set(f._1.toString, f._2.toString)
    }
    if (params.containsKey("streaming.master")) {
      conf.setMaster(params.get("streaming.master").toString)
    }
    conf.setAppName(params.get("streaming.name").toString)
    val duration = params.getOrElse("streaming.duration", "10").toString.toInt

    params.filter(f => f._1.toString.startsWith("streaming.spark.")).foreach { f =>
      val key = f._1.toString
      conf.set(key.substring("streaming".length + 1), f._2.toString)
    }


   val ssc =  if (params.containsKey("streaming.checkpoint")) {
      val checkpoinDir = params.get("streaming.checkpoint").toString
      StreamingContext.getActiveOrCreate(checkpoinDir,
        () => {
          val ssc = new StreamingContext(conf, Seconds(duration))
          if (SparkStreamingRuntime.sparkContext.get() == null) {
            SparkStreamingRuntime.sparkContext.set(ssc.sparkContext)
          }
          ssc.checkpoint(checkpoinDir)
          ssc
        }
      )
    } else {
      if (SparkStreamingRuntime.sparkContext.get() == null) {
        SparkStreamingRuntime.sparkContext.set(new SparkContext(conf))
      }
      new StreamingContext(SparkStreamingRuntime.sparkContext.get(), Seconds(duration))
    }
    ssc

  }

  if (params.getOrElse("streaming.compatibility.crossversion", "false").toString.toBoolean) {
    SparkCompatibility.preCompile(this)
  }

  if (SQLContextHolder.sqlContextHolder == null) {
    SQLContextHolder.setActive(createSQLContextHolder(params, this))
    params.put("_sqlContextHolder_", SQLContextHolder.getOrCreate())
  }

  override def destroyRuntime(stopGraceful: Boolean, stopSparkContext: Boolean = false) = {

    //clear inputDStreamId
    _streamingRuntimeInfo.jobNameToInputStreamId.clear()
    streamingContext.stop(stopSparkContext, stopGraceful)
    SparkStreamingRuntime.clearLastInstantiatedContext()
    SparkStreamingRuntime.sparkContext.set(null)
    true
  }

  override def startRuntime = {

    streamingRuntimeInfo.jobNameToInputStreamId.foreach { f =>
      _streamingRuntimeInfo.sparkStreamingOperator.directKafkaRecoverSource.restoreJobSate(f._1)
      _streamingRuntimeInfo.sparkStreamingOperator.testInputRecoverSource.restoreJobSate(f._1)
    }

    streamingContext.start()
    _streamingRuntimeInfo.jobNameToState.clear()
    this
  }

  override def awaitTermination = {
    streamingContext.awaitTermination()
  }

  override def startThriftServer: Unit = {

  }

  override def startHttpServer: Unit = {}

  override def processEvent(event: Event): Unit = {
    event match {
      case e: JobFlowGenerate =>
        val inputStreamId = streamingRuntimeInfo.sparkStreamingOperator.inputStreamId(e.index)
        streamingRuntimeInfo.jobNameToInputStreamId.put(e.jobName, inputStreamId)
      case _ =>
    }
  }

  SparkStreamingRuntime.setLastInstantiatedContext(self)


}

class SparkStreamingRuntimeInfo(ssr: SparkStreamingRuntime) extends StreamingRuntimeInfo {
  val jobNameToInputStreamId = new ConcurrentHashMap[String, Int]()
  val jobNameToState = new ConcurrentHashMap[String, Any]()
  var lastTime: Time = _
  var sparkStreamingOperator: SparkStreamingOperator = new SparkStreamingOperator(ssr)
}

class BatchStreamingListener(runtime: SparkStreamingRuntime) extends StreamingListener {

  override def onBatchCompleted(batchCompleted: StreamingListenerBatchCompleted): Unit = {
    val time = batchCompleted.batchInfo.batchTime
    val operator = runtime.streamingRuntimeInfo.sparkStreamingOperator

    //first check kafka offset which on direct approach, later we will add more sources
    operator.directKafkaRecoverSource.saveJobSate(time)
    operator.testInputRecoverSource.saveJobSate(time)

    runtime.streamingRuntimeInfo.lastTime = time
  }

}

object SparkStreamingRuntime {

  var sparkContext = new AtomicReference[SparkContext]()

  private val INSTANTIATION_LOCK = new Object()

  /**
   * Reference to the last created SQLContext.
   */
  @transient private val lastInstantiatedContext = new AtomicReference[SparkStreamingRuntime]()

  /**
   * Get the singleton SQLContext if it exists or create a new one using the given SparkContext.
   * This function can be used to create a singleton SQLContext object that can be shared across
   * the JVM.
   */
  def getOrCreate(params: JMap[Any, Any]): SparkStreamingRuntime = {
    INSTANTIATION_LOCK.synchronized {
      if (lastInstantiatedContext.get() == null) {
        new SparkStreamingRuntime(params)
      }
    }
    PlatformManager.getOrCreate.register(lastInstantiatedContext.get())
    lastInstantiatedContext.get()
  }

  private[platform] def clearLastInstantiatedContext(): Unit = {
    INSTANTIATION_LOCK.synchronized {
      PlatformManager.getOrCreate.unRegister(lastInstantiatedContext.get())
      lastInstantiatedContext.set(null)
    }
  }

  private[platform] def setLastInstantiatedContext(sparkStreamingRuntime: SparkStreamingRuntime): Unit = {
    INSTANTIATION_LOCK.synchronized {
      lastInstantiatedContext.set(sparkStreamingRuntime)
    }
  }
}
