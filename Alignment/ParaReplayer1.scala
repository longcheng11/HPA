import org.apache.spark.SparkContext
import java.util.{HashMap, Map}

import org.deckfour.xes.extension.std.XConceptExtension
import org.deckfour.xes.factory.XFactoryRegistry
import org.deckfour.xes.extension.std.XLifecycleExtension
import org.deckfour.xes.model.{XLog, XTrace}
import org.jbpt.petri.NetSystem
import org.jbpt.petri.io.PNMLSerializer
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph
import org.processmining.models.graphbased.directed.petrinet.elements.Place
import org.processmining.models.graphbased.directed.petrinet.elements.Transition
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory

import scala.collection.mutable.ListBuffer
import scala.io.Source

//remove if not needed
import scala.collection.JavaConversions._


/**
  * Created by lcheng on 7/12/2017.
  */
object ParaReplayer1 {

  def main(args: Array[String]) {
    val sc = new SparkContext(args(0), "ParaReplayer1", System.getenv("SPARK_HOME"))

    //val path = "hdfs://ais-hadoop-m:8020/user/lcheng/"
    val path = args(1)
    //val log = sc.textFile(path + "test.log")
    val N = args(2).toInt
    val log = sc.textFile(path + args(3), N)
    val netpath = args(4)
    val M = args(5).toInt //number of sub models
    val ILP=args(6).toBoolean

    //multiple sub nets
    var sub_nets: Array[String] = new Array[String](M)
    for (i <- 0 until M) {
      val inputStrings = Source.fromFile(args(4) + i.toString + ".pnml").getLines
      var netString: String = ""
      for (line <- inputStrings) netString += line
      sub_nets(i) = netString
    }

    val broadCast_Net = sc.broadcast(sub_nets)

    //convert partition to XLog
    val sublogs = log.mapPartitions(
      iter => {
        val iterlog: Array[XLog] = new Array[XLog](1)
        iterlog(0) = convertToXLog(iter)
        iterlog.iterator
      }
    )
    //println("number of traces : " +sublogs.count())

    //parallel processing
    val cost = sublogs.mapPartitions(
      iter => {
        //construct local net
        val LnetString = broadCast_Net.value
        val localNets: ListBuffer[PetrinetGraph] = new ListBuffer[PetrinetGraph]()
        for (netstring <- LnetString) {
          val localNet = constructNet(netstring.getBytes)
          localNets += localNet
        }

        //alignment
        var iCost:Array[Array[Int]] = new Array[Array[Int]](localNets.size)
        var idx: Int = 0
        for (iLog <- iter) {
          //each partition has only one xlog
          for (subnet <- localNets) {
            val aLign = new Alignment(subnet, iLog, ILP)
            iCost(idx) = aLign.run()
            idx = idx + 1
          }
        }

        val X = iCost(0).size
        val Y = iCost.size

        var minC = new Array[Int](X)
        for (i <- 0 until X) {
          minC(i) = 1000000000 //a maxmium value can be reached
          for (j <- 0 until Y) {
            if (iCost(j)(i) < minC(i)) minC(i) = iCost(j)(i)
          }
        }
        minC.iterator
      }).collect()

    //println("size is: " + cost.length)
    //print out the cost on each partition
    var sum: Int = 0
    for (c <- cost) {
      sum += c
     // println(c)
    }
    //println("sum of cost is: " + sum)

    println("------job is done-------")
    sc.stop()

  }

  private def convertToXLog(traces: Iterator[String]): XLog = {
    val factory = XFactoryRegistry.instance().currentDefault()
    val log = factory.createLog()
    for (traceString <- traces) {
      val trace: XTrace = factory.createTrace()
      val eventNames = traceString.split(",")

      for (i <- 1 until eventNames.length) {
        val event = factory.createEvent()
        XConceptExtension.instance().assignName(event, eventNames(i))
        XLifecycleExtension.instance().assignTransition(event, "complete")
        trace.add(event)
      }
      log.add(trace)
    }
    log
  }


  private def constructNet(netFile: Array[Byte]): PetrinetGraph = {
    val PNML: PNMLSerializer = new PNMLSerializer()
    val sys: NetSystem = PNML.parse(netFile)

    //todo make sure that the parameter has no meaning here
    val net: PetrinetGraph = PetrinetFactory.newPetrinet("")

    // places
    val p2p: Map[org.jbpt.petri.Place, Place] = new HashMap[org.jbpt.petri.Place, Place]()
    for (p <- sys.getPlaces) {
      val pp: Place = net.addPlace(p.toString)
      p2p.put(p, pp)
    }

    // transitions
    val l: Int = 0
    val t2t: Map[org.jbpt.petri.Transition, Transition] = new HashMap[org.jbpt.petri.Transition, Transition]()
    for (t <- sys.getTransitions) {
      val tt: Transition = net.addTransition(t.getLabel)
      tt.setInvisible(t.isSilent)
      t2t.put(t, tt)
    }

    // flow
    for (f <- sys.getFlow) {
      if (f.getSource.isInstanceOf[org.jbpt.petri.Place]) {
        net.addArc(p2p.get(f.getSource), t2t.get(f.getTarget))
      } else {
        net.addArc(t2t.get(f.getSource), p2p.get(f.getTarget))
      }
    }

    // add unique start node
    if (sys.getSourceNodes.isEmpty) {
      val i: Place = net.addPlace("START_P")
      val t: Transition = net.addTransition("")
      t.setInvisible(true)
      net.addArc(i, t)
      for (p <- sys.getMarkedPlaces) {
        net.addArc(t, p2p.get(p))
      }
    }
    net
  }


}
