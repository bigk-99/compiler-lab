package edu.kit.kastel.vads.compiler.backend.asm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import edu.kit.kastel.vads.compiler.backend.regalloc.Register;
import edu.kit.kastel.vads.compiler.backend.regalloc.RegisterAllocator;
import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ProjNode;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.StartNode;

/**
 * 
 */
public class AsmRegisterAllocator implements RegisterAllocator{

	private final AsmRegisterCollection ASM_REGISTERS = new AsmRegisterCollection();
	private final int K = ASM_REGISTERS.REGISTERS.size();

	private final Map<Node, Register> registerAllocation = new HashMap<>();	

	private InterferenceGraph interferenceGraph = new InterferenceGraph();
	private LivenessAnalysis liveness = new LivenessAnalysis();

	private Set<Node> initialWorklist = new HashSet<>();
	private Stack<Node> selectStack = new Stack<>();
	private Set<Node> simplifyWorklist = new HashSet<>();
	private Set<Node> spillWorklist = new HashSet<>();

	private class InterferenceGraph {
		private Map<Node, Set<Node>> adjList = new HashMap<>();

		public void addEdge(Node u, Node v) {
			if (!u.equals(v)) {
				adjList.computeIfAbsent(u, k -> new HashSet<>()).add(v);
				adjList.computeIfAbsent(v, k -> new HashSet<>()).add(u);
			}
		}

		public Set<Node> nodes() {
			return adjList.keySet();
		}

		public int degree(Node node) {
			return adjList.get(node).size();
		} 

		public Set<Node> adjacent(Node node) {
			return adjList.getOrDefault(node, Set.of());
		}

	}

	@Override
	public Map<Node, Register> allocateRegisters(IrGraph graph) {
		// 
		Set<Node> visited = new HashSet<>();
		visited.add(graph.endBlock());
		scan(graph.endBlock(), visited);
		
		// Perform allocation using graph coloring
		liveness.analyzeLiveness(initialWorklist, graph);
		buildInterferenceGraph(initialWorklist);
		// TODO: simplify, spill
		//maximumCardinalitySearch(interferenceGraph);
		for (Node node : interferenceGraph.adjList.keySet()) {
			selectStack.push(node);
		}
		colorGraph();
		// TODO: coalescing
		return Map.copyOf(this.registerAllocation);
	}

	private void scan(Node node, Set<Node> visited) {
		for (Node predecessor: node.predecessors()) {
			if (visited.add(predecessor)) {
				scan(predecessor, visited);
			}
		}
		
		if (needsRegister(node)) {
			this.initialWorklist.add(node);
		}
		
	}

	private static boolean needsRegister(Node node) {
		return !(node instanceof ProjNode || node instanceof StartNode || node instanceof Block || node instanceof ReturnNode);
	}

	
	/**
	 * Builds the interference graph
	 * @param nodes
	 * @return
	 */
	private InterferenceGraph buildInterferenceGraph(Set<Node> nodes) {
		for (Node u: nodes) {
			Set<Node> live = new HashSet<>(liveness.getLiveOut().get(u));

			Node def = u;
			List<? extends Node> uses = u.predecessors();

			for (Node v : live) {
				if (!v.equals(def)) {
					interferenceGraph.addEdge(def, v);
				}
			}
			live.remove(def);
			live.addAll(uses);
		}
		return interferenceGraph;
	}

	/**
	 * Performs greedy coloring on the interference graph
	 * @return
	 */
	private void colorGraph() {
		Map<Node, Integer> coloring = new HashMap<>();
		while (!selectStack.isEmpty()) {
			Node node = selectStack.pop();
			Set<Integer> usedColors = new HashSet<>();
			for (Node w : interferenceGraph.adjacent(node)) {
				if (coloring.containsKey(w)) {
					usedColors.add(coloring.get(w));
				}
			}
			int color = -1;
			for (int i = 0; i < K; i++) {
				if (!usedColors.contains(i)) {
					color = i;
					break;
				}
			}
			if (color == -1) {
				spillWorklist.add(node);
			} else {
				coloring.put(node, color);
			}
			
		}
		// TODO coalesing
		/*for (Node coalesced : coalescedNodes) {
			colored.put(coalesced, colored.get(getAlias(coalesced)));
		}*/
		assignRegisters(coloring);
	}

	/**
	 * Performs maximum cardinality search on the interference graph
	 * for simplical elimination ordering
	 * @param graph
	 */
	private void maximumCardinalitySearch(InterferenceGraph graph) {
		List<Node> eliminationOrdering = new ArrayList<>();
		int n = graph.nodes().size();

		Map<Node, Integer> weights = new HashMap<>();
		for (Node node: graph.nodes()) {
			weights.put(node, 0);
		}
		Set<Node> unvisited = graph.nodes();
		
		for (int i = 0; i < n; i++) {
			Node maxNode = findMaxWeightNode(weights, unvisited);
			eliminationOrdering.add(maxNode);
			for (Node neighbor : graph.adjacent(maxNode)) {
				if (unvisited.contains(neighbor)) {
					weights.compute(neighbor, (k, v) -> v + 1);
					
				}
			}
			unvisited.remove(maxNode);
		}
		
		for (Node node : eliminationOrdering.reversed()) {
			selectStack.push(node);
		}
		// System.out.println(selectStack);
	}

	/**
	 * Helper method for maximum cardinality search, returns a node of maximal weight
	 * @param weights List of weights for each node
	 * @return Node with maximum weight
	 */
	private Node findMaxWeightNode(Map<Node, Integer> weights, Set<Node> nodes) {
		int maximum = 0;
		Node maxNode = null;
		for (Node node : nodes) {
			if (weights.get(node) > maximum || maxNode == null) {
				maximum = weights.get(node);
				maxNode = node;
			}
		}
		return maxNode;
	}

	/**
	 * Assign colors to registers
	 * @param colors
	 * @param registers
	 */
	private void assignRegisters(Map<Node, Integer> coloring) {
		Map<Integer, Register> registerMap = new HashMap<>();
		Iterator<AsmRegister> iter = ASM_REGISTERS.REGISTERS.iterator();
		for (int i = 0; i < K; i++) {
			registerMap.putIfAbsent(i, iter.next());
		}
		for (Node node : coloring.keySet()) {
			int num = coloring.get(node);
			registerAllocation.putIfAbsent(node, registerMap.get(num));
		}
	}

	// TODO:
	private void simplify() {
		Node n = simplifyWorklist.iterator().next();
		simplifyWorklist.remove(n);
		selectStack.push(n);
		for (Node m : interferenceGraph.adjacent(n)) {
			// 
		}
	}

	// TODO
	private void coalesce() {

	}

	private void spill() {
		for (Node node : spillWorklist) {
			spillWorklist.remove(node);
			simplifyWorklist.add(node);

		}
	}

}
