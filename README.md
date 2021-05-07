# HPA
Test code for Computing Optimal Alignments

0, The used process to generate large event log with noise is in "Gen".

1, The decomposition code is in the "Decompose".

2, The HPA and d-HPA implementations are in "Alignment".

2.1 A sample of run HPA using jar is shown as below. There are three input parameters. 

> java -classpath test.jar test/DecomposeAlignment 7  final_log/trace_50_005.xes.gz prAm6/ true

2.2 A sample of the job submission command for d-HPA is shown as below. The "spark://master:7077" is the sparkcontext, "hdfs://master:8020/final/" is the file input path over HDFS, "32" is the number of executor cores, "trace_50_005.txt" is the input event log in the forms of trace strings, which is located in the HDFS. "/home/lcheng/ParaReplayer/Final/" is the path for generated submodels, "7" is the number of the submodels, "true" means using ILP

> spark-submit \
  --class ParaReplayer1 \
  --master spark://master:7077 \
  --executor-memory 64G \
  /home/lcheng/ParaReplayer/Final/ParaReplayer.jar \
  spark://master:7077 \
  hdfs://master:8020/final/  \
  32 \
  trace_50_005.txt \
 /home/lcheng/ParaReplayer/Final/  \
 7 \
 true
  
  
3, How to set Spark, please refer to http://spark.apache.org/.

4, If any questions, please email to cheng03(AT)ieee.org

### License

The theme is available as open source under the terms of the [MIT License](https://opensource.org/licenses/MIT).
