package edu.kit.kastel.vads.compiler.backend.asm;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.kit.kastel.vads.compiler.ir.IrGraph;
import edu.kit.kastel.vads.compiler.ir.node.BinaryOperationNode;
import edu.kit.kastel.vads.compiler.ir.node.ConstIntNode;
import edu.kit.kastel.vads.compiler.ir.node.Node;
import edu.kit.kastel.vads.compiler.ir.node.ReturnNode;
import edu.kit.kastel.vads.compiler.ir.node.Phi;

import static edu.kit.kastel.vads.compiler.ir.util.NodeSupport.predecessorSkipProj;
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
			
				Set<Node> def = getDefined(node);
				Set<Node> use = getUsed(node);
				
				Set<Node> newOut = new HashSet<>();
				for (Node succ : graph.successors(node)) {
					newOut.addAll(liveIn.getOrDefault(succ, new HashSet<>()));
				}

				Set<Node> newIn = new HashSet<>(use);
				Set<Node> temp = new HashSet<>(newOut);
				temp.removeAll(def);
				newIn.addAll(temp);

				liveOut.put(node, newOut);
				liveIn.put(node, newIn);

				if (!in_.equals(newIn) || !out_.equals(newOut)) {
					changed = true;
				}
			}
		} while (changed);
		
	}

	private Set<Node> getDefined(Node node) {
		Set<Node> defs = new HashSet<>();
		if (node instanceof BinaryOperationNode || 
			node instanceof ConstIntNode) {
			defs.add(node);
		}
		return defs;
	}

	private Set<Node> getUsed(Node node) {
		Set<Node> uses = new HashSet<>();
		if (node instanceof BinaryOperationNode) {
			uses.add(predecessorSkipProj(node, BinaryOperationNode.LEFT));
			uses.add(predecessorSkipProj(node, BinaryOperationNode.RIGHT));
		} else if (node instanceof ReturnNode) {
			uses.add(predecessorSkipProj(node, ReturnNode.RESULT));
		} else if (node instanceof Phi) {
			for (Node pred : node.predecessors()) {
				uses.add(pred);
			}
		}
		return uses;
	}

	public Map<Node, Set<Node>> getLiveIn() {
		return liveIn;
	}

	public Map<Node, Set<Node>> getLiveOut() {
		return liveOut;
	}
}
