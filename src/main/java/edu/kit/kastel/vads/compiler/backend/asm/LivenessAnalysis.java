package edu.kit.kastel.vads.compiler.backend.asm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.Node;

public class LivenessAnalysis {
	private final Map<Node, Set<Node>> liveIn = new HashMap<>();
	private final Map<Node, Set<Node>> liveOut = new HashMap<>();

	public void analyzeLiveness(Set<Node> nodes, IrGraph graph) {
		for (Node node : nodes) {
			liveIn.put(node, new HashSet<>());
			liveOut.put(node, new HashSet<>());
		}
		boolean changed;
		do {
			changed = false;
			for (Node node : nodes) {
				Set<Node> in_ = new HashSet<>(liveIn.get(node));
				Set<Node> out_ = new HashSet<>(liveOut.get(node));
			
				Set<Node> def = Set.of(node);
				Set<Node> use = new HashSet<>(node.predecessors());

				Set<Node> newOut = new HashSet<>();
				for (Node succ : graph.successors(node)) {
					newOut.addAll(liveIn.getOrDefault(succ, Set.of()));
				}

				Set<Node> newIn = new HashSet<>(newOut);
				newIn.removeAll(def);
				newIn.addAll(use);

				liveOut.put(node, newOut);
				liveIn.put(node, newIn);

				if (!in_.equals(newIn) || !out_.equals(newOut)) {
					changed = true;
				}
			}
		} while (changed);
		
	}

	public Map<Node, Set<Node>> getLiveIn() {
		return liveIn;
	}

	public Map<Node, Set<Node>> getLiveOut() {
		return liveOut;
	}
}
