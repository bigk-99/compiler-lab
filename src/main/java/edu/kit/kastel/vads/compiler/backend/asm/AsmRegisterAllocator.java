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
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.Block;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
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
	private final InterferenceGraph interferenceGraph = new InterferenceGraph();
	private final LivenessAnalysis liveness = new LivenessAnalysis();

	private final Set<Node> initialWorklist = new HashSet<>();
	private final Set<Node> orderedNodes = new HashSet<>();

	private final Stack<Node> selectStack = new Stack<>();
	private final Set<Node> simplifyWorklist = new HashSet<>();
	private final Set<Node> spillWorklist = new HashSet<>();

	private final Set<Node> spilledNodes = new HashSet<>();
	private final Map<Node, Node> coalescedNodes = new HashMap<>();

	@Override
	public Map<Node, Register> allocateRegisters(IrGraph graph) {

		orderNodes(graph);

		liveness.analyzeLiveness(orderedNodes, graph);

		/*System.out.println("+++");
		System.out.println(liveness.getLiveOut());
		System.out.println(liveness.getLiveIn());
		System.out.println("+++");
		System.out.println(initialWorklist);*/

		buildInterferenceGraph(orderedNodes);

		/*System.out.println("_________");
		System.out.println(interferenceGraph.getAdjList());
		System.out.println("_________");*/
		// TODO: simplify, spill
		
		//maximumCardinalitySearch(interferenceGraph);
		/*for (Node node : interferenceGraph.getAdjList().keySet()) {
			selectStack.push(node);
		}*/
		for (Node node : interferenceGraph.getNodes()) {
            if (interferenceGraph.degree(node) < K) {
                simplifyWorklist.add(node);
            } else {
                spillWorklist.add(node);
            }
        }

        while (!simplifyWorklist.isEmpty() || !spillWorklist.isEmpty()) {
            if (!simplifyWorklist.isEmpty()) {
                simplify();
            } else {
                spill();
            }
        }

		System.out.println(selectStack);
		colorGraph();
		// TODO: coalescing
		return Map.copyOf(this.registerAllocation);
	}

	private void orderNodes(IrGraph graph) {
		Set<Node> visited = new HashSet<>();
		scan(graph.endBlock(), visited);
	}

	private void scan(Node node, Set<Node> visited) {
		for (Node predecessor: node.predecessors()) {
			if (visited.add(predecessor)) {
				scan(predecessor, visited);
			}
		}
		
		if (needsRegister(node)) {
			orderedNodes.add(node);
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
			
			for (Node v : live) {
				for (Node w : live) {
					if (!v.equals(w)) {
						interferenceGraph.addEdge(v, w);
					}
				}
				interferenceGraph.addEdge(u, v);
			}
			interferenceGraph.getAdjList().putIfAbsent(u, new HashSet<>());
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
			for (Node w : interferenceGraph.getAdjacents(node)) {
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
				//simplify();
				/*if (!simplifyWorklist.isEmpty()) {
					spilledNodes.add(node);
				}*/
			} else {
				coloring.put(node, color);
			}
		}
		
		for (Node node : spilledNodes) {
			int color = coloring.size() % K;
			coloring.put(node, color);
		}
		
		assignRegisters(coloring);
	}

	/**
	 * Performs maximum cardinality search on the interference graph
	 * for simplical elimination ordering
	 * @param graph
	 */
	private void maximumCardinalitySearch(InterferenceGraph graph) {
		List<Node> eliminationOrdering = new ArrayList<>();
		int n = graph.getNodes().size();

		Map<Node, Integer> weights = new HashMap<>();
		for (Node node: graph.getNodes()) {
			weights.put(node, 0);
		}
		
		while (!weights.isEmpty()){
			Node maxNode = findMaxWeightNode(weights);
			eliminationOrdering.add(maxNode);
			for (Node neighbor : graph.getAdjacents(maxNode)) {
				if (weights.containsKey(neighbor)) {
					weights.compute(neighbor, (k, v) -> v + 1);
					
				}
			}
			weights.remove(maxNode);
		}
		
		for (Node node : eliminationOrdering.reversed()) {
			selectStack.push(node);
		}
	}

	/**
	 * Helper method for maximum cardinality search, returns a node of maximal weight
	 * @param weights List of weights for each node
	 * @return Node with maximum weight
	 */
	private Node findMaxWeightNode(Map<Node, Integer> weights) {
		int maximum = 0;
		Node maxNode = null;
		for (Node node : weights.keySet()) {
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
		Iterator<Node> it = simplifyWorklist.iterator();
        if (it.hasNext()) {
            Node n = it.next();
            it.remove();
            selectStack.push(n);
            for (Node m : interferenceGraph.getAdjacents(n)) {
                if (interferenceGraph.degree(m) < K && spillWorklist.contains(m)) {
                    spillWorklist.remove(m);
                    simplifyWorklist.add(m);
                }
            }
        }

		/*while (!simplifyWorklist.isEmpty()) {
			Node n = simplifyWorklist.iterator().next();
			simplifyWorklist.remove(n);
			selectStack.push(n);
			
			for (Node m : interferenceGraph.getAdjacents(n)) {
				int degree = interferenceGraph.degree(m);
				if (degree == K) {
					spillWorklist.remove(m);
					simplifyWorklist.add(m);
				}
			}
		}*/
	}

	// TODO
	private void coalesce() {
		for (Node u : interferenceGraph.getNodes()) {
			for (Node v : interferenceGraph.getAdjacents(u)) {
				if (canCoalesce(u, v)) {
					Set<Node> neighbors = new HashSet<>(interferenceGraph.getAdjacents(u));
					neighbors.addAll(interferenceGraph.getAdjacents(v));
					interferenceGraph.getAdjList().remove(u);
					interferenceGraph.getAdjList().remove(v);
					
					
					coalescedNodes.put(u, v);
				}
			}
		}
	}

	private boolean canCoalesce(Node u, Node v) {
		Set<Node> combinedNeighbors = new HashSet<>(interferenceGraph.getAdjacents(u));
		combinedNeighbors.addAll(interferenceGraph.getAdjacents(v));
		return combinedNeighbors.size() < K;
	}

	private void spill() {
		Node spill = spillWorklist.iterator().next();
        spillWorklist.remove(spill);
        simplifyWorklist.add(spill);
        spilledNodes.add(spill);
		/* 
		while (!spillWorklist.isEmpty()) {
			Node n = spillWorklist.iterator().next();
			spillWorklist.remove(n);
			
			simplify();
			
			if (!simplifyWorklist.isEmpty()) {
				// Mark node for spilling
				spilledNodes.add(n);
			}
		}*/
	}

}
