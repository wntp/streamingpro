package streaming.core.compositor

import java.io.File
import java.nio.charset.Charset

import com.google.common.io.Files
import net.sf.json.{JSONArray, JSONObject}
import org.apache.spark.streaming.BasicStreamingOperation
import streaming.common.SQLContextHolder
import streaming.core.Dispatcher
import streaming.core.compositor.spark.output.{MultiSQLOutputCompositor, SQLUnitTestCompositor}
import streaming.core.strategy.platform.SparkRuntime

import scala.collection.JavaConversions._

/**
  * 8/29/16 WilliamZhu(allwefantasy@gmail.com)
  */
class BatchSpec extends BasicStreamingOperation {


  val batchParams = Array(
    "-streaming.master", "local[2]",
    "-streaming.name", "unit-test",
    "-streaming.rest", "false",
    "-streaming.platform", "spark",
    "-streaming.enableHiveSupport", "false",
    "-streaming.spark.service", "false",
    "-streaming.unittest", "true"
  )


  "batch" should "run normally" in {
    val file = new java.io.File("/tmp/hdfsfile/abc.txt")
    Files.createParentDirs(file)
    Files.write("abc\tbbc", file, Charset.forName("utf-8"))

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/batch-test.json")) { runtime: SparkRuntime =>

      val sd = Dispatcher.dispatcher(null)
      val strategies = sd.findStrategies("convert_data_parquet").get
      strategies.size should be(1)

      val output = strategies.head.compositor.last.asInstanceOf[SQLUnitTestCompositor[Any]]
      val result = output.result.head

      result.size should be(1)

      result.head.getAs[String]("tp") should be("bbc")

      file.delete()

    }
  }

  "batch-with-inline-script" should "run normally" in {
    val file = new java.io.File("/tmp/hdfsfile/abc.txt")
    Files.createParentDirs(file)
    Files.write("kk\tbb", file, Charset.forName("utf-8"))

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/batch-test-inline-script.json")) { runtime: SparkRuntime =>

      val sd = Dispatcher.dispatcher(null)
      val strategies = sd.findStrategies("convert_data_parquet").get
      strategies.size should be(1)

      val output = strategies.head.compositor.last.asInstanceOf[SQLUnitTestCompositor[Any]]
      val result = output.result.head

      result.size should be(1)

      result.head.getAs[String]("a") should be("kk")

      println("a=>" + result.head.getAs[String]("a") + ",b=>" + result.head.getAs[String]("b"))

      file.delete()

    }
  }

  "batch-with-file-script-with-schema" should "run normally" in {
    val file = new java.io.File("/tmp/hdfsfile/abc.txt")
    Files.createParentDirs(file)
    Files.write("kk\tbb", file, Charset.forName("utf-8"))

    val script =
      s""" val Array(a,b)=doc("raw").toString.split("\t")
           Map("a"->a,"b"->b)
       """.stripMargin
    val scripFile = new File("/tmp/raw_process.scala")
    Files.write(script, scripFile, Charset.forName("utf-8"))


    val script2 =
      s"""
         Some(StructType(Array(StructField("a", StringType, true),StructField("b", StringType, true))))
       """.stripMargin
    val scripFile2 = new File("/tmp/raw_schema.scala")
    Files.write(script2, scripFile2, Charset.forName("utf-8"))

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/batch-test-file-script-schema.json")) { runtime: SparkRuntime =>

      val sd = Dispatcher.dispatcher(null)
      val strategies = sd.findStrategies("convert_data_parquet").get
      strategies.size should be(1)

      val output = strategies.head.compositor.last.asInstanceOf[SQLUnitTestCompositor[Any]]
      val result = output.result.head

      result.size should be(1)

      result.head.getAs[String]("a") should be("kk")

      println("a=>" + result.head.getAs[String]("a") + ",b=>" + result.head.getAs[String]("b"))

      file.delete()
      scripFile.delete()
      scripFile2.delete()

    }
  }


  "batch-with-file-script" should "run normally" in {
    val file = new java.io.File("/tmp/hdfsfile/abc.txt")
    Files.createParentDirs(file)
    Files.write("kk\tbb", file, Charset.forName("utf-8"))

    val script =
      s""" val Array(a,b)=rawLine.split("\t")
           Map("a"->a,"b"->b)
       """.stripMargin
    val scripFile = new File("/tmp/raw_process.scala")
    Files.write(script, scripFile, Charset.forName("utf-8"))

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/batch-test-file-script.json")) { runtime: SparkRuntime =>

      val sd = Dispatcher.dispatcher(null)
      val strategies = sd.findStrategies("convert_data_parquet").get
      strategies.size should be(1)

      val output = strategies.head.compositor.last.asInstanceOf[SQLUnitTestCompositor[Any]]
      val result = output.result.head

      result.size should be(1)

      result.head.getAs[String]("a") should be("kk")

      println("a=>" + result.head.getAs[String]("a") + ",b=>" + result.head.getAs[String]("b"))

      file.delete()
      scripFile.delete()

    }
  }


  "batch-with-inline-script-doc" should "run normally" in {
    val file = new java.io.File("/tmp/hdfsfile/abc.txt")
    Files.createParentDirs(file)
    Files.write("kk\tbb", file, Charset.forName("utf-8"))

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/batch-test-script-doc.json")) { runtime: SparkRuntime =>

      val sd = Dispatcher.dispatcher(null)
      val strategies = sd.findStrategies("convert_data_parquet").get
      strategies.size should be(1)

      val output = strategies.head.compositor.last.asInstanceOf[SQLUnitTestCompositor[Any]]
      val result = output.result.head

      result.size should be(1)

      result.head.getAs[String]("a") should be("kk")

      println("a=>" + result.head.getAs[String]("a") + ",b=>" + result.head.getAs[String]("b"))

      file.delete()

    }
  }

  "batch-script-df" should "run normally" in {
    val file = new java.io.File("/tmp/hdfsfile/abc.txt")
    Files.createParentDirs(file)
    val obj = new JSONObject()
    obj.put("a", "yes")
    obj.put("b", "no")
    Files.write(obj.toString(), file, Charset.forName("utf-8"))


    withBatchContext(setupBatchContext(batchParams, "classpath:///test/batch-script-df.json")) { runtime: SparkRuntime =>

      val sd = Dispatcher.dispatcher(null)
      val strategies = sd.findStrategies("batch-console").get
      strategies.size should be(1)

      val output = strategies.head.compositor.last.asInstanceOf[MultiSQLOutputCompositor[_]]

      val configParams = getCompositorParam(output)

      val finalOutputTable = configParams(0)("inputTableName").toString

      val name  = SQLContextHolder.getOrCreate().getOrCreate().table(finalOutputTable).schema.get(0).name

      name should startWith("t")

      println(name)

      file.delete()

    }
  }

  "batch console" should "run normally" in {
    val file = new java.io.File("/tmp/hdfsfile/abc.txt")
    Files.createParentDirs(file)
    val obj = new JSONObject()
    obj.put("a", "yes")
    obj.put("b", "no")
    Files.write(obj.toString(), file, Charset.forName("utf-8"))

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/batch-console.json")) { runtime: SparkRuntime =>

      val sd = Dispatcher.dispatcher(null)
      val strategies = sd.findStrategies("batch-console").get
      strategies.size should be(1)
      file.delete()

    }
  }

  "batch  with repartition" should "should have only 3 file" in {

    val file = new java.io.File("/tmp/hdfsfile/abc.txt")
    Files.createParentDirs(file)
    var obj = new JSONObject()
    obj.put("a", "yes")
    obj.put("b", "no")
    val array = new JSONArray()
    array.add(obj)
    obj = new JSONObject()
    obj.put("a", "wow")
    obj.put("b", "no")
    array.add(obj)
    array.add(obj)

    Files.write(array.toString(), file, Charset.forName("utf-8"))

    withBatchContext(setupBatchContext(batchParams, "classpath:///test/batch-repartition.json")) { runtime: SparkRuntime =>

      val sd = Dispatcher.dispatcher(null)
      val strategies = sd.findStrategies("batch-console").get
      strategies.size should be(1)
      val output = strategies.head.compositor.last.asInstanceOf[MultiSQLOutputCompositor[_]]
      val configParams = getCompositorParam(output)
      val fileNum = new File(configParams(0)("path").toString.substring("file://".length)).list().filter(f => f.startsWith("part-r-"))
      fileNum should have size (3)
      file.delete()

    }
  }


}