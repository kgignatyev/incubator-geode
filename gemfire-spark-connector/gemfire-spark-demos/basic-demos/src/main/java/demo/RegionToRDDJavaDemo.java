package demo;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaSparkContext;
import static io.pivotal.gemfire.spark.connector.javaapi.GemFireJavaUtil.*;

/**
 * This Spark application demonstrates how to expose a region in GemFire as a RDD using GemFire
 * Spark Connector with Java.
 * <p>
 * In order to run it, you will need to start GemFire cluster, and run demo PairRDDSaveJavaDemo
 * first to create some data in the region.
 * <p>
 * Once you compile and package the demo, the jar file basic-demos_2.10-0.5.0.jar
 * should be generated under gemfire-spark-demos/basic-demos/target/scala-2.10/. 
 * Then run the following command to start a Spark job:
 * <pre>
 *   <path to spark>/bin/spark-submit --master=local[2] --class demo.RegionToRDDJavaDemo \
 *       <path to>/basic-demos_2.10-0.5.0.jar <locator host>:<port>
 * </pre>
 */
public class RegionToRDDJavaDemo {

  public static void main(String[] argv) {

    if (argv.length != 1) {
      System.err.printf("Usage: RegionToRDDJavaDemo <locators>\n");
      return;
    }
    
    SparkConf conf = new SparkConf().setAppName("RegionToRDDJavaDemo"); 
    conf.set(GemFireLocatorPropKey, argv[0]);
    JavaSparkContext sc = new JavaSparkContext(conf);

    JavaPairRDD<String, String> rdd = javaFunctions(sc).gemfireRegion("str_str_region");
    System.out.println("=== gemfireRegion =======\n" + rdd.collect() + "\n=========================");
    
    sc.stop();
  }
}
