package test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executor;

import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.classification.XEventClassifier;
import org.deckfour.xes.in.XParserRegistry;
import org.deckfour.xes.info.XLogInfo;
import org.deckfour.xes.info.XLogInfoFactory;
import org.deckfour.xes.info.impl.XLogInfoImpl;
import org.deckfour.xes.model.XLog;
import org.processmining.contexts.cli.CLIContext;
import org.processmining.framework.connections.Connection;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.connections.ConnectionManager;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.plugin.PluginContextID;
import org.processmining.framework.plugin.PluginDescriptor;
import org.processmining.framework.plugin.PluginExecutionResult;
import org.processmining.framework.plugin.PluginManager;
import org.processmining.framework.plugin.PluginParameterBinding;
import org.processmining.framework.plugin.ProMFuture;
import org.processmining.framework.plugin.Progress;
import org.processmining.framework.plugin.RecursiveCallException;
import org.processmining.framework.plugin.events.Logger.MessageLevel;
import org.processmining.framework.plugin.events.PluginLifeCycleEventListener.List;
import org.processmining.framework.plugin.events.ProgressEventListener.ListenerList;
import org.processmining.framework.plugin.impl.FieldSetException;
import org.processmining.framework.providedobjects.ProvidedObjectManager;
import org.processmining.framework.util.Pair;
import org.processmining.models.connections.GraphLayoutConnection;
import org.processmining.models.connections.petrinets.behavioral.FinalMarkingConnection;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.astar.petrinet.AbstractPetrinetReplayer;
import org.processmining.plugins.astar.petrinet.PetrinetReplayerWithILP;
import org.processmining.plugins.astar.petrinet.PetrinetReplayerWithoutILP;
import org.processmining.plugins.astar.petrinet.impl.AbstractPILPDelegate;
import org.processmining.plugins.connectionfactories.logpetrinet.TransEvClassMapping;
import org.processmining.plugins.petrinet.replayer.algorithms.IPNReplayParameter;
import org.processmining.plugins.petrinet.replayer.algorithms.costbasedcomplete.CostBasedCompleteParam;
import org.processmining.plugins.petrinet.replayresult.PNRepResult;
import org.processmining.plugins.pnml.base.Pnml;
import org.processmining.plugins.pnml.importing.PnmlImportUtils;
import org.processmining.plugins.replayer.replayresult.SyncReplayResult;

import nl.tue.astar.AStarException;
import nl.tue.astar.AStarThread.ASynchronousMoveSorting;
import nl.tue.astar.AStarThread.QueueingModel;
import nl.tue.astar.AStarThread.Type;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

// a log is performed alignment on all the sub-nets and choose the one with the
// lowest score

public class DecomposeAlignment {

	public static int iteration = 0;

	static {
		try {
			System.loadLibrary("lpsolve55");
			System.loadLibrary("lpsolve55j");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		boolean TEST = false;

		int N;
		String logPath;
		String netPath;
		Boolean ILP;

		if (TEST) {
			//number of nets
			N = 5;
			logPath = "C:\\Users\\lcheng\\Desktop\\decomp\\prBm6.xes.gz";
			netPath = "C:\\Users\\lcheng\\Desktop\\decomp\\";
			ILP = true;
		} else {
			N = Integer.valueOf(args[0]);
			logPath = args[1];
			netPath = args[2];
			ILP = Boolean.valueOf(args[3]);
		}

		int[][] AScore = test(N, logPath, netPath, ILP);
		//get the minimal score for each trace cluster

		int X = AScore[0].length;
		int Y = N;

		//System.out.println("2D-Array " + Y + "\t" + X);
		int total = 0;

		for (int x = 0; x < X; x++) {
			int min = 100000;
			for (int y = 0; y < Y; y++) {
				int score = AScore[y][x];
				if (score < min) {
					min = score;
				}
			}
			//System.out.println();
			total += min;
			//System.out.print(min + ",");
			//System.out.println(min);
		}
		//System.out.println();
		System.out.println("With ILP total Cost: " + total);
	}

	//Y is the number of nets
	public static int[][] test(int Y, String logPath, String netPath, Boolean ILP) throws Exception {
		AbstractPILPDelegate.setDebugMode(null);
		PetrinetGraph net = null;
		Marking initialMarking = null;
		Marking[] finalMarkings = null; // only one marking is used so far
		XLog log = null;
		Map<Transition, Integer> costMOS = null; // movements on system
		Map<XEventClass, Integer> costMOT = null; // movements on trace
		TransEvClassMapping mapping = null;

		//log = XParserRegistry.instance().currentDefault().parse(new File("D:\\temp\\exe3.xes.gz")).get(0);
		log = XParserRegistry.instance().currentDefault().parse(new File(logPath)).get(0);

		int X = log.size();
		int[][] AScore = new int[Y][X];

		//ArrayList<Integer>[] AScore = new ArrayList[X];
		long T = 0;
		for (int i = 0; i < Y; i++) {
			String name = String.valueOf(i);
			//net = constructNet("D:\\temp\\" + name + ".pnml");
			net = constructNet(netPath + name + ".pnml");
			//net = constructNet(netPath + "6.pnml");
			//net = constructNet("C:\\Users\\lcheng\\Desktop\\decomp\\" + "prAm6" + ".pnml");
			initialMarking = getInitialMarking(net);
			finalMarkings = getFinalMarkings(net);

			//System.out.println("TRANSITION SIZE "+net.getTransitions().size());

			costMOS = constructMOSCostFunction(net);
			XEventClass dummyEvClass = new XEventClass("DUMMY", 99999);
			XEventClassifier eventClassifier = XLogInfoImpl.STANDARD_CLASSIFIER;
			//XEventClassifier eventClassifier = XLogInfoImpl.NAME_CLASSIFIER;
			costMOT = constructMOTCostFunction(net, log, eventClassifier, dummyEvClass);
			mapping = constructMapping(net, log, dummyEvClass, eventClassifier);

			int iteration = 0;
			for (ASynchronousMoveSorting sort : new ASynchronousMoveSorting[] { ASynchronousMoveSorting.NONE,
					ASynchronousMoveSorting.LOGMOVEFIRST }) {
				//System.out.println("start: " + iteration + " sorting: " + sort);
				long start = System.currentTimeMillis();
				int[] cost1 = DecomposeAlignment.computeCost(costMOS, costMOT, initialMarking, finalMarkings,
						new TestPluginContext(), net, log, mapping, ILP, sort, X);
				long mid = System.currentTimeMillis();

				//System.out.println(" size " + cost1.size());
				long t = mid - start;
				T += t;

				int cost = 0;
				for (int c : cost1) {
					cost += c;
				}
				//System.out.println(" With ILP cost: " + cost + "  t: " + (mid - start));

				System.gc();
				System.out.flush();
				iteration++;

				AScore[i] = cost1;
				System.out.println("subnet " + i + " is done");
				break;
			}
		}
		System.out.println();
		System.out.println("With ILP total Time: " + T);
		return AScore;
	}

	public static int[] computeCost(Map<Transition, Integer> costMOS, Map<XEventClass, Integer> costMOT,
			Marking initialMarking, Marking[] finalMarkings, PluginContext context, PetrinetGraph net, XLog log,
			TransEvClassMapping mapping, boolean useILP, ASynchronousMoveSorting sorting, int X) {
		AbstractPetrinetReplayer<?, ?> replayEngine;
		if (useILP) {
			replayEngine = new PetrinetReplayerWithILP();
		} else {
			replayEngine = new PetrinetReplayerWithoutILP();
		}

		IPNReplayParameter parameters = new CostBasedCompleteParam(costMOT, costMOS);
		parameters.setInitialMarking(initialMarking);
		parameters.setFinalMarkings(finalMarkings[0]);
		parameters.setAsynchronousMoveSort(sorting);
		parameters.setGUIMode(false);
		parameters.setCreateConn(false);
		parameters.setNumThreads(1);
		parameters.setType(Type.PLAIN);
		parameters.setQueueingModel(QueueingModel.DEPTHFIRSTWITHCERTAINTYPRIORITY);

		int[] cost = new int[X];
		int tmp = -1;
		try {
			PNRepResult result = replayEngine.replayLog(context, net, log, mapping, parameters);
			long q = 0;
			long g = 0;
			for (SyncReplayResult res : result) {
				if (res.isReliable()) {
					q += res.getInfo().get(PNRepResult.QUEUEDSTATE);
					g += res.getInfo().get(PNRepResult.NUMSTATEGENERATED);
					//cost.add(((int) res.getInfo().get(PNRepResult.RAWFITNESSCOST).doubleValue())* res.getTraceIndex().size());
					tmp = (int) res.getInfo().get(PNRepResult.RAWFITNESSCOST).doubleValue();
					for (int idx : res.getTraceIndex()) {
						cost[idx] = tmp;
					}
				} else {
					System.err.println("Error in traces " + res.getTraceIndex());
				}
			}
			System.out.println("Queued states: " + q);
			System.out.println("Generated states: " + g);

		} catch (AStarException e) {
			e.printStackTrace();
		}
		return cost;
	}

	public static class DummyContext extends CLIContext {
		public PluginContext getContext() {
			return getMainPluginContext();
		}
	}

	public static PetrinetGraph constructNet(String netFile) {
		DummyContext context = new DummyContext();
		Object[] res = null;
		try {
			res = (Object[]) importNet(context.getContext(), netFile);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		PetrinetGraph net = (PetrinetGraph) res[0];

		return net;
	}

	// to import a PNML to PetrinetGraph
	public static Object[] importNet(PluginContext context, String filename) throws FileNotFoundException, Exception {
		File file = new File(filename);
		PnmlImportUtils utils = new PnmlImportUtils();
		Pnml pnml = utils.importPnmlFromStream(context, new FileInputStream(file), filename, file.length());
		if (pnml == null) {
			return null;
		}

		PetrinetGraph net = PetrinetFactory.newPetrinet(pnml.getLabel());
		Marking marking = new Marking();
		Collection<Marking> finalMarkings = new HashSet<Marking>();
		GraphLayoutConnection layout = new GraphLayoutConnection(net);

		pnml.convertToNet(net, marking, finalMarkings, layout);

		context.addConnection(new InitialMarkingConnection(net, marking));
		for (Marking finalMarking : finalMarkings) {
			context.addConnection(new FinalMarkingConnection(net, finalMarking));
		}
		context.addConnection(layout);

		Object[] objects = new Object[2];
		objects[0] = net;
		objects[1] = marking;
		return objects;
	}

	private static Marking[] getFinalMarkings(PetrinetGraph net) {
		Marking finalMarking = new Marking();

		for (Place p : net.getPlaces()) {
			if (net.getOutEdges(p).isEmpty())
				finalMarking.add(p);
		}

		Marking[] finalMarkings = new Marking[1];
		finalMarkings[0] = finalMarking;

		return finalMarkings;
	}

	private static Marking getInitialMarking(PetrinetGraph net) {
		Marking initMarking = new Marking();

		for (Place p : net.getPlaces()) {
			if (net.getInEdges(p).isEmpty())
				initMarking.add(p);
		}

		return initMarking;
	}

	private static Map<Transition, Integer> constructMOSCostFunction(PetrinetGraph net) {
		Map<Transition, Integer> costMOS = new HashMap<Transition, Integer>();

		for (Transition t : net.getTransitions())
			if (t.isInvisible() || t.getLabel().equals(""))
				costMOS.put(t, 0);
			else
				costMOS.put(t, 1);

		return costMOS;
	}

	private static Map<XEventClass, Integer> constructMOTCostFunction(PetrinetGraph net, XLog log,
			XEventClassifier eventClassifier, XEventClass dummyEvClass) {
		Map<XEventClass, Integer> costMOT = new HashMap<XEventClass, Integer>();
		XLogInfo summary = XLogInfoFactory.createLogInfo(log, eventClassifier);

		for (XEventClass evClass : summary.getEventClasses().getClasses()) {
			costMOT.put(evClass, 1);
		}

		//		costMOT.put(dummyEvClass, 1);

		return costMOT;
	}

	private static TransEvClassMapping constructMapping(PetrinetGraph net, XLog log, XEventClass dummyEvClass,
			XEventClassifier eventClassifier) {
		TransEvClassMapping mapping = new TransEvClassMapping(eventClassifier, dummyEvClass);

		XLogInfo summary = XLogInfoFactory.createLogInfo(log, eventClassifier);
		int count = 0;
		for (Transition t : net.getTransitions()) {
			boolean mapped = false;

			for (XEventClass evClass : summary.getEventClasses().getClasses()) {
				String id = evClass.getId();
				if (t.getLabel().equals(id)) {
					mapping.put(t, evClass);
					mapped = true;
					count++;
					//System.out.println("mapping true\t" + t.getLabel() + " -> " + id);
					break;
				}
			}

			//			if (!mapped && !t.isInvisible()) {
			//				mapping.put(t, dummyEvClass);
			//			}

		}

		if (count == 0)
			System.err.println("lable mapping is wrong");
		else
			System.out.println("lable mapping is right");
		return mapping;
	}

	private static class TestPluginContext implements PluginContext {

		private final Progress progress = new Progress() {

			public void setMinimum(int value) {
			}

			public void setMaximum(int value) {
			}

			public void setValue(int value) {
			}

			public void setCaption(String message) {
			}

			public String getCaption() {
				throw new NotImplementedException();
			}

			public int getValue() {
				throw new NotImplementedException();
			}

			public void inc() {
				System.out.print(".");
			}

			public void setIndeterminate(boolean makeIndeterminate) {
			}

			public boolean isIndeterminate() {
				throw new NotImplementedException();
			}

			public int getMinimum() {
				throw new NotImplementedException();
			}

			public int getMaximum() {
				throw new NotImplementedException();
			}

			public boolean isCancelled() {
				return false;
			}

			public void cancel() {
			}

		};

		public PluginManager getPluginManager() {
			throw new NotImplementedException();

		}

		public ProvidedObjectManager getProvidedObjectManager() {
			throw new NotImplementedException();

		}

		public ConnectionManager getConnectionManager() {
			throw new NotImplementedException();

		}

		public PluginContextID createNewPluginContextID() {
			throw new NotImplementedException();

		}

		public void invokePlugin(PluginDescriptor plugin, int index, Object... objects) {
			throw new NotImplementedException();

		}

		public void invokeBinding(PluginParameterBinding binding, Object... objects) {
			throw new NotImplementedException();

		}

		public Class<? extends PluginContext> getPluginContextType() {
			throw new NotImplementedException();

		}

		public <T, C extends Connection> Collection<T> tryToFindOrConstructAllObjects(Class<T> type,
				Class<C> connectionType, String role, Object... input) throws ConnectionCannotBeObtained {
			throw new NotImplementedException();

		}

		public <T, C extends Connection> T tryToFindOrConstructFirstObject(Class<T> type, Class<C> connectionType,
				String role, Object... input) throws ConnectionCannotBeObtained {
			throw new NotImplementedException();

		}

		public <T, C extends Connection> T tryToFindOrConstructFirstNamedObject(Class<T> type, String name,
				Class<C> connectionType, String role, Object... input) throws ConnectionCannotBeObtained {
			throw new NotImplementedException();

		}

		public PluginContext createChildContext(String label) {
			throw new NotImplementedException();

		}

		public Progress getProgress() {
			return progress;
		}

		public ListenerList getProgressEventListeners() {
			throw new NotImplementedException();

		}

		public List getPluginLifeCycleEventListeners() {
			throw new NotImplementedException();

		}

		public PluginContextID getID() {
			throw new NotImplementedException();

		}

		public String getLabel() {
			throw new NotImplementedException();

		}

		public Pair<PluginDescriptor, Integer> getPluginDescriptor() {
			throw new NotImplementedException();

		}

		public PluginContext getParentContext() {
			throw new NotImplementedException();

		}

		public java.util.List<PluginContext> getChildContexts() {
			throw new NotImplementedException();
		}

		public PluginExecutionResult getResult() {
			throw new NotImplementedException();
		}

		public ProMFuture<?> getFutureResult(int i) {
			throw new NotImplementedException();
		}

		public Executor getExecutor() {
			throw new NotImplementedException();
		}

		public boolean isDistantChildOf(PluginContext context) {
			throw new NotImplementedException();
		}

		public void setFuture(PluginExecutionResult resultToBe) {
			throw new NotImplementedException();

		}

		public void setPluginDescriptor(PluginDescriptor descriptor, int methodIndex)
				throws FieldSetException, RecursiveCallException {
			throw new NotImplementedException();

		}

		public boolean hasPluginDescriptorInPath(PluginDescriptor descriptor, int methodIndex) {
			throw new NotImplementedException();
		}

		public void log(String message, MessageLevel level) {
			System.out.println(message);
		}

		public void log(String message) {
			System.out.println(message);
		}

		public void log(Throwable exception) {
			exception.printStackTrace();
		}

		public org.processmining.framework.plugin.events.Logger.ListenerList getLoggingListeners() {
			throw new NotImplementedException();
		}

		public PluginContext getRootContext() {
			throw new NotImplementedException();
		}

		public boolean deleteChild(PluginContext child) {
			throw new NotImplementedException();
		}

		public <T extends Connection> T addConnection(T c) {
			throw new NotImplementedException();
		}

		public void clear() {
			throw new NotImplementedException();

		}

	}

}
