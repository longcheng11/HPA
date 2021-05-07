import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.connections.GraphLayoutConnection;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.pnml.base.FullPnmlElementFactory;
import org.processmining.plugins.pnml.base.Pnml;
import org.processmining.plugins.utils.ProvidedObjectHelper;

public class NetDecom {

	private String iPath = null;
	private String oPath = null;
	private int DIS = 1;

	public NetDecom(String in, String out, int dis) {
		iPath = in;
		oPath = out;
		DIS = dis;
	}

	public void run() throws Exception {
		// TODO Auto-generated method stub
		String netfile = iPath;

		// construct the original graph
		PetriNet4 G = new PetriNet4(netfile);
		List<PetriNet4> gv = new ArrayList<PetriNet4>();
		gv.add(G);
		System.out.println("transition in G: " + G.getTransNum());

		// store the net to be decomposed
		SortedMap<Integer, List<PetriNet4>> dGraph = new TreeMap();
		dGraph.put(G.getTransNum(), gv);

		// for un-decomposed net
		List<PetriNet4> oGraph = new ArrayList<PetriNet4>();

		while (dGraph.size() != 0) {
			int key = dGraph.lastKey();

			List<PetriNet4> v = dGraph.get(key);
			PetriNet4 dg = v.get(0);
			if (dg.getReachList().size() != 0) {
				// decompose the subgraph with the most transitions
				Place[] splitPair = dg.getSplitPair(DIS);
				if (splitPair[1] == null) { // just update its reachList
					dg.getReachList().remove(splitPair[0]);
				} else { // decomposition
					System.out.println("decompose g with transitions: " + key);
					List<PetriNet4> sNets = Decompose(splitPair, dg);
					// System.out.println("get new nets\t" + sNets.size());
					put(sNets, dGraph);
					dg.clear();

					// remove the decompose dg
					v.remove(dg);
					if (v.size() == 0)
						dGraph.remove(key);
				}
			} else {
				oGraph.add(dg);
				// remove the moved dg
				v.remove(dg);
				if (v.size() == 0)
					dGraph.remove(key);
			}

		}
		System.out.println("decomposition is done, and final nets\t" + oGraph.size());

		for (PetriNet4 n3 : oGraph) {
			System.out.print(n3.getTransNum() + ",");
		}
		System.out.println();

		// get the subGraphs in the form of petri net
		List<PetrinetGraph> subGraphs = getPetriNets(oGraph, G);

		// output the petri nets
		exportSubNet(subGraphs);
	}

	//export the net to PNML file
	private void exportSubNet(List<PetrinetGraph> subGraphs) {
		DummyContext context = new DummyContext();
		// PnmlExportNetToEPNML pnmlSerializer = new PnmlExportNetToEPNML();

		int num = 0;
		String path = oPath;
		for (PetrinetGraph spg : subGraphs) {
			File of = new File(path + String.valueOf(num) + ".pnml");
			try {
				// pnmlSerializer.exportPetriNetToEPNMLFile(context.getContext(),
				// (Petrinet) spg, of);
				exportPetriNetToPNMLOrEPNMLFile(context.getContext(), spg, of, Pnml.PnmlType.EPNML);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			num++;
		}
	}

	private List<PetrinetGraph> getPetriNets(List<PetriNet4> oGraph, PetriNet4 G) {
		List<PetrinetGraph> subGraphs = new ArrayList<PetrinetGraph>(oGraph.size());

		int num = 1;
		Transition tt = null;
		for (PetriNet4 blk : oGraph) {
			num++;
			PetrinetGraph net = PetrinetFactory.newPetrinet(String.valueOf(num));

			// add places
			Map<Place, Place> p2p = new HashMap<Place, Place>();
			for (Place p : blk.getPlaces()) {
				Place pp = net.addPlace(p.getLabel());
				p2p.put(p, pp);
			}

			// add transitions
			Map<Transition, Transition> t2t = new HashMap<Transition, Transition>();
			for (Transition t : blk.getTranstions()) {
				tt = net.addTransition(t.getLabel());
				tt.setInvisible(t.isInvisible());
				t2t.put(t, tt);
			}

			// add arcs
			Iterator iter = blk.get_p2t().entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry1 = (Map.Entry) iter.next();
				Place key = (Place) entry1.getKey();
				List<Transition> value = (List<Transition>) entry1.getValue();
				for (Transition v : value) {
					net.addArc(p2p.get(key), t2t.get(v));
				}
			}

			iter = blk.get_t2p().entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				Transition key = (Transition) entry.getKey();
				List<Place> value = (List<Place>) entry.getValue();
				for (Place v : value) {
					net.addArc(t2t.get(key), p2p.get(v));
				}
			}

			for (Transition t : net.getTransitions()) {
				// System.out.print(t.getLabel() + ",");
			}
			// System.out.println();
			subGraphs.add(net);
		}

		return subGraphs;
	}

	//export the net to PNML file
	private void exportPetriNetToPNMLOrEPNMLFile(PluginContext context, PetrinetGraph spg, File file,
			Pnml.PnmlType type) throws IOException {
		Marking marking = getInitialMarking(spg);
		Collection<Marking> finalMarkings = getFinalMarkings(spg);
		Petrinet net = (Petrinet) spg;

		GraphLayoutConnection layout;
		try {
			layout = context.getConnectionManager().getFirstConnection(GraphLayoutConnection.class, context, net);
		} catch (ConnectionCannotBeObtained e) {
			layout = new GraphLayoutConnection(net);
		}
		HashMap<PetrinetGraph, Marking> markedNets = new HashMap<PetrinetGraph, Marking>();
		HashMap<PetrinetGraph, Collection<Marking>> finalMarkedNets = new HashMap<PetrinetGraph, Collection<Marking>>();
		markedNets.put(net, marking);
		finalMarkedNets.put(net, finalMarkings);

		Pnml pnml = new Pnml();
		FullPnmlElementFactory factory = new FullPnmlElementFactory();
		synchronized (factory) {
			pnml.setFactory(factory);
			pnml = pnml.convertFromNet(markedNets, finalMarkedNets, layout);
		}
		pnml.setType(type);
		updateName(context, pnml, net);
		String text = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>\n" + pnml.exportElement(pnml);

		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
		bw.write(text);
		bw.close();
	}

	private void updateName(PluginContext context, Pnml pnml, PetrinetGraph net) {
		String name = ProvidedObjectHelper.getProvidedObjectLabel(context, net);
		if (name != null) {
			pnml.setName(name);
		}
	}

	//put a net into a list of net
	private void put(List<PetriNet4> sNets, SortedMap<Integer, List<PetriNet4>> dGraph) {
		for (PetriNet4 n : sNets) {
			int key = n.getTransNum();
			List<PetriNet4> v = dGraph.get(key);
			if (v == null) {
				v = new ArrayList<PetriNet4>();
				v.add(n);
				dGraph.put(key, v);
			} else {
				v.add(n);
			}
		}
	}

	//decompose current net based on split pair
	private List<PetriNet4> Decompose(Place[] splitPair, PetriNet4 G) {
		List<PetriNet4> BLKS = new ArrayList<PetriNet4>();

		Place start = splitPair[0];
		Place end = splitPair[1];

		List<Transition> lt = G.get_p2t().get(start);
		for (Transition t : lt) {
			PetriNet4 blk = new PetriNet4();
			blk.add(start, t);

			// generate a subgraph from G
			generateBlock(G, t, end, blk);
			BLKS.add(blk);
		}

		// remove the extracted blks from the G
		for (PetriNet4 blk : BLKS) {
			G.removeBlk(blk);
		}

		// get a new list of subgraph, blk+public, and update subgraph
		HashMap<Place, List<Place>> nReachList = null;
		HashMap<Place, List<Place>> gReachList = G.getReachList();
		gReachList.remove(start);
		for (PetriNet4 blk : BLKS) {
			blk.addBlock(G);
			blk.update(gReachList, G.getSink());
		}

		return BLKS;
	}

	//generate net block based on the starting and end point
	private void generateBlock(PetriNet4 G, Transition t, Place end, PetriNet4 blk) {
		// System.out.println("Phase start------"+t.getLabel()+ " " +
		// end.getLabel());
		List<Transition> tempT = null;
		List<Place> tempP = null;

		HashMap<Place, List<Transition>> p2t = G.get_p2t();
		HashMap<Transition, List<Place>> t2p = G.get_t2p();

		List<Place> neighbourP = t2p.get(t);
		HashSet<Place> currentLP = new HashSet<Place>();

		for (Place p : neighbourP) {
			if (!p.equals(end)) {
				currentLP.add(p);
			}
			blk.add(t, p);
		}

		HashSet<Transition> currentLT = null;
		while (currentLP.size() != 0) {
			tempT = new ArrayList<Transition>();
			tempP = new ArrayList<Place>();
			currentLT = new HashSet<Transition>();

			for (Place p : currentLP) {
				tempT = p2t.get(p);
				if (tempT == null) {
					System.out.println(
							"debug error 1 " + p.getLabel() + " " + G.getSink().getLabel() + " " + end.getLabel());
				}
				blk.add(p, tempT);

				for (Transition t1 : tempT) {
					currentLT.add(t1);
				}
			}

			currentLP = new HashSet<Place>();
			// get all the places
			for (Transition t1 : currentLT) {
				tempP = t2p.get(t1);
				blk.add(t1, tempP);

				// remove the reach points
				for (Place p : tempP) {
					if (!p.equals(end)) {
						currentLP.add(p);
					}
				}
			}
			// System.out.println("currentLP size is "+currentLP.size());
		}
	}

	
	//get final markings
	private Collection<Marking> getFinalMarkings(PetrinetGraph net) {
		Marking finalMarking = new Marking();

		for (Place p : net.getPlaces()) {
			if (net.getOutEdges(p).isEmpty())
				finalMarking.add(p);
		}

		Collection<Marking> finalMarkings = new HashSet<Marking>();
		finalMarkings.add(finalMarking);
		return finalMarkings;
	}

	//get initial marking
	private Marking getInitialMarking(PetrinetGraph net) {
		Marking initMarking = new Marking();

		for (Place p : net.getPlaces()) {
			if (net.getInEdges(p).isEmpty())
				initMarking.add(p);
		}

		return initMarking;
	}

}
