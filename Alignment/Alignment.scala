import java.util.{HashMap, Map}
import scala.util.control.Breaks._

import org.deckfour.xes.classification.{XEventClass, XEventClassifier}
import org.deckfour.xes.info.{XLogInfo, XLogInfoFactory}
import org.deckfour.xes.info.impl.XLogInfoImpl
import org.deckfour.xes.model.XLog
import org.processmining.framework.plugin.PluginContext
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph
import org.processmining.models.graphbased.directed.petrinet.elements.Transition
import org.processmining.models.semantics.petrinet.Marking
import org.processmining.plugins.astar.petrinet.{PetrinetReplayerWithILP, PetrinetReplayerWithoutILP}
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayParameter
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompleteParam
import org.processmining.plugins.petrinet.replayresult.PNRepResult

import nl.tue.astar.AStarException
import nl.tue.astar.AStarThread.ASynchronousMoveSorting
import nl.tue.astar.AStarThread.QueueingModel
import nl.tue.astar.AStarThread.Type

//remove if not needed
import scala.collection.JavaConversions._

/**
  * Created by lcheng on 7/12/2017.
  */
class Alignment(pNet: PetrinetGraph, sublog: XLog, ilp: Boolean) {

  private var net: PetrinetGraph = null
  private var initialMarking: Marking = null

  // only one marking is used so far
  private var finalMarkings: Array[Marking] = null
  private var log: XLog = null

  // movements on system
  private var costMOS: Map[Transition, Integer] = null

  // movements on trace
  private var costMOT: Map[XEventClass, Integer] = null
  private var mapping: TransEvClassMapping = null

  //ILP or not
  private var ILP: Boolean = false

  def run(): Array[Int] = {
    net = pNet
    log = sublog
    ILP = ilp

    initialMarking = getInitialMarking(net)
    finalMarkings = getFinalMarkings(net)

    costMOS = constructMOSCostFunction(net)

    val dummyEvClass: XEventClass = new XEventClass("DUMMY", 99999)
    val eventClassifier: XEventClassifier = XLogInfoImpl.STANDARD_CLASSIFIER

    costMOT = constructMOTCostFunction(net, log, eventClassifier, dummyEvClass)
    mapping = constructMapping(net, log, dummyEvClass, eventClassifier)

    //computer cost
    val cost: Array[Int] = computeCost(costMOS, costMOT, initialMarking, finalMarkings, null, net, log, mapping, ILP)

    cost
  }


  private def getInitialMarking(net: PetrinetGraph): Marking = {
    val initMarking: Marking = new Marking()
    for (p <- net.getPlaces if net.getInEdges(p).isEmpty) initMarking.add(p)
    initMarking
  }

  private def getFinalMarkings(net: PetrinetGraph): Array[Marking] = {
    val finalMarking: Marking = new Marking()
    for (p <- net.getPlaces if net.getOutEdges(p).isEmpty) finalMarking.add(p)
    val finalMarkings: Array[Marking] = Array.ofDim[Marking](1)
    finalMarkings(0) = finalMarking
    finalMarkings
  }

  private def constructMOSCostFunction(net: PetrinetGraph): Map[Transition, Integer] = {
    val costMOS: Map[Transition, Integer] = new HashMap[Transition, Integer]()
    for (t <- net.getTransitions)
      if (t.isInvisible || t.getLabel.==("")) costMOS.put(t, 0)
      else costMOS.put(t, 1)
    costMOS
  }

  private def constructMOTCostFunction(net: PetrinetGraph, log: XLog, eventClassifier: XEventClassifier, dummyEvClass: XEventClass): Map[XEventClass, Integer] = {
    val costMOT: Map[XEventClass, Integer] = new HashMap[XEventClass, Integer]()
    val summary: XLogInfo = XLogInfoFactory.createLogInfo(log, eventClassifier)
    for (evClass <- summary.getEventClasses.getClasses) {
      costMOT.put(evClass, 1)
    }
    costMOT
  }

  private def constructMapping(net: PetrinetGraph, log: XLog, dummyEvClass: XEventClass, eventClassifier: XEventClassifier): TransEvClassMapping = {
    val mapping: TransEvClassMapping = new TransEvClassMapping(eventClassifier, dummyEvClass)
    val summary: XLogInfo = XLogInfoFactory.createLogInfo(log, eventClassifier)
    var count: Int = 0
    for (t <- net.getTransitions) {
      var mapped: Boolean = false
      breakable {
        for (evClass <- summary.getEventClasses.getClasses) {
          val id: String = evClass.getId
          if (t.getLabel == id) {
            mapping.put(t, evClass)
            mapped = true
            count = count + 1
            break
          }
        }
      }
    }
    if (count == 0)
      println("lable mapping is wrong")
    else
      println("lable mapping is right")

    mapping
  }

  private def computeCost(costMOS: Map[Transition, Integer], costMOT: Map[XEventClass, Integer], initialMarking: Marking, finalMarkings: Array[Marking],
                          context: PluginContext, net: PetrinetGraph, log: XLog, mapping: TransEvClassMapping, useILP: Boolean): Array[Int] = {

    val replayEngine =
      if (useILP) new PetrinetReplayerWithILP()
      else new PetrinetReplayerWithoutILP()

    val parameters: IPNReplayParameter = new CostBasedCompleteParam(costMOT, costMOS)

    parameters.setInitialMarking(initialMarking)
    parameters.setFinalMarkings(finalMarkings(0))
    parameters.setGUIMode(false)
    parameters.setCreateConn(false)
    parameters.setNumThreads(1)
    parameters.setType(Type.PLAIN)
    parameters.setQueueingModel(QueueingModel.DEPTHFIRSTWITHCERTAINTYPRIORITY)

    val num = log.size()
    var cost: Array[Int] = new Array[Int](num)
    val result: PNRepResult = replayEngine.replayLog(context, net, log, mapping, parameters)
    for (res <- result) {
      val tmp = res.getInfo.get(PNRepResult.RAWFITNESSCOST).doubleValue().toInt
      for (idx <- res.getTraceIndex()) {
        cost(idx) = tmp
      }
    }
    cost
  }


}
