package org.apache.spark.ml.algs

import org.apache.spark.ml.classification.{LogisticRegressionModel, LogisticRegression}
import org.apache.spark.ml.evaluation.{BinaryClassificationEvaluator, Evaluator, RegressionEvaluator}
import org.apache.spark.ml.regression.LinearRegression
import org.apache.spark.ml.tuning.TrainValidationSplit
import org.apache.spark.ml.{BaseAlgorithmEstimator, Estimator, Model}
import org.apache.spark.sql.DataFrame

/**
  * 7/27/16 WilliamZhu(allwefantasy@gmail.com)
  */
class LogicRegressionEstimator(training: DataFrame, params: Array[Map[String, Any]]) extends BaseAlgorithmEstimator {

   val lr = new LogisticRegression()

   override def name: String = "lr"

   override def fit: Model[_] = {
     val paramGrid = mlParams(params.tail)
     val vectorSize = if (params.head.contains("dicTable")) {
       training.sqlContext.table(params.head.getOrElse("dicTable", "").toString).count()
     } else 0l
     if (params.length <= 1) {
       lr.fit(source(training,vectorSize.toInt), paramGrid(0))
     } else {
       val trainValidationSplit = new TrainValidationSplit()
         .setEstimator(lr)
         .setEvaluator(evaluator)
         .setEstimatorParamMaps(paramGrid)
         .setTrainRatio(0.8)
       trainValidationSplit.fit(source(training,vectorSize.toInt))
     }

   }

   override def algorithm: Estimator[_] = lr

   override def evaluator: Evaluator = new BinaryClassificationEvaluator()

 }
