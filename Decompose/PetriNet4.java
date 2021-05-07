import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.connections.GraphLayoutConnection;
import org.processmining.models.connections.petrinets.behavioral.FinalMarkingConnection;
import org.processmining.models.connections.petrinets.behavioral.InitialMarkingConnection;
import org.processmining.models.graphbased.directed.DirectedGraphNode;
import org.processmining.models.graphbased.directed.petrinet.PetrinetEdge;
import org.processmining.models.graphbased.directed.petrinet.PetrinetGraph;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.pnml.base.Pnml;
import org.processmining.plugins.pnml.importing.PnmlImportUtils;

public class PetriNet4 {

	// the graph
	private HashMap<Place, List<Transition>> p2t = null;
	private HashMap<Transition, List<Place>> t2p = null;

	// the places, decompose priority depend on the number of transitions
	private Set<Place> places = null;
	private Set<Transition> transitions = null;

	// source and sink
	private PetrinetGraph net = null;
	private Place sink = null;

	// reach list, will be updated based on the decomposition
	private HashMap<Place, List<Place>> reachList = null;

	// get the G
	public PetriNet4(String netFile) throws FileNotFoundException, Exception {
		p2t = new HashMap<Place, List<Transition>>();
		t2p = new HashMap<Transition, List<Place>>();
		places = new HashSet<Place>();
		transitions = new HashSet<Transition>();
		reachList = new HashMap<Place, List<Place>>();
		// get the values of G
		ParseNet(netFile);
		calReachList();
		// printG();
	}

	public PetriNet4() {
		p2t = new HashMap<Place, List<Transition>>();
		t2p = new HashMap<Transition, List<Place>>();
		places = new HashSet<Place>();
		transitions = new HashSet<Transition>();
		reachList = new HashMap<Place, List<Place>>();
	}

	public void clear() {
		p2t = null;
		t2p = null;
		places = null;
		transitions = null;
		reachList = null;
		sink = null;
	}
	
	

	// update the transition, places
	public void update(HashMap<Place, List<Place>> gReachList, Place end) {
		
		for(Place p: p2t.keySet()) places.add(p);
		for(Transition t: t2p.keySet()) transitions.add(t);
		sink = end;
		places.add(sink);
		//System.out.println("debug 11 "+places.size());
		
		// update reachList
		Iterator entries = gReachList.entrySet().iterator();
		while (entries.hasNext()) {
			Map.Entry entry = (Map.Entry) entries.next();
			Place k = (Place) entry.getKey();
			List<Place> v = (List<Place>) entry.getValue();
			if (places.contains(k))
				reachList.put(k, v);
		}
	}

	// get split points, set priority and check confirm by BFS
	public Place[] getSplitPair(int DIS) {
		Place[] splitPair = new Place[2];
		HashMap<Place, List<Place>> b_reachList = getReverse3();
		List<Place> cands = new ArrayList<Place>();

		// get starting point
		for (Place p : reachList.keySet()) {
			if (!b_reachList.containsKey(p)) // first or non-in point
				cands.add(p);
		}
		Place start = getFirst(cands);

		// get the responsible end point
		cands = reachList.get(start);
		int[] score = new int[cands.size()];
		int len = cands.size();
		for (int i = 0; i < len; i++) {
			Place x = cands.get(i);
			for (int j = 0; j < len; j++) {
				if (j != i) {
					Place y = cands.get(j);
					if (DFSearch(x, y)) {
						score[i] += 1;
					}
				}
			}
		}

		// System.out.println("score\t"+ score[0]);

		// based on the score to set the priority of the cands
		Place end = null;
		int dis = 0;
		LP1: for (int i = 0; i < len; i++) {
			int max = -1;
			int idx = 0;
			for (int j = 0; j < len; j++) {
				if (score[j] > max) {
					max = score[j];
					idx = j;
				}
			}

			end = cands.get(idx);
			score[idx] = -2;
			dis = Converge(start, end);
			if (dis > 0)
				break LP1;
		}

		// only decomposition for the case with convergence distance > 4
		if (dis > DIS) {
			splitPair[0] = start;
			splitPair[1] = end;
			System.out.println("current split pairs and their dis " + start.getLabel() + "\t" + end.getLabel() + "\t" + dis);
		} else {
			splitPair[0] = start;
			splitPair[1] = null;
			System.out.println("distance is small, no decomposition");
		}

		return splitPair;
	}

	// based on BFS searching
	private int Converge(Place start, Place end) {
		List<Transition> tempT = null;
		List<Place> tempP = null;
		int count = 0;
		HashSet<Place> currentLP = new HashSet<Place>();
		HashSet<Transition> currentLT = new HashSet<Transition>();
		for (Transition t : p2t.get(start)) {
			currentLT.add(t);
		}

		do {
			tempT = new ArrayList<Transition>();
			tempP = new ArrayList<Place>();

			for (Place p : currentLP) {
				tempT = p2t.get(p);
				if (tempT == null) {
					return -1;
				} else {
					currentLT.addAll(tempT);
				}
			}
			currentLP = new HashSet<Place>();

			for (Transition t1 : currentLT) {
				tempP = t2p.get(t1);
				// remove the reach points
				for (Place p : tempP) {
					if (!p.equals(end)) {
						currentLP.add(p);
					}
				}
			}
			currentLT = new HashSet<Transition>();
			count++;
			// System.out.println("current\t"+ currentLP.size());
		} while (currentLP.size() != 0);

		return count;
	}

	// get split points
	public Place[] getSplitPair1() {
		Place[] splitPair = new Place[2];
		HashMap<Place, List<Place>> b_reachList = getReverse3();
		List<Place> cands = new ArrayList<Place>();

		// get starting point
		for (Place p : reachList.keySet()) {
			if (!b_reachList.containsKey(p)) // first or non-in point
				cands.add(p);
		}
		Place start = getFirst(cands);

		// get the responsible end point
		Place end = null;
		LP1: for (Place p : reachList.get(start)) {
			Boolean check = true;
			LP2: for (Transition t : p2t.get(start)) {
				if (!DFSearchT(t, p)) {
					check = false;
					break LP2;
				}
			}
			if (check) {
				end = p;
				break LP1;
			}
		}

		splitPair[0] = start;
		splitPair[1] = end;
		System.out.println("current split pairs " + start.getLabel() + "\t" + end.getLabel());

		return splitPair;
	}

	// get split points
	public Place[] getSplitPair2() {
		Place[] splitPair = new Place[2];
		HashMap<Place, List<Place>> b_reachList = getReverse3();
		List<Place> cands = new ArrayList<Place>();

		// get starting point
		for (Place p : reachList.keySet()) {
			if (!b_reachList.containsKey(p)) // first or non-in point
				cands.add(p);
		}
		Place start = getFirst(cands);

		// get the responsible end point
		cands = new ArrayList<Place>();
		for (Place p : reachList.get(start)) {
			if (!reachList.containsKey(p)) // final or non-out point
				cands.add(p);
		}
		Place end = getFinal(cands);

		splitPair[0] = start;
		splitPair[1] = end;
		// System.out.println("current split pairs " + start.getLabel() + "\t" +
		// end.getLabel());

		return splitPair;
	}

	// get the earliest place that can be split
	private Place getFirst(List<Place> cands) {
		Place start = null;

		int len = cands.size();
		for (int i = 0; i < len; i++) {
			start = cands.get(i);
			if (i == len - 1)
				return start; // reach the end

			// the place can be reached can not be needed one
			LP2: for (int j = i + 1; j < len; j++) {
				Place test = cands.get(j);
				if (DFSearch(test, start))
					break LP2;
				if (j == len - 1)
					return start;
			}
		}
		return start;
	}

	// get the place that can reach all other places
	private Place getFinal(List<Place> cands) {
		Place end = null;
		HashSet<Place> check = new HashSet<Place>();
		int len = cands.size();
		if (len == 1) {
			end = cands.get(0);
		} else {
			for (int i = 0; i < len; i++) {
				Place start = cands.get(i);
				// check with all other places
				int count = 0;
				LP2: for (int j = 0; j < len; j++) {
					if (i != j) {
						Place test = cands.get(j);
						if (DFSearch(start, test))
							break LP2;
						count++;
						if (count == len - 1)
							check.add(start);
					}
				}
			}
			if (check.size() > 1) {
				System.out.println("--sink is selected");
				end = sink;
			} else {
				for (Place p : check)
					end = p;
			}
		}
		return end;
	}

	// calculate ReachList
	private void calReachList() {
		// get the reverse list
		HashMap<Place, List<Transition>> B_p2t = getReverse1();
		
		// get source and sink places
		Place source = null;
		for (Place p : places) {
			if (p2t.get(p) == null) {
				sink = p;
			} else if (B_p2t.get(p) == null) {
				source = p;
			}
		}
		//System.out.println("source and sink: "+source.getLabel()+"\t"+sink.getLabel());

		// split and join places
		HashSet<Place> src = new HashSet<Place>();
		HashSet<Place> des = new HashSet<Place>();

		// process source and sink place
		if (p2t.get(source).size() > 1) { // the source place
			src.add(source);
		}
		if (B_p2t.get(sink).size() > 1) { // the source place
			des.add(sink);
		}

		// process the rest places, remove single in and single out
		for (Place p : places) {
			if (!p.equals(source) && !p.equals(sink)) {
				if (p2t.get(p).size() > 1) { // a split place
					src.add(p);
				}
				if (B_p2t.get(p) == null) {
					System.out.println("error " + p.getLabel());
				}
				if (B_p2t.get(p).size() > 1) { // a join place
					des.add(p);
				}
			}
		}
		System.out.println("before circle: src and des size: " + src.size() + " " + des.size());

		ArrayList<Place> src1 = new ArrayList<Place>();
		ArrayList<Place> des1 = new ArrayList<Place>();
		for (Place p1 : src) {
			if (!DFSearch(p1, p1)) {
				src1.add(p1);
			}
		}
		for (Place p2 : des) {
			if (!DFSearch(p2, p2)) {
				des1.add(p2);
			}
		}

		System.out.println("after circle: src and des size: " + src1.size() + " " + des1.size());

		// get the reached pairs but without circle
		HashSet<Place> circle = new HashSet<Place>();
		for (Place p1 : src1) {
			for (Place p2 : des1) {
				if (DFSearch(p1, p2)) {
					if (!DFSearch(p2, p1)) {
						// (p1,p2) a legal candidate
						List<Place> v1 = reachList.get(p1);
						if (v1 == null) {
							v1 = new ArrayList<Place>();
							v1.add(p2);
							reachList.put(p1, v1);
						} else {
							v1.add(p2);
						}
					} else {
						// there is a loop between p1 and p2
						circle.add(p1);
						circle.add(p2);
					}
				}
			}
		}
		// System.out.println("reachList\t " + reachList.size() + "\t" +
		// circle.size());

		// refine the reachPairs: remove the circle points
		Iterator iter = reachList.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			Place key = (Place) entry.getKey();
			List<Place> value = (List<Place>) entry.getValue();

			if (circle.contains(key)) {
				reachList.remove(key);
			} else {
				if (value == null)
					System.out.println("error");
				for (Place v : value) {
					if (circle.contains(v)) {
						value.remove(v);
					}
				}
			}
		}
	}

	// calculate ReachList
	private void calReachList1() {
		// get the reverse list
		HashMap<Place, List<Transition>> B_p2t = getReverse1();

		// get source and sink places
		Place source = null;
		for (Place p : places) {
			if (p2t.get(p) == null) {
				sink = p;
			} else if (B_p2t.get(p) == null) {
				source = p;
			}
		}

		// split and join places
		ArrayList<Place> src = new ArrayList<Place>();
		ArrayList<Place> des = new ArrayList<Place>();

		// process source and sink place
		if (p2t.get(source).size() > 1) { // the source place
			src.add(source);
		}
		if (B_p2t.get(sink).size() > 1) { // the source place
			des.add(sink);
		}

		// process the rest places, remove single in and single out
		for (Place p : places) {
			if (!p.equals(source) && !p.equals(sink)) {
				if (p2t.get(p).size() > 1) { // a split place
					src.add(p);
				}
				if (B_p2t.get(p).size() > 1) { // a join place
					des.add(p);
				}
			}
		}
		System.out.println(places.size() + " " + transitions.size());

		for (Place p1 : src) {
			System.out.print("|" + p1.getLabel());
		}
		System.out.println();
		for (Place p1 : des) {
			System.out.print("|" + p1.getLabel());
		}
		System.out.println();

		// get the reached pairs but without circle
		HashSet<Place> circle = new HashSet<Place>();
		for (Place p1 : src) {
			for (Place p2 : des) {
				if (DFSearch(p1, p2)) {
					if (!DFSearch(p2, p1)) {
						// (p1,p2) a legal candidate
						List<Place> v1 = reachList.get(p1);
						if (v1 == null) {
							v1 = new ArrayList<Place>();
							v1.add(p2);
							reachList.put(p1, v1);
						} else {
							v1.add(p2);
						}
					} else {
						// there is a loop between p1 and p2
						circle.add(p1);
						circle.add(p2);
					}
				}
			}
		}

		System.out.println(
				"src and des size: " + src.size() + " " + des.size() + "\t" + reachList.size() + "\t" + circle.size());

		// refine the reachPairs: remove the circle points
		Iterator iter = reachList.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			Place key = (Place) entry.getKey();
			List<Place> value = (List<Place>) entry.getValue();

			if (circle.contains(key)) {
				reachList.remove(key);
			} else {
				if (value == null)
					System.out.println("error");
				for (Place v : value) {
					if (circle.contains(v)) {
						value.remove(v);
					}
				}
			}
		}

		System.out.println("reachList size: " + reachList.size());
	}

	public HashMap<Place, List<Transition>> get_p2t() {
		return p2t;
	}

	public HashMap<Transition, List<Place>> get_t2p() {
		return t2p;
	}

	public HashMap<Place, List<Place>> getReachList() {
		return reachList;
	}

	public Place getSink() {
		return sink;
	}

	public int getTransNum() {
		return transitions.size();
	}
	
	public Set<Transition> getTranstions() {
		return transitions;
	}
	
	public Set<Place> getPlaces() {
		return places;
	}
	
	public void addBlock(PetriNet4 blk) {
		HashMap<Place, List<Transition>> pt = blk.get_p2t();
		HashMap<Transition, List<Place>> tp = blk.get_t2p();

		// add pt
		Iterator iter = pt.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry1 = (Map.Entry) iter.next();
			Place key = (Place) entry1.getKey();
			List<Transition> value = (List<Transition>) entry1.getValue();
			add(key, value);
		}

		// add tp
		iter = tp.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			Transition key = (Transition) entry.getKey();
			List<Place> value = (List<Place>) entry.getValue();
			add(key, value);
		}
	}

	public void add(Place p, Transition t) {
		List<Transition> v = p2t.get(p);
		if (v == null) {
			v = new ArrayList<Transition>();
			v.add(t);
			p2t.put(p, v);
		} else {
			v.add(t);
		}
	}

	public void add(Transition t, Place p) {
		List<Place> v1 = t2p.get(t);
		if (v1 == null) {
			v1 = new ArrayList<Place>();
			v1.add(p);
			t2p.put(t, v1);
		} else {
			v1.add(p);
		}
	}

	public void add(Place p, List<Transition> lt) {
		if (lt != null) {
			for (Transition t : lt) {
				add(p, t);
			}
		}
	}

	public void add(Transition t, List<Place> lp) {
		if (lp != null) {
			for (Place p : lp) {
				add(t, p);
			}
		}
	}

	public void removeBlk(PetriNet4 blk) {
		HashMap<Place, List<Transition>> pt = blk.get_p2t();
		HashMap<Transition, List<Place>> tp = blk.get_t2p();

		// remove pt
		Iterator iter = pt.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry1 = (Map.Entry) iter.next();
			Place key = (Place) entry1.getKey();
			List<Transition> value = (List<Transition>) entry1.getValue();
			remove(key, value);
		}

		// add tp
		iter = tp.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			Transition key = (Transition) entry.getKey();
			List<Place> value = (List<Place>) entry.getValue();
			remove(key, value);
		}

	}

	private void remove(Place p, List<Transition> lt) {
		if (lt != null) {
			for (Transition t : lt) {
				remove(p, t);
			}
		}
	}

	private void remove(Transition t, List<Place> lp) {
		if (lp != null) {
			for (Place p : lp) {
				remove(t, p);
			}
		}
	}

	private void remove(Place p, Transition t) {
		List<Transition> v = p2t.get(p);
		if (v != null) {
			if (v.size() == 1 || v.size() == 0) {
				p2t.remove(p);
			} else {
				v.remove(t);
			}
		}
	}

	private void remove(Transition t, Place p) {
		List<Place> v = t2p.get(t);
		if (v != null) {
			if (v.size() == 1 || v.size() == 0) {
				t2p.remove(t);
			} else {
				v.remove(p);
			}
		}
	}

	// to import a PNML to PetrinetGraph
	private Object[] importNet(PluginContext context, String filename) throws FileNotFoundException, Exception {
		File file = new File(filename);
		PnmlImportUtils utils = new PnmlImportUtils();
		Pnml pnml = utils.importPnmlFromStream(context, new FileInputStream(file), filename, file.length());
		if (pnml == null) {
			/*
			 * No PNML found in file. Fail.
			 */
			return null;
		}
		/*
		 * PNML file has been imported. Now we need to convert the contents to a
		 * regular Petri net.
		 */
		PetrinetGraph net = PetrinetFactory.newPetrinet(pnml.getLabel());

		/*
		 * Create fresh marking(s) and layout.
		 */
		Marking marking = new Marking();
		Collection<Marking> finalMarkings = new HashSet<Marking>();
		GraphLayoutConnection layout = new GraphLayoutConnection(net);

		/*
		 * Initialize the Petri net, marking(s), and layout from the PNML
		 * element.
		 */
		pnml.convertToNet(net, marking, finalMarkings, layout);

		/*
		 * Add a connection from the Petri net to the marking(s) and layout.
		 */
		context.addConnection(new InitialMarkingConnection(net, marking));
		for (Marking finalMarking : finalMarkings) {
			context.addConnection(new FinalMarkingConnection(net, finalMarking));
		}
		context.addConnection(layout);

		/*
		 * Return the net and the marking.
		 */
		Object[] objects = new Object[2];
		objects[0] = net;
		objects[1] = marking;
		return objects;
	}

	// construct a PN based on input string, only used for the initial graph G
	private void ParseNet(String netFile) throws FileNotFoundException, Exception {
		DummyContext context = new DummyContext();
		Object[] res = (Object[]) importNet(context.getContext(), netFile);
		net = (PetrinetGraph) res[0];

		for (PetrinetEdge edge : net.getEdges()) {
			DirectedGraphNode s = (DirectedGraphNode) edge.getSource();
			DirectedGraphNode e = (DirectedGraphNode) edge.getTarget();
			if (s instanceof Place) {
				Place ss = (Place) s;
				Transition ee = (Transition) e;

				List<Transition> v = p2t.get(ss);
				if (v == null) {
					v = new ArrayList<Transition>();
					v.add(ee);
					p2t.put(ss, v);
				} else {
					v.add(ee);
				}
				places.add(ss);
				transitions.add(ee);
			} else {
				Place ss = (Place) e;
				Transition ee = (Transition) s;
				List<Place> v = t2p.get(ee);
				if (v == null) {
					v = new ArrayList<Place>();
					v.add(ss);
					t2p.put(ee, v);
				} else {
					v.add(ss);
				}
				transitions.add(ee);
				places.add(ss);
			}
		}
	}

	// check whether a transition can reach a place based under specified
	// conditions
	private boolean DFSearchT(Transition n1, Place n2) {
		// check all the neighbor places
		List<Place> np = t2p.get(n1);
		for (Place p : np) {
			if (p.equals(n2))
				return true;
			else if (DFSearch(p, n2))
				return true;
		}
		return false;
	}

	private boolean DFSearch(Place n1, Place n2) {
		Stack<Place> stp = new Stack<Place>();
		Stack<Transition> stt = new Stack<Transition>();
		Set<Place> visitedP = new HashSet<Place>();
		Set<Transition> visitedT = new HashSet<Transition>();

		Place currentP = n1;

		// first step
		List<Transition> nt = p2t.get(currentP);
		if (nt != null) {
			for (Transition t : nt) {
				stt.push(t);
			}
		}
		visitedP.add(currentP);

		while (!stt.isEmpty()) {
			Transition currentT = stt.pop();

			for (Place p : t2p.get(currentT)) {
				if (p.equals(n2)) {
					return true;
				} else if (!visitedP.contains(p)) {
					stp.push(p);
				}
			}
			visitedT.add(currentT);

			// it is possible the stp is empty
			if (!stp.isEmpty()) {
				currentP = stp.pop();
				nt = p2t.get(currentP);
				if (nt != null) {
					for (Transition t : nt) {
						if (!visitedT.contains(t))
							stt.push(t);
					}
				}
				visitedP.add(currentP);
			}
		}
		return false;
	}

	private boolean DFSearch1(Place n1, Place n2) {
		Stack<Place> stp = new Stack<Place>();
		Stack<Transition> stt = new Stack<Transition>();
		Set<Place> visitedP = new HashSet<Place>();
		Set<Transition> visitedT = new HashSet<Transition>();

		Place currentP = n1;
		do {
			if (!visitedP.contains(currentP)) {
				// get the neighbored transitions in stack
				List<Transition> nt = p2t.get(currentP);
				if (nt != null) { // reach the end
					for (Transition t : nt) {
						stt.push(t);
					}
				}
				visitedP.add(currentP);

				if (stt.isEmpty())
					return false;

				Transition currentT = stt.pop();
				while (visitedT.contains(currentT)) {
					if (!stt.isEmpty())
						currentT = stt.pop();
					else
						return false;
				}

				for (Place p : t2p.get(currentT)) {
					stp.push(p);
				}

				visitedT.add(currentT);
			}

			if (stp.isEmpty())
				return false;

			currentP = stp.pop();

		} while (!currentP.equals(n2));

		return true;
	}

	// get the reverse of t2p
	private HashMap<Place, List<Transition>> getReverse1() {
		HashMap<Place, List<Transition>> b_p2t = new HashMap<Place, List<Transition>>();
		Iterator iter = t2p.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry1 = (Map.Entry) iter.next();
			Transition key = (Transition) entry1.getKey();
			List<Place> value = (List<Place>) entry1.getValue();
			for (Place p : value) {
				List<Transition> v1 = b_p2t.get(p);
				if (v1 == null) {
					v1 = new ArrayList<Transition>();
					v1.add(key);
					b_p2t.put(p, v1);
				} else {
					v1.add(key);
				}
			}
		}
		return b_p2t;
	}

	// get the reverse of p2t
	private HashMap<Transition, List<Place>> getReverse2() {
		HashMap<Transition, List<Place>> b_t2p = new HashMap<Transition, List<Place>>();
		Iterator iter = p2t.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry1 = (Map.Entry) iter.next();
			Place key = (Place) entry1.getKey();
			List<Transition> value = (List<Transition>) entry1.getValue();
			for (Transition t : value) {
				// add v,key to reverse
				List<Place> v1 = b_t2p.get(t);
				if (v1 == null) {
					v1 = new ArrayList<Place>();
					v1.add(key);
					b_t2p.put(t, v1);
				} else {
					v1.add(key);
				}
			}
		}
		return b_t2p;
	}

	// get the reverse of reachList
	private HashMap<Place, List<Place>> getReverse3() {
		HashMap<Place, List<Place>> b_reachList = new HashMap<Place, List<Place>>();
		Iterator iter = reachList.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry1 = (Map.Entry) iter.next();
			Place key = (Place) entry1.getKey();
			List<Place> value = (List<Place>) entry1.getValue();
			for (Place v : value) {
				// add v,key to reverse
				List<Place> v1 = b_reachList.get(v);
				if (v1 == null) {
					v1 = new ArrayList<Place>();
					v1.add(key);
					b_reachList.put(v, v1);
				} else {
					v1.add(key);
				}
			}
		}

		return b_reachList;
	}

	private void printG() {
		// System.out.println(p2t.size());
		Iterator iter = p2t.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry1 = (Map.Entry) iter.next();
			Place key = (Place) entry1.getKey();
			List<Transition> value = (List<Transition>) entry1.getValue();
			for (Transition t : value) {
				System.out.println(key.getLabel() + "\t" + t.getLabel());
				// System.out.println(key.getId() + "\t" + t.getId());
			}
		}

		// the t2p
		iter = t2p.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			Transition key = (Transition) entry.getKey();
			List<Place> value = (List<Place>) entry.getValue();

			for (Place p : value) {
				System.out.println(key.getLabel() + "\t" + p.getLabel());
			}
		}

	}

}
